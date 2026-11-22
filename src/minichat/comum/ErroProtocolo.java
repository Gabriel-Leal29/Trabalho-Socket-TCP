package minichat.comum;

import java.io.IOException;

/** O outro lado respondeu ERROR (código, mensagem). */
public class ErroProtocolo extends IOException {

    private final int codigo;

    public ErroProtocolo(int codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public int codigo() {
        return codigo;
    }

    /** Lança a exceção se a resposta for ERROR; senão devolve a própria resposta. */
    public static Mensagem conferir(Mensagem resposta) throws ErroProtocolo {
        if (resposta.tipo().equals(Protocolo.ERROR)) {
            int codigo;
            try {
                codigo = Integer.parseInt(resposta.campo(0));
            } catch (NumberFormatException e) {
                codigo = Protocolo.ERRO_INTERNO;
            }
            throw new ErroProtocolo(codigo, resposta.campo(1));
        }
        return resposta;
    }
}
