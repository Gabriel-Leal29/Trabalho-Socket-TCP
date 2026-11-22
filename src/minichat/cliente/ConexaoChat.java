package minichat.cliente;

import minichat.comum.ErroProtocolo;
import minichat.comum.InfoArquivo;
import minichat.comum.Mensagem;
import minichat.comum.Protocolo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * O canal de chat do cliente. Uma thread só recebe; outra só envia (mensagens digitadas e o
 * PING a cada 5 s), então a interface nunca fica parada esperando a rede.
 */
public class ConexaoChat {

    private final String host;
    private final int porta;
    private final String nickname;
    private final String token;
    private final Socket socket;
    private final DataInputStream entrada;
    private final DataOutputStream saida;
    private final ScheduledExecutorService envios = Executors.newSingleThreadScheduledExecutor(tarefa -> {
        Thread thread = new Thread(tarefa, "envios-chat");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean encerrada;

    /** Conecta e faz o LOGIN. Lança ErroProtocolo se o servidor recusar (ex.: 409, nickname em uso). */
    public ConexaoChat(String host, int porta, String nickname) throws IOException {
        this.host = host;
        this.porta = porta;
        this.nickname = nickname;
        socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, porta), Protocolo.TIMEOUT_CONEXAO_MS);
            socket.setSoTimeout(Protocolo.TIMEOUT_LEITURA_MS);
            socket.setTcpNoDelay(true);
            entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            saida = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            new Mensagem(Protocolo.LOGIN, nickname).escrever(saida);
            saida.flush();
            token = ErroProtocolo.conferir(Mensagem.ler(entrada)).campo(0);
        } catch (IOException e) {
            socket.close();
            envios.shutdown();
            throw e;
        }
    }

    public void iniciar(OuvinteChat ouvinte) {
        Thread receptora = new Thread(() -> receber(ouvinte), "receptora-chat");
        receptora.setDaemon(true);
        receptora.start();
        envios.scheduleAtFixedRate(() -> escrever(new Mensagem(Protocolo.PING)),
                Protocolo.INTERVALO_PING_MS, Protocolo.INTERVALO_PING_MS, TimeUnit.MILLISECONDS);
    }

    private void receber(OuvinteChat ouvinte) {
        try {
            while (true) {
                Mensagem m = Mensagem.ler(entrada);
                switch (m.tipo()) {
                    case Protocolo.BROADCAST -> ouvinte.mensagem(m.campo(0), m.campo(1), m.campo(2), m.campo(3));
                    case Protocolo.USERS -> ouvinte.usuarios(m.campos());
                    case Protocolo.FILE_LIST -> ouvinte.arquivos(InfoArquivo.deCampos(m.campos()));
                    case Protocolo.NEW_FILE -> ouvinte.novoArquivo(
                            new InfoArquivo(m.campo(0), Long.parseLong(m.campo(1)), m.campo(2)));
                    case Protocolo.ERROR -> ouvinte.erro(m.campo(0), m.campo(1));
                    default -> { }
                }
            }
        } catch (SocketTimeoutException e) {
            perderConexao(ouvinte, "o servidor não responde há " + Protocolo.TIMEOUT_LEITURA_MS / 1000 + " s");
        } catch (IOException | RuntimeException e) {
            perderConexao(ouvinte, "o servidor foi desligado ou a rede caiu");
        }
    }

    private void perderConexao(OuvinteChat ouvinte, String motivo) {
        if (!encerrada) {
            encerrada = true;
            envios.shutdownNow();
            fecharSocket();
            ouvinte.conexaoPerdida(motivo);
        }
    }

    public void enviarMensagem(String texto) {
        try {
            envios.execute(() -> escrever(new Mensagem(Protocolo.MSG, texto)));
        } catch (RejectedExecutionException ignorada) {
        }
    }

    /** Se a escrita falha, fecha o socket; a thread receptora percebe e avisa a perda da conexão. */
    private void escrever(Mensagem mensagem) {
        try {
            mensagem.escrever(saida);
            saida.flush();
        } catch (IOException e) {
            fecharSocket();
        }
    }

    /** LOGOUT e fecha. Espera no máximo 1 s para o LOGOUT sair. */
    public void sair() {
        if (encerrada) {
            return;
        }
        encerrada = true;
        try {
            envios.execute(() -> escrever(new Mensagem(Protocolo.LOGOUT)));
            envios.shutdown();
            envios.awaitTermination(1, TimeUnit.SECONDS);
        } catch (RejectedExecutionException ignorada) {
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        fecharSocket();
    }

    private void fecharSocket() {
        try {
            socket.close();
        } catch (IOException ignorada) {
        }
    }

    public String host() {
        return host;
    }

    public int porta() {
        return porta;
    }

    public String nickname() {
        return nickname;
    }

    public String token() {
        return token;
    }
}
