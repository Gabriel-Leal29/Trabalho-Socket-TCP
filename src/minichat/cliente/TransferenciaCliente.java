package minichat.cliente;

import minichat.comum.ErroProtocolo;
import minichat.comum.Mensagem;
import minichat.comum.Protocolo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.LongConsumer;

public final class TransferenciaCliente {

    private TransferenciaCliente() {
    }

    /** Envia o arquivo e devolve o nome com que o servidor salvou (pode ter sido renomeado). */
    public static String enviar(ConexaoChat conexao, File arquivo, LongConsumer progresso) throws IOException {
        long tamanho = arquivo.length();
        try (Socket socket = abrir(conexao)) {
            DataInputStream entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream saida = new DataOutputStream(
                    new BufferedOutputStream(socket.getOutputStream(), Protocolo.TAMANHO_BUFFER));

            // O tamanho vai antes dos bytes: é assim que o servidor sabe onde o arquivo termina.
            new Mensagem(Protocolo.UPLOAD, conexao.token(), arquivo.getName(), String.valueOf(tamanho)).escrever(saida);
            saida.flush();
            ErroProtocolo.conferir(Mensagem.ler(entrada));

            try (InputStream origem = Files.newInputStream(arquivo.toPath())) {
                Protocolo.copiarEmBlocos(origem, saida, tamanho, progresso);
            }
            saida.flush();
            return ErroProtocolo.conferir(Mensagem.ler(entrada)).campo(0);
        }
    }

    /** Baixa para um arquivo .parcial e só renomeia para o destino quando chegou tudo. */
    public static void baixar(ConexaoChat conexao, String nome, File destino, LongConsumer progresso) throws IOException {
        Path parcial = Path.of(destino.getPath() + ".parcial");
        try (Socket socket = abrir(conexao)) {
            DataInputStream entrada = new DataInputStream(
                    new BufferedInputStream(socket.getInputStream(), Protocolo.TAMANHO_BUFFER));
            DataOutputStream saida = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            new Mensagem(Protocolo.DOWNLOAD, conexao.token(), nome).escrever(saida);
            saida.flush();
            long tamanho = Long.parseLong(ErroProtocolo.conferir(Mensagem.ler(entrada)).campo(0));

            try (OutputStream arquivo = Files.newOutputStream(parcial)) {
                Protocolo.copiarEmBlocos(entrada, arquivo, tamanho, progresso);
            }
            Files.move(parcial, destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(parcial);
            throw e;
        }
    }

    private static Socket abrir(ConexaoChat conexao) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(conexao.host(), conexao.porta()), Protocolo.TIMEOUT_CONEXAO_MS);
            socket.setSoTimeout(Protocolo.TIMEOUT_LEITURA_MS);
            return socket;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }
}
