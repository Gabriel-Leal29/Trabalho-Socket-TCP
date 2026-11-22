package minichat.cliente;

import minichat.comum.InfoArquivo;

import java.util.List;

/** O que chega pelo canal de chat. Os métodos são chamados pela thread receptora, não pela da interface. */
public interface OuvinteChat {

    void mensagem(String remetente, String dataHora, String texto, String categoria);

    void usuarios(List<String> nicknames);

    void arquivos(List<InfoArquivo> arquivos);

    void novoArquivo(InfoArquivo arquivo);

    void erro(String codigo, String mensagem);

    void conexaoPerdida(String motivo);
}
