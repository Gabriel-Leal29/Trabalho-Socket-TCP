package minichat.comum;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.LongConsumer;

public final class Protocolo {
    public static final int PORTA_PADRAO = 5050;

    // 64 KB: poucas chamadas de sistema por arquivo, mas ainda atualiza a barra de progresso
    // várias vezes por segundo e nunca guarda mais que isso do arquivo na memória.
    public static final int TAMANHO_BUFFER = 64 * 1024;

    public static final int TIMEOUT_CONEXAO_MS = 5000;
    public static final int INTERVALO_PING_MS = 5000;
    // Três PINGs sem resposta: a conexão é dada como perdida.
    public static final int TIMEOUT_LEITURA_MS = 15000;
    public static final int MAX_TEXTO = 1000;

    public static final String LOGIN = "LOGIN";
    public static final String OK = "OK";
    public static final String ERROR = "ERROR";
    public static final String MSG = "MSG";
    public static final String BROADCAST = "BROADCAST";
    public static final String USERS = "USERS";
    public static final String UPLOAD = "UPLOAD";
    public static final String NEW_FILE = "NEW_FILE";
    public static final String FILE_LIST = "FILE_LIST";
    public static final String DOWNLOAD = "DOWNLOAD";
    public static final String LOGOUT = "LOGOUT";
    public static final String PING = "PING";
    public static final String PONG = "PONG";

    public static final String CATEGORIA_CHAT = "CHAT";
    public static final String CATEGORIA_SISTEMA = "SISTEMA";
    public static final String CATEGORIA_ARQUIVO = "ARQUIVO";

    public static final int ERRO_REQUISICAO = 400;
    public static final int ERRO_SEM_LOGIN = 401;
    public static final int ERRO_NAO_ENCONTRADO = 404;
    public static final int ERRO_NICK_EM_USO = 409;
    public static final int ERRO_INTERNO = 500;
    public static final int ERRO_SEM_ESPACO = 507;

    private Protocolo() {
    }

    public static Mensagem erro(int codigo, String texto) {
        return new Mensagem(ERROR, String.valueOf(codigo), texto);
    }

    public static void copiarEmBlocos(InputStream origem, OutputStream destino, long tamanho,
                                      LongConsumer progresso) throws IOException {
        byte[] buffer = new byte[TAMANHO_BUFFER];
        long copiados = 0;
        while (copiados < tamanho) {
            int lidos = origem.read(buffer, 0, (int) Math.min(buffer.length, tamanho - copiados));
            if (lidos < 0) {
                throw new EOFException("conexão encerrada antes do fim do arquivo");
            }
            destino.write(buffer, 0, lidos);
            copiados += lidos;
            progresso.accept(copiados);
        }
    }
}
