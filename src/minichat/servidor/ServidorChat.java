package minichat.servidor;

import minichat.comum.InfoArquivo;
import minichat.comum.Mensagem;
import minichat.comum.Protocolo;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServidorChat {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final int porta;
    private final Path pastaBase;
    private final LogServidor log;
    private final RepositorioArquivos repositorio;
    private final Map<String, ClienteConectado> clientes = new ConcurrentHashMap<>();
    private volatile Runnable aoMudar = () -> { };
    private ServerSocket servidor;

    public ServidorChat(int porta, Path pastaBase) throws IOException {
        this.porta = porta;
        this.pastaBase = pastaBase;
        log = new LogServidor(pastaBase.resolve("log_servidor.txt"));
        repositorio = new RepositorioArquivos(pastaBase.resolve("arquivos_servidor"),
                pastaBase.resolve(".uploads_parciais"));
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.net.preferIPv4Stack", "true");
        int porta = Protocolo.PORTA_PADRAO;
        boolean comJanela = !GraphicsEnvironment.isHeadless();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--porta") && i + 1 < args.length) {
                porta = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--sem-janela")) {
                comJanela = false;
            }
        }

        ServidorChat servidor = new ServidorChat(porta, Path.of("").toAbsolutePath());
        if (comJanela) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignorada) {
            }
            SwingUtilities.invokeAndWait(() -> new JanelaServidor(servidor).setVisible(true));
        }
        try {
            servidor.abrir();
        } catch (IOException e) {
            String aviso = "Não foi possível abrir a porta " + porta + ": " + e.getMessage()
                    + "\nVeja se já existe outro servidor aberto.";
            if (comJanela) {
                JOptionPane.showMessageDialog(null, aviso, "Servidor", JOptionPane.ERROR_MESSAGE);
            } else {
                System.err.println(aviso);
            }
            System.exit(1);
        }
        servidor.atenderConexoes();
    }

    public void abrir() throws IOException {
        servidor = new ServerSocket(porta);
        log.registrar("0.0.0.0:" + porta, "-", "SERVIDOR", "OK",
                "servidor iniciado na porta " + porta + " (" + repositorio.listar().size() + " arquivos na pasta central)");
    }

    /** Uma thread por conexão aceita, como no ServidorMulti da aula. */
    public void atenderConexoes() {
        while (!servidor.isClosed()) {
            try {
                Socket socket = servidor.accept();
                new AtendimentoCliente(socket, this).start();
            } catch (IOException e) {
                log.registrar("0.0.0.0:" + porta, "-", "SERVIDOR", "ERRO", "falha ao aceitar conexão: " + e.getMessage());
            }
        }
    }

    public synchronized boolean entrar(ClienteConectado cliente) {
        if (clientes.putIfAbsent(cliente.nickname(), cliente) != null) {
            return false;
        }
        cliente.enviar(new Mensagem(Protocolo.OK, cliente.token()));
        cliente.enviar(listaDeArquivos());
        cliente.iniciar();
        transmitir(listaDeUsuarios());
        transmitir(aviso(Protocolo.CATEGORIA_SISTEMA, cliente.nickname() + " entrou no chat"));
        log.registrar(cliente.endereco(), cliente.nickname(), "CONEXAO", "OK",
                "entrou no chat (" + clientes.size() + " online)");
        aoMudar.run();
        return true;
    }

    public synchronized void sair(ClienteConectado cliente, boolean normal, String motivo) {
        cliente.encerrar(motivo);
        if (!clientes.remove(cliente.nickname(), cliente)) {
            return;
        }
        transmitir(listaDeUsuarios());
        transmitir(aviso(Protocolo.CATEGORIA_SISTEMA,
                cliente.nickname() + (normal ? " saiu do chat" : " caiu (conexão perdida)")));
        log.registrar(cliente.endereco(), cliente.nickname(), "DESCONEXAO", normal ? "OK" : "ERRO", motivo);
        aoMudar.run();
    }

    public synchronized void mensagemDoChat(ClienteConectado cliente, String texto) {
        texto = texto.strip();
        if (texto.isEmpty()) {
            return;
        }
        if (texto.length() > Protocolo.MAX_TEXTO) {
            texto = texto.substring(0, Protocolo.MAX_TEXTO);
        }
        transmitir(new Mensagem(Protocolo.BROADCAST, cliente.nickname(), agora(), texto, Protocolo.CATEGORIA_CHAT));
        log.registrar(cliente.endereco(), cliente.nickname(), "MSG", "OK", texto);
    }

    public synchronized void anunciarArquivo(InfoArquivo arquivo) {
        transmitir(aviso(Protocolo.CATEGORIA_ARQUIVO,
                arquivo.autor() + " enviou " + arquivo.nome() + " – " + arquivo.tamanhoLegivel()));
        transmitir(new Mensagem(Protocolo.NEW_FILE, arquivo.nome(), String.valueOf(arquivo.tamanho()), arquivo.autor()));
        transmitir(listaDeArquivos());
        aoMudar.run();
    }

    /** Broadcast: só coloca a mensagem na fila de cada cliente; quem escreve no socket é a thread de cada um. */
    private void transmitir(Mensagem mensagem) {
        for (ClienteConectado cliente : clientes.values()) {
            cliente.enviar(mensagem);
        }
    }

    private Mensagem aviso(String categoria, String texto) {
        return new Mensagem(Protocolo.BROADCAST, "servidor", agora(), texto, categoria);
    }

    private Mensagem listaDeUsuarios() {
        return new Mensagem(Protocolo.USERS, nicknames());
    }

    private Mensagem listaDeArquivos() {
        return new Mensagem(Protocolo.FILE_LIST, InfoArquivo.paraCampos(repositorio.listar()));
    }

    private static String agora() {
        return LocalDateTime.now().format(DATA_HORA);
    }

    public ClienteConectado clientePorToken(String token) {
        for (ClienteConectado cliente : clientes.values()) {
            if (cliente.token().equals(token)) {
                return cliente;
            }
        }
        return null;
    }

    public List<String> nicknames() {
        List<String> nomes = new ArrayList<>(clientes.keySet());
        Collections.sort(nomes, String.CASE_INSENSITIVE_ORDER);
        return nomes;
    }

    public void aoMudar(Runnable ouvinte) {
        aoMudar = ouvinte;
    }

    public int porta() {
        return porta;
    }

    public Path pastaBase() {
        return pastaBase;
    }

    public LogServidor log() {
        return log;
    }

    public RepositorioArquivos repositorio() {
        return repositorio;
    }
}
