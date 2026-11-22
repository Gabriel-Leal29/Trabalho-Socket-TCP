package minichat.servidor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/** Grava o log_servidor.txt no formato [AAAA-MM-DD HH:MM:SS] [IP:porta] [nickname] [OPERAÇÃO] [STATUS] detalhes. */
public class LogServidor {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PrintWriter arquivo;
    private volatile Consumer<String> aoRegistrar = linha -> { };

    public LogServidor(Path caminho) throws IOException {
        arquivo = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(caminho.toFile(), true), StandardCharsets.UTF_8), true);
    }

    public void aoRegistrar(Consumer<String> ouvinte) {
        aoRegistrar = ouvinte;
    }

    public synchronized void registrar(String endereco, String nickname, String operacao,
                                       String status, String detalhes) {
        String linha = String.format("[%s] [%s] [%s] [%s] [%s] %s",
                LocalDateTime.now().format(FORMATO), endereco, nickname, operacao, status, detalhes);
        arquivo.println(linha);
        System.out.println(linha);
        aoRegistrar.accept(linha);
    }
}
