package minichat.servidor;

import minichat.comum.Mensagem;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Um cliente logado no chat. Tudo o que o servidor manda para ele passa por uma fila,
 * esvaziada por uma thread só dele. Assim o broadcast só coloca a mensagem nas filas e
 * nunca espera um cliente lento.
 */
public class ClienteConectado {

    // Folga para rajadas; só um cliente que parou de ler chega aqui, e aí é desconectado
    // para a fila não crescer sem limite na memória do servidor.
    private static final int LIMITE_FILA = 10_000;

    private final String nickname;
    private final String endereco;
    private final String token = UUID.randomUUID().toString();
    private final Socket socket;
    private final DataOutputStream saida;
    private final BlockingQueue<Mensagem> fila = new LinkedBlockingQueue<>(LIMITE_FILA);
    private final Thread escritora;
    private volatile boolean encerrado;
    private volatile String motivoEncerramento;

    public ClienteConectado(String nickname, String endereco, Socket socket, DataOutputStream saida) {
        this.nickname = nickname;
        this.endereco = endereco;
        this.socket = socket;
        this.saida = saida;
        escritora = new Thread(this::escreverFila, "escritora-" + nickname);
        escritora.setDaemon(true);
    }

    public String nickname() {
        return nickname;
    }

    public String endereco() {
        return endereco;
    }

    public String token() {
        return token;
    }

    public String motivoEncerramento() {
        return motivoEncerramento;
    }

    public void iniciar() {
        escritora.start();
    }

    public void enviar(Mensagem mensagem) {
        if (!encerrado && !fila.offer(mensagem)) {
            encerrar("cliente não está lendo as mensagens (fila cheia)");
        }
    }

    private void escreverFila() {
        try {
            while (!encerrado) {
                Mensagem mensagem = fila.take();
                mensagem.escrever(saida);
                if (fila.isEmpty()) {
                    saida.flush();
                }
            }
        } catch (IOException e) {
            encerrar("falha ao enviar (" + e.getMessage() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Fechar o socket também acorda a thread que lê deste cliente, que faz a saída do chat. */
    public void encerrar(String motivo) {
        if (motivoEncerramento == null) {
            motivoEncerramento = motivo;
        }
        encerrado = true;
        escritora.interrupt();
        try {
            socket.close();
        } catch (IOException ignorada) {
        }
    }
}
