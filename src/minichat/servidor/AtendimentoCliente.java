package minichat.servidor;

import minichat.comum.Mensagem;
import minichat.comum.Protocolo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.regex.Pattern;

public class AtendimentoCliente extends Thread {

    private static final Pattern NICK_VALIDO = Pattern.compile("[\\p{L}\\p{N}_.-]{1,20}");

    private final Socket socket;
    private final ServidorChat servidor;
    private final String endereco;

    public AtendimentoCliente(Socket socket, ServidorChat servidor) {
        this.socket = socket;
        this.servidor = servidor;
        endereco = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        setName("atendimento-" + endereco);
    }

    @Override
    public void run() {
        try (socket) {
            socket.setSoTimeout(Protocolo.TIMEOUT_LEITURA_MS);
            socket.setTcpNoDelay(true);
            DataInputStream entrada = new DataInputStream(
                    new BufferedInputStream(socket.getInputStream(), Protocolo.TAMANHO_BUFFER));
            DataOutputStream saida = new DataOutputStream(
                    new BufferedOutputStream(socket.getOutputStream(), Protocolo.TAMANHO_BUFFER));

            Mensagem primeira;
            try {
                primeira = Mensagem.ler(entrada);
            } catch (IOException e) {
                servidor.log().registrar(endereco, "-", "CONEXAO", "ERRO", "conexão encerrada antes do primeiro comando");
                return;
            }

            switch (primeira.tipo()) {
                case Protocolo.LOGIN -> atenderChat(primeira.campo(0).strip(), entrada, saida);
                case Protocolo.UPLOAD -> new TransferenciaArquivos(servidor, endereco, entrada, saida).receber(primeira);
                case Protocolo.DOWNLOAD -> new TransferenciaArquivos(servidor, endereco, entrada, saida).enviar(primeira);
                default -> {
                    servidor.log().registrar(endereco, "-", "ERRO", "ERRO", "comando inesperado: " + primeira.tipo());
                    responder(saida, Protocolo.erro(Protocolo.ERRO_REQUISICAO,
                            "o primeiro comando deve ser LOGIN, UPLOAD ou DOWNLOAD"));
                }
            }
        } catch (IOException ignorada) {
        }
    }

    private void atenderChat(String nickname, DataInputStream entrada, DataOutputStream saida) throws IOException {
        if (!NICK_VALIDO.matcher(nickname).matches()) {
            servidor.log().registrar(endereco, nickname.isEmpty() ? "-" : nickname, "CONEXAO", "ERRO", "nickname inválido");
            responder(saida, Protocolo.erro(Protocolo.ERRO_REQUISICAO,
                    "nickname inválido: use de 1 a 20 letras, números, ponto, _ ou -"));
            return;
        }
        ClienteConectado cliente = new ClienteConectado(nickname, endereco, socket, saida);
        if (!servidor.entrar(cliente)) {
            servidor.log().registrar(endereco, nickname, "CONEXAO", "ERRO", "recusado: nickname já está em uso");
            responder(saida, Protocolo.erro(Protocolo.ERRO_NICK_EM_USO,
                    "o nickname \"" + nickname + "\" já está em uso"));
            return;
        }

        boolean logout = false;
        String motivo = "logout";
        try {
            while (!logout) {
                Mensagem mensagem = Mensagem.ler(entrada);
                switch (mensagem.tipo()) {
                    case Protocolo.MSG -> servidor.mensagemDoChat(cliente, mensagem.campo(0));
                    case Protocolo.PING -> cliente.enviar(new Mensagem(Protocolo.PONG));
                    case Protocolo.LOGOUT -> logout = true;
                    default -> cliente.enviar(Protocolo.erro(Protocolo.ERRO_REQUISICAO,
                            "comando desconhecido: " + mensagem.tipo()));
                }
            }
        } catch (IOException e) {
            motivo = cliente.motivoEncerramento() != null ? cliente.motivoEncerramento() : descrever(e);
        }
        servidor.sair(cliente, logout, motivo);
    }

    private static String descrever(IOException e) {
        if (e instanceof SocketTimeoutException) {
            return "sem resposta há " + Protocolo.TIMEOUT_LEITURA_MS / 1000 + " s";
        }
        if (e instanceof EOFException) {
            return "conexão fechada sem LOGOUT (queda abrupta)";
        }
        return "conexão interrompida (" + e.getMessage() + ")";
    }

    private static void responder(DataOutputStream saida, Mensagem mensagem) throws IOException {
        mensagem.escrever(saida);
        saida.flush();
    }
}
