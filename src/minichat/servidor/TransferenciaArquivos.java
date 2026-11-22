package minichat.servidor;

import minichat.comum.InfoArquivo;
import minichat.comum.Mensagem;
import minichat.comum.Protocolo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

class TransferenciaArquivos {

    private final ServidorChat servidor;
    private final String endereco;
    private final DataInputStream entrada;
    private final DataOutputStream saida;

    TransferenciaArquivos(ServidorChat servidor, String endereco, DataInputStream entrada, DataOutputStream saida) {
        this.servidor = servidor;
        this.endereco = endereco;
        this.entrada = entrada;
        this.saida = saida;
    }

    /** UPLOAD(token, nome, tamanho) → OK → bytes do arquivo → OK(nome final). */
    void receber(Mensagem pedido) throws IOException {
        ClienteConectado autor = servidor.clientePorToken(pedido.campo(0));
        String nick = autor != null ? autor.nickname() : "-";
        String nome = RepositorioArquivos.limparNome(pedido.campo(1));
        long tamanho = paraNumero(pedido.campo(2));

        if (autor == null) {
            recusar(nick, "UPLOAD", Protocolo.ERRO_SEM_LOGIN, "faça login antes de enviar arquivos");
            return;
        }
        if (nome.isEmpty() || tamanho < 0) {
            recusar(nick, "UPLOAD", Protocolo.ERRO_REQUISICAO, "nome ou tamanho de arquivo inválido");
            return;
        }
        if (!servidor.repositorio().temEspacoPara(tamanho)) {
            recusar(nick, "UPLOAD", Protocolo.ERRO_SEM_ESPACO, "o servidor está sem espaço para " + nome);
            return;
        }
        responder(new Mensagem(Protocolo.OK));
        registrar(nick, "UPLOAD", "INICIO", nome + " (" + InfoArquivo.tamanhoLegivel(tamanho) + ")");

        // Grava num arquivo à parte: só vai para a pasta central (e para a lista) se chegar inteiro.
        Path parcial = servidor.repositorio().novoParcial();
        long[] recebidos = {0};
        long inicio = System.currentTimeMillis();
        try (OutputStream arquivo = Files.newOutputStream(parcial)) {
            Protocolo.copiarEmBlocos(entrada, arquivo, tamanho, n -> recebidos[0] = n);
        } catch (IOException e) {
            Files.deleteIfExists(parcial);
            registrar(nick, "UPLOAD", "ERRO", nome + ": interrompido com " + InfoArquivo.tamanhoLegivel(recebidos[0])
                    + " de " + InfoArquivo.tamanhoLegivel(tamanho) + " (" + descrever(e) + "); arquivo parcial descartado");
            return;
        }

        InfoArquivo salvo;
        try {
            salvo = servidor.repositorio().guardar(parcial, nome, nick);
        } catch (IOException e) {
            Files.deleteIfExists(parcial);
            recusar(nick, "UPLOAD", Protocolo.ERRO_INTERNO, "falha ao gravar " + nome + ": " + e.getMessage());
            return;
        }
        double segundos = (System.currentTimeMillis() - inicio) / 1000.0;
        registrar(nick, "UPLOAD", "OK", salvo.nome() + " (" + salvo.tamanhoLegivel() + ") em "
                + String.format(Locale.forLanguageTag("pt-BR"), "%.1f", segundos) + " s" + (salvo.nome().equals(nome) ? "" : ", renomeado de " + nome));
        try {
            responder(new Mensagem(Protocolo.OK, salvo.nome()));
        } catch (IOException ignorada) {
            // O arquivo chegou inteiro e já está salvo; anuncia mesmo se o cliente fechou agora.
        }
        servidor.anunciarArquivo(salvo);
    }

    /** DOWNLOAD(token, nome) → OK(tamanho) + bytes do arquivo, ou ERROR 404. */
    void enviar(Mensagem pedido) throws IOException {
        ClienteConectado cliente = servidor.clientePorToken(pedido.campo(0));
        String nick = cliente != null ? cliente.nickname() : "-";
        String nome = pedido.campo(1);

        if (cliente == null) {
            recusar(nick, "DOWNLOAD", Protocolo.ERRO_SEM_LOGIN, "faça login antes de baixar arquivos");
            return;
        }
        InfoArquivo info = servidor.repositorio().buscar(nome);
        if (info == null) {
            recusar(nick, "DOWNLOAD", Protocolo.ERRO_NAO_ENCONTRADO, "arquivo inexistente: " + nome);
            return;
        }

        Path caminho = servidor.repositorio().caminho(info);
        long[] enviados = {0};
        long tamanho = 0;
        try (InputStream arquivo = Files.newInputStream(caminho)) {
            tamanho = Files.size(caminho);
            responder(new Mensagem(Protocolo.OK, String.valueOf(tamanho)));
            registrar(nick, "DOWNLOAD", "INICIO", info.nome() + " (" + InfoArquivo.tamanhoLegivel(tamanho) + ")");
            Protocolo.copiarEmBlocos(arquivo, saida, tamanho, n -> enviados[0] = n);
            saida.flush();
            // O cliente fecha a conexão depois de ler o último byte; esperar isso confirma a entrega.
            if (entrada.read() != -1) {
                throw new IOException("dados inesperados depois do arquivo");
            }
            registrar(nick, "DOWNLOAD", "OK", info.nome() + " (" + InfoArquivo.tamanhoLegivel(tamanho) + ")");
        } catch (IOException e) {
            registrar(nick, "DOWNLOAD", "ERRO", info.nome() + ": interrompido com " + InfoArquivo.tamanhoLegivel(enviados[0])
                    + " de " + InfoArquivo.tamanhoLegivel(tamanho) + " (" + descrever(e) + ")");
        }
    }

    private void recusar(String nick, String operacao, int codigo, String texto) throws IOException {
        registrar(nick, operacao, "ERRO", texto);
        responder(Protocolo.erro(codigo, texto));
    }

    private void responder(Mensagem mensagem) throws IOException {
        mensagem.escrever(saida);
        saida.flush();
    }

    private void registrar(String nick, String operacao, String status, String detalhes) {
        servidor.log().registrar(endereco, nick, operacao, status, detalhes);
    }

    private static long paraNumero(String texto) {
        try {
            return Long.parseLong(texto);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String descrever(IOException e) {
        if (e instanceof SocketTimeoutException) {
            return "sem dados há " + Protocolo.TIMEOUT_LEITURA_MS / 1000 + " s";
        }
        if (e instanceof EOFException) {
            return "o cliente fechou a conexão";
        }
        return e.getMessage();
    }
}
