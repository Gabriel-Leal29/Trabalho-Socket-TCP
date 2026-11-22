package minichat.comum;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public record InfoArquivo(String nome, long tamanho, String autor) {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    public String tamanhoLegivel() {
        return tamanhoLegivel(tamanho);
    }

    public static String tamanhoLegivel(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] unidades = {"KB", "MB", "GB", "TB"};
        double valor = bytes;
        int i = -1;
        while (valor >= 1024 && i < unidades.length - 1) {
            valor /= 1024;
            i++;
        }
        return String.format(PT_BR, "%.1f %s", valor, unidades[i]);
    }

    /** FILE_LIST leva três campos por arquivo: nome, tamanho e autor. */
    public static List<String> paraCampos(Collection<InfoArquivo> arquivos) {
        List<String> campos = new ArrayList<>();
        for (InfoArquivo a : arquivos) {
            campos.add(a.nome());
            campos.add(String.valueOf(a.tamanho()));
            campos.add(a.autor());
        }
        return campos;
    }

    public static List<InfoArquivo> deCampos(List<String> campos) {
        List<InfoArquivo> arquivos = new ArrayList<>();
        for (int i = 0; i + 2 < campos.size(); i += 3) {
            arquivos.add(new InfoArquivo(campos.get(i), Long.parseLong(campos.get(i + 1)), campos.get(i + 2)));
        }
        return arquivos;
    }
}
