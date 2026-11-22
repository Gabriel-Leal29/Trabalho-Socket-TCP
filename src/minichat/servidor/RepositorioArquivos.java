package minichat.servidor;

import minichat.comum.InfoArquivo;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A pasta central. Nomes repetidos são renomeados ("foto (1).jpg") para ninguém perder
 * arquivo; a escolha do nome e a gravação ficam no mesmo bloco synchronized, então dois
 * uploads simultâneos com o mesmo nome nunca disputam o mesmo arquivo.
 */
public class RepositorioArquivos {

    private static final String ARQUIVO_AUTORES = ".autores.properties";

    private final Path pasta;
    private final Path pastaParciais;
    // Sem distinguir maiúsculas: no Windows e no macOS "Foto.jpg" e "foto.jpg" são o mesmo arquivo.
    private final Map<String, InfoArquivo> arquivos = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Properties autores = new Properties();

    public RepositorioArquivos(Path pasta, Path pastaParciais) throws IOException {
        this.pasta = pasta;
        this.pastaParciais = pastaParciais;
        Files.createDirectories(pasta);
        Files.createDirectories(pastaParciais);
        apagarParciaisAntigos();
        carregar();
    }

    private void apagarParciaisAntigos() throws IOException {
        try (DirectoryStream<Path> parciais = Files.newDirectoryStream(pastaParciais)) {
            for (Path p : parciais) {
                Files.deleteIfExists(p);
            }
        }
    }

    private void carregar() throws IOException {
        Path caminhoAutores = pasta.resolve(ARQUIVO_AUTORES);
        if (Files.exists(caminhoAutores)) {
            try (Reader leitor = Files.newBufferedReader(caminhoAutores, StandardCharsets.UTF_8)) {
                autores.load(leitor);
            }
        }
        try (DirectoryStream<Path> conteudo = Files.newDirectoryStream(pasta)) {
            for (Path p : conteudo) {
                String nome = p.getFileName().toString();
                if (Files.isRegularFile(p) && !nome.startsWith(".")) {
                    arquivos.put(nome, new InfoArquivo(nome, Files.size(p), autores.getProperty(nome, "?")));
                }
            }
        }
    }

    public synchronized List<InfoArquivo> listar() {
        return new ArrayList<>(arquivos.values());
    }

    public synchronized InfoArquivo buscar(String nome) {
        InfoArquivo info = arquivos.get(nome);
        return info != null && Files.isRegularFile(caminho(info)) ? info : null;
    }

    public Path caminho(InfoArquivo info) {
        return pasta.resolve(info.nome());
    }

    public Path novoParcial() {
        return pastaParciais.resolve(UUID.randomUUID() + ".parcial");
    }

    public boolean temEspacoPara(long tamanho) throws IOException {
        return Files.getFileStore(pasta).getUsableSpace() > tamanho;
    }

    public synchronized InfoArquivo guardar(Path parcial, String nomeDesejado, String autor) throws IOException {
        String nome = nomeLivre(nomeDesejado);
        Path destino = pasta.resolve(nome);
        Files.move(parcial, destino);
        InfoArquivo info = new InfoArquivo(nome, Files.size(destino), autor);
        arquivos.put(nome, info);
        autores.setProperty(nome, autor);
        try (Writer escritor = Files.newBufferedWriter(pasta.resolve(ARQUIVO_AUTORES), StandardCharsets.UTF_8)) {
            autores.store(escritor, null);
        }
        return info;
    }

    private String nomeLivre(String nome) {
        if (!ocupado(nome)) {
            return nome;
        }
        int ponto = nome.lastIndexOf('.');
        String base = ponto > 0 ? nome.substring(0, ponto) : nome;
        String extensao = ponto > 0 ? nome.substring(ponto) : "";
        for (int i = 1; ; i++) {
            String candidato = base + " (" + i + ")" + extensao;
            if (!ocupado(candidato)) {
                return candidato;
            }
        }
    }

    private boolean ocupado(String nome) {
        return arquivos.containsKey(nome) || Files.exists(pasta.resolve(nome));
    }

    /**
     * Fica só com o nome do arquivo (sem pastas) e troca caracteres que o Windows não aceita,
     * para um upload feito no Mac poder ser salvo num servidor Windows e vice-versa.
     */
    public static String limparNome(String nome) {
        String limpo = nome.replace('\\', '/');
        limpo = limpo.substring(limpo.lastIndexOf('/') + 1);
        limpo = limpo.replaceAll("[:*?\"<>|\\p{Cntrl}]", "_").strip();
        if (limpo.startsWith(".")) {
            limpo = "_" + limpo.substring(1);
        }
        return limpo.length() > 200 ? limpo.substring(0, 200) : limpo;
    }
}
