package minichat.comum;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Uma mensagem do protocolo: o comando seguido dos campos, todos em texto.
 * No fio: writeUTF(comando), writeInt(quantidade de campos) e writeUTF de cada campo.
 * O writeUTF põe 2 bytes de tamanho antes do texto, então o receptor sabe onde cada
 * campo termina mesmo o TCP sendo um fluxo contínuo de bytes.
 */
public final class Mensagem {

    private static final int MAX_CAMPOS = 100_000;

    private final String tipo;
    private final String[] campos;

    public Mensagem(String tipo, String... campos) {
        this.tipo = tipo;
        this.campos = campos;
    }

    public Mensagem(String tipo, List<String> campos) {
        this(tipo, campos.toArray(new String[0]));
    }

    public String tipo() {
        return tipo;
    }

    public String campo(int i) {
        return i < campos.length ? campos[i] : "";
    }

    public List<String> campos() {
        return List.of(campos);
    }

    public void escrever(DataOutputStream saida) throws IOException {
        saida.writeUTF(tipo);
        saida.writeInt(campos.length);
        for (String campo : campos) {
            saida.writeUTF(campo);
        }
    }

    public static Mensagem ler(DataInputStream entrada) throws IOException {
        String tipo = entrada.readUTF();
        int quantidade = entrada.readInt();
        if (quantidade < 0 || quantidade > MAX_CAMPOS) {
            throw new IOException("mensagem inválida (" + quantidade + " campos)");
        }
        String[] campos = new String[quantidade];
        for (int i = 0; i < quantidade; i++) {
            campos[i] = entrada.readUTF();
        }
        return new Mensagem(tipo, campos);
    }

    @Override
    public String toString() {
        return tipo + " " + Arrays.toString(campos);
    }
}
