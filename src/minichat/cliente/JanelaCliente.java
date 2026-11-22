package minichat.cliente;

import minichat.comum.ErroProtocolo;
import minichat.comum.InfoArquivo;
import minichat.comum.Protocolo;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Janela do cliente. A rede nunca roda na thread da interface: conectar, enviar e baixar
 * usam SwingWorker, e o que chega do servidor volta para a tela por SwingUtilities.invokeLater.
 */
public class JanelaCliente extends JFrame {

    private static final Color VERDE = new Color(0, 128, 60);
    private static final Color VERMELHO = new Color(190, 30, 30);
    private static final Color LARANJA = new Color(190, 110, 0);
    private static final Color CINZA = new Color(110, 110, 110);

    private static final SimpleAttributeSet HORA = estilo(CINZA, false, false, null);
    private static final SimpleAttributeSet NICK = estilo(new Color(40, 40, 40), true, false, null);
    private static final SimpleAttributeSet NICK_PROPRIO = estilo(new Color(0, 90, 180), true, false, null);
    private static final SimpleAttributeSet TEXTO = estilo(Color.BLACK, false, false, null);
    private static final SimpleAttributeSet SISTEMA = estilo(CINZA, false, true, null);
    private static final SimpleAttributeSet ERRO = estilo(VERMELHO, false, true, null);
    private static final SimpleAttributeSet ARQUIVO = estilo(new Color(0, 70, 150), true, false, new Color(255, 240, 190));

    final JTextField campoIp = new JTextField("127.0.0.1", 11);
    final JTextField campoPorta = new JTextField(String.valueOf(Protocolo.PORTA_PADRAO), 5);
    final JTextField campoNick = new JTextField(10);
    final JButton botaoConectar = new JButton("Conectar");
    private final JButton botaoSair = new JButton("Sair");

    private final JTextPane areaChat = new JTextPane();
    final JTextField campoMensagem = new JTextField();
    private final JButton botaoEnviar = new JButton("Enviar");

    private final DefaultListModel<String> modeloUsuarios = new DefaultListModel<>();
    private final JScrollPane rolagemUsuarios = new JScrollPane(new JList<>(modeloUsuarios));
    private final ModeloArquivos modeloArquivos = new ModeloArquivos();
    private final JTable tabelaArquivos = new JTable(modeloArquivos);
    private final JScrollPane rolagemArquivos = new JScrollPane(tabelaArquivos);
    private final JButton botaoUpload = new JButton("Enviar arquivo...");
    private final JButton botaoDownload = new JButton("Baixar...");

    private final JProgressBar barraUpload = new JProgressBar(0, 1000);
    private final JLabel textoUpload = new JLabel("nenhum envio");
    private final JProgressBar barraDownload = new JProgressBar(0, 1000);
    private final JLabel textoDownload = new JLabel("nenhum download");
    private final JLabel status = new JLabel();

    private ConexaoChat conexao;
    private boolean conectando;
    private boolean enviando;
    private boolean baixando;
    private File pastaEnvio = new File(System.getProperty("user.home"));
    private File pastaDownload = pastaDownloadsPadrao();

    public JanelaCliente() {
        super("Cliente de Chat (TCP)");

        JPanel painelConexao = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 8));
        painelConexao.add(new JLabel("IP do servidor:"));
        painelConexao.add(campoIp);
        painelConexao.add(new JLabel("Porta:"));
        painelConexao.add(campoPorta);
        painelConexao.add(new JLabel("Nickname:"));
        painelConexao.add(campoNick);
        painelConexao.add(botaoConectar);
        painelConexao.add(botaoSair);

        areaChat.setEditable(false);
        areaChat.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        JScrollPane rolagemChat = new JScrollPane(areaChat);
        rolagemChat.setBorder(BorderFactory.createTitledBorder("Chat"));
        JPanel linhaMensagem = new JPanel(new BorderLayout(6, 0));
        linhaMensagem.add(campoMensagem, BorderLayout.CENTER);
        linhaMensagem.add(botaoEnviar, BorderLayout.EAST);
        JPanel painelChat = new JPanel(new BorderLayout(0, 6));
        painelChat.add(rolagemChat, BorderLayout.CENTER);
        painelChat.add(linhaMensagem, BorderLayout.SOUTH);

        rolagemUsuarios.setPreferredSize(new Dimension(300, 150));
        tabelaArquivos.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabelaArquivos.setFillsViewportHeight(true);
        tabelaArquivos.getColumnModel().getColumn(0).setPreferredWidth(200);
        tabelaArquivos.getColumnModel().getColumn(1).setPreferredWidth(70);
        tabelaArquivos.getColumnModel().getColumn(2).setPreferredWidth(70);
        JPanel botoesArquivo = new JPanel(new GridLayout(1, 2, 6, 0));
        botoesArquivo.add(botaoUpload);
        botoesArquivo.add(botaoDownload);
        JPanel painelLateral = new JPanel(new BorderLayout(0, 6));
        painelLateral.add(rolagemUsuarios, BorderLayout.NORTH);
        painelLateral.add(rolagemArquivos, BorderLayout.CENTER);
        painelLateral.add(botoesArquivo, BorderLayout.SOUTH);

        JSplitPane divisao = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, painelChat, painelLateral);
        divisao.setResizeWeight(0.6);
        divisao.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        JPanel barras = new JPanel(new GridLayout(2, 1, 0, 4));
        barras.add(linhaDeProgresso("Upload", barraUpload, textoUpload));
        barras.add(linhaDeProgresso("Download", barraDownload, textoDownload));
        status.setFont(status.getFont().deriveFont(Font.BOLD, 13f));
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(6, 0, 0, 0)));
        JPanel painelSul = new JPanel(new BorderLayout(0, 8));
        painelSul.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        painelSul.add(barras, BorderLayout.CENTER);
        painelSul.add(status, BorderLayout.SOUTH);

        add(painelConexao, BorderLayout.NORTH);
        add(divisao, BorderLayout.CENTER);
        add(painelSul, BorderLayout.SOUTH);

        botaoConectar.addActionListener(e -> conectar());
        campoNick.addActionListener(e -> conectar());
        botaoSair.addActionListener(e -> desconectar());
        botaoEnviar.addActionListener(e -> enviarMensagem());
        campoMensagem.addActionListener(e -> enviarMensagem());
        botaoUpload.addActionListener(e -> enviarArquivo());
        botaoDownload.addActionListener(e -> baixarSelecionado());
        tabelaArquivos.getSelectionModel().addListSelectionListener(e -> atualizarBotoes());
        tabelaArquivos.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    baixarSelecionado();
                }
            }
        });

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                fecharJanela();
            }
        });

        atualizarTitulos();
        atualizarBotoes();
        mostrarStatus("Desconectado. Preencha IP, porta e nickname e clique em Conectar.", CINZA);
        setSize(1050, 700);
        setMinimumSize(new Dimension(820, 520));
        setLocationRelativeTo(null);
    }

    void conectar() {
        if (conexao != null || conectando) {
            return;
        }
        String host = campoIp.getText().strip();
        String nick = campoNick.getText().strip();
        int porta;
        try {
            porta = Integer.parseInt(campoPorta.getText().strip());
        } catch (NumberFormatException e) {
            mostrarStatus("Porta inválida.", VERMELHO);
            return;
        }
        if (host.isEmpty() || nick.isEmpty()) {
            mostrarStatus("Preencha o IP do servidor e o nickname.", VERMELHO);
            return;
        }

        conectando = true;
        atualizarBotoes();
        mostrarStatus("Conectando a " + host + ":" + porta + "...", LARANJA);
        new SwingWorker<ConexaoChat, Void>() {
            @Override
            protected ConexaoChat doInBackground() throws IOException {
                return new ConexaoChat(host, porta, nick);
            }

            @Override
            protected void done() {
                conectando = false;
                try {
                    ConexaoChat nova = get();
                    conexao = nova;
                    nova.iniciar(new Ouvinte(nova));
                    setTitle("Cliente de Chat (TCP) — " + nick);
                    mostrarStatus("Conectado como " + nick + " em " + host + ":" + porta, VERDE);
                    avisoLocal("Conectado ao servidor " + host + ":" + porta + " como " + nick, false);
                    campoMensagem.requestFocusInWindow();
                } catch (ExecutionException e) {
                    String erro = falhaAoConectar(e.getCause(), host, porta, nick);
                    mostrarStatus(erro, VERMELHO);
                    avisoLocal(erro, true);
                    JOptionPane.showMessageDialog(JanelaCliente.this, erro, "Não foi possível conectar",
                            JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                atualizarBotoes();
            }
        }.execute();
    }

    private void desconectar() {
        if (conexao == null) {
            return;
        }
        ConexaoChat antiga = conexao;
        conexao = null;
        new Thread(antiga::sair, "logout").start();
        limparListas();
        avisoLocal("Você saiu do chat.", false);
        mostrarStatus("Desconectado.", CINZA);
        setTitle("Cliente de Chat (TCP)");
        atualizarBotoes();
    }

    private void fecharJanela() {
        if ((enviando || baixando) && JOptionPane.showConfirmDialog(this,
                "Há uma transferência em andamento. Fechar mesmo assim?", "Sair",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        if (conexao != null) {
            conexao.sair();
        }
        dispose();
        System.exit(0);
    }

    private static String falhaAoConectar(Throwable causa, String host, int porta, String nick) {
        if (causa instanceof ErroProtocolo erro) {
            if (erro.codigo() == Protocolo.ERRO_NICK_EM_USO) {
                return "O nickname \"" + nick + "\" já está em uso. Escolha outro.";
            }
            return "O servidor recusou a conexão: " + erro.getMessage();
        }
        if (causa instanceof ConnectException || causa instanceof SocketTimeoutException
                || causa instanceof NoRouteToHostException || causa instanceof UnknownHostException) {
            return "Servidor indisponível em " + host + ":" + porta + ". Confira o IP, a porta e se o servidor está aberto.";
        }
        return "Erro ao conectar: " + causa.getMessage();
    }

    private class Ouvinte implements OuvinteChat {

        private final ConexaoChat origem;

        Ouvinte(ConexaoChat origem) {
            this.origem = origem;
        }

        // Ignora o que chegar de uma conexão antiga depois de reconectar.
        private void naTela(Runnable acao) {
            SwingUtilities.invokeLater(() -> {
                if (conexao == origem) {
                    acao.run();
                }
            });
        }

        @Override
        public void mensagem(String remetente, String dataHora, String texto, String categoria) {
            naTela(() -> adicionarNoChat(dataHora, remetente, texto, categoria));
        }

        @Override
        public void usuarios(List<String> nicknames) {
            naTela(() -> {
                modeloUsuarios.clear();
                for (String nick : nicknames) {
                    modeloUsuarios.addElement(nick.equals(origem.nickname()) ? nick + " (você)" : nick);
                }
                atualizarTitulos();
            });
        }

        @Override
        public void arquivos(List<InfoArquivo> arquivos) {
            naTela(() -> {
                modeloArquivos.definir(arquivos);
                atualizarTitulos();
                atualizarBotoes();
            });
        }

        @Override
        public void novoArquivo(InfoArquivo arquivo) {
            naTela(() -> {
                if (!enviando && !baixando) {
                    mostrarStatus("Novo arquivo disponível: " + arquivo.nome() + " (" + arquivo.tamanhoLegivel()
                            + ", enviado por " + arquivo.autor() + ")", VERDE);
                }
            });
        }

        @Override
        public void erro(String codigo, String mensagem) {
            naTela(() -> {
                mostrarStatus("Erro do servidor: " + mensagem, VERMELHO);
                avisoLocal("Erro " + codigo + ": " + mensagem, true);
            });
        }

        @Override
        public void conexaoPerdida(String motivo) {
            naTela(() -> {
                conexao = null;
                limparListas();
                String aviso = "Conexão perdida: " + motivo + ".";
                avisoLocal(aviso, true);
                mostrarStatus(aviso + " Clique em Conectar para tentar de novo.", VERMELHO);
                setTitle("Cliente de Chat (TCP)");
                atualizarBotoes();
                JOptionPane.showMessageDialog(JanelaCliente.this, aviso, "Conexão perdida", JOptionPane.WARNING_MESSAGE);
            });
        }
    }

    void enviarMensagem() {
        String texto = campoMensagem.getText().strip();
        if (texto.isEmpty() || conexao == null) {
            return;
        }
        if (texto.length() > Protocolo.MAX_TEXTO) {
            mostrarStatus("Mensagem muito longa (máximo de " + Protocolo.MAX_TEXTO + " caracteres).", VERMELHO);
            return;
        }
        conexao.enviarMensagem(texto);
        campoMensagem.setText("");
    }

    private void adicionarNoChat(String dataHora, String remetente, String texto, String categoria) {
        String hora = dataHora.length() >= 16 ? dataHora.substring(11, 16) : dataHora;
        inserir("[" + hora + "] ", HORA);
        switch (categoria) {
            case Protocolo.CATEGORIA_ARQUIVO -> inserir(" [arquivo] " + texto + " ", ARQUIVO);
            case Protocolo.CATEGORIA_SISTEMA -> inserir(texto, SISTEMA);
            default -> {
                boolean proprio = conexao != null && remetente.equals(conexao.nickname());
                inserir(remetente + ": ", proprio ? NICK_PROPRIO : NICK);
                inserir(texto, TEXTO);
            }
        }
        inserir("\n", TEXTO);
    }

    private void avisoLocal(String texto, boolean erro) {
        inserir("[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + "] ", HORA);
        inserir(texto + "\n", erro ? ERRO : SISTEMA);
    }

    private void inserir(String texto, SimpleAttributeSet estilo) {
        StyledDocument documento = areaChat.getStyledDocument();
        try {
            documento.insertString(documento.getLength(), texto, estilo);
        } catch (BadLocationException ignorada) {
        }
        areaChat.setCaretPosition(documento.getLength());
    }

    private void enviarArquivo() {
        JFileChooser seletor = new JFileChooser(pastaEnvio);
        seletor.setDialogTitle("Escolha o arquivo para enviar");
        if (seletor.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File arquivo = seletor.getSelectedFile();
        pastaEnvio = arquivo.getParentFile();
        if (!arquivo.isFile()) {
            mostrarStatus("Arquivo não encontrado: " + arquivo, VERMELHO);
            return;
        }
        enviar(arquivo);
    }

    void enviar(File arquivo) {
        ConexaoChat atual = conexao;
        String nome = arquivo.getName();
        long tamanho = arquivo.length();
        enviando = true;
        atualizarBotoes();
        atualizarProgresso(barraUpload, textoUpload, nome, 0, tamanho);
        mostrarStatus("Enviando " + nome + " (" + InfoArquivo.tamanhoLegivel(tamanho) + ")...", LARANJA);

        new SwingWorker<String, Long>() {
            @Override
            protected String doInBackground() throws IOException {
                return TransferenciaCliente.enviar(atual, arquivo, feito -> publish(feito));
            }

            @Override
            protected void process(List<Long> feitos) {
                atualizarProgresso(barraUpload, textoUpload, nome, feitos.get(feitos.size() - 1), tamanho);
            }

            @Override
            protected void done() {
                enviando = false;
                try {
                    String salvoComo = get();
                    atualizarProgresso(barraUpload, textoUpload, nome, tamanho, tamanho);
                    mostrarStatus("Arquivo enviado: " + salvoComo
                            + (salvoComo.equals(nome) ? "" : " (renomeado: já existia um " + nome + ")"), VERDE);
                } catch (ExecutionException e) {
                    String erro = "Falha ao enviar " + nome + ": " + falhaNaTransferencia(e.getCause());
                    textoUpload.setText(nome + " — falhou");
                    mostrarStatus(erro, VERMELHO);
                    JOptionPane.showMessageDialog(JanelaCliente.this, erro, "Upload", JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                atualizarBotoes();
            }
        }.execute();
    }

    private void baixarSelecionado() {
        int linha = tabelaArquivos.getSelectedRow();
        if (linha < 0 || conexao == null || baixando) {
            return;
        }
        InfoArquivo info = modeloArquivos.get(tabelaArquivos.convertRowIndexToModel(linha));
        JFileChooser seletor = new JFileChooser(pastaDownload);
        seletor.setDialogTitle("Onde salvar " + info.nome() + "?");
        seletor.setSelectedFile(new File(pastaDownload, info.nome()));
        if (seletor.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File destino = seletor.getSelectedFile();
        pastaDownload = destino.getParentFile();
        if (destino.exists() && JOptionPane.showConfirmDialog(this, destino.getName() + " já existe. Substituir?",
                "Baixar", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        baixar(info, destino);
    }

    void baixar(InfoArquivo info, File destino) {
        ConexaoChat atual = conexao;
        baixando = true;
        atualizarBotoes();
        atualizarProgresso(barraDownload, textoDownload, info.nome(), 0, info.tamanho());
        mostrarStatus("Baixando " + info.nome() + "...", LARANJA);

        new SwingWorker<Void, Long>() {
            @Override
            protected Void doInBackground() throws IOException {
                TransferenciaCliente.baixar(atual, info.nome(), destino, feito -> publish(feito));
                return null;
            }

            @Override
            protected void process(List<Long> feitos) {
                atualizarProgresso(barraDownload, textoDownload, info.nome(), feitos.get(feitos.size() - 1), info.tamanho());
            }

            @Override
            protected void done() {
                baixando = false;
                atualizarBotoes();
                try {
                    get();
                    atualizarProgresso(barraDownload, textoDownload, info.nome(), info.tamanho(), info.tamanho());
                    mostrarStatus("Download concluído: " + destino.getAbsolutePath(), VERDE);
                    if (JOptionPane.showConfirmDialog(JanelaCliente.this, "Download concluído:\n" + destino.getAbsolutePath()
                            + "\n\nAbrir o arquivo agora?", "Download", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                        abrirArquivo(destino);
                    }
                } catch (ExecutionException e) {
                    String erro = "Falha ao baixar " + info.nome() + ": " + falhaNaTransferencia(e.getCause());
                    textoDownload.setText(info.nome() + " — falhou");
                    mostrarStatus(erro, VERMELHO);
                    JOptionPane.showMessageDialog(JanelaCliente.this, erro, "Download", JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void abrirArquivo(File arquivo) {
        try {
            Desktop.getDesktop().open(arquivo);
        } catch (IOException | UnsupportedOperationException e) {
            mostrarStatus("Não consegui abrir " + arquivo.getName() + ": " + e.getMessage(), VERMELHO);
        }
    }

    private static String falhaNaTransferencia(Throwable causa) {
        if (causa instanceof ErroProtocolo) {
            return causa.getMessage();
        }
        if (causa instanceof ConnectException) {
            return "servidor indisponível";
        }
        if (causa instanceof SocketTimeoutException) {
            return "o servidor parou de responder";
        }
        if (causa instanceof EOFException || causa instanceof SocketException) {
            return "a conexão caiu no meio da transferência";
        }
        return causa.getMessage();
    }

    private void atualizarBotoes() {
        boolean conectado = conexao != null;
        boolean livre = !conectado && !conectando;
        campoIp.setEnabled(livre);
        campoPorta.setEnabled(livre);
        campoNick.setEnabled(livre);
        botaoConectar.setEnabled(livre);
        botaoSair.setEnabled(conectado);
        campoMensagem.setEnabled(conectado);
        botaoEnviar.setEnabled(conectado);
        botaoUpload.setEnabled(conectado && !enviando);
        botaoDownload.setEnabled(conectado && !baixando && tabelaArquivos.getSelectedRow() >= 0);
    }

    private void atualizarTitulos() {
        rolagemUsuarios.setBorder(BorderFactory.createTitledBorder("Usuários conectados (" + modeloUsuarios.size() + ")"));
        rolagemArquivos.setBorder(BorderFactory.createTitledBorder("Arquivos no servidor (" + modeloArquivos.getRowCount() + ")"));
    }

    private void limparListas() {
        modeloUsuarios.clear();
        modeloArquivos.definir(List.of());
        atualizarTitulos();
    }

    private void mostrarStatus(String texto, Color cor) {
        status.setText(texto);
        status.setForeground(cor);
    }

    private static void atualizarProgresso(JProgressBar barra, JLabel texto, String nome, long feito, long total) {
        int milesimos = total == 0 ? 1000 : (int) (feito * 1000 / total);
        barra.setValue(milesimos);
        texto.setText(String.format("%s — %d%% (%s de %s)", nome, milesimos / 10,
                InfoArquivo.tamanhoLegivel(feito), InfoArquivo.tamanhoLegivel(total)));
    }

    private static JPanel linhaDeProgresso(String titulo, JProgressBar barra, JLabel texto) {
        JLabel rotulo = new JLabel(titulo);
        rotulo.setPreferredSize(new Dimension(75, 20));
        texto.setPreferredSize(new Dimension(380, 20));
        texto.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        JPanel linha = new JPanel(new BorderLayout());
        linha.add(rotulo, BorderLayout.WEST);
        linha.add(barra, BorderLayout.CENTER);
        linha.add(texto, BorderLayout.EAST);
        return linha;
    }

    private static SimpleAttributeSet estilo(Color cor, boolean negrito, boolean italico, Color fundo) {
        SimpleAttributeSet estilo = new SimpleAttributeSet();
        StyleConstants.setForeground(estilo, cor);
        StyleConstants.setBold(estilo, negrito);
        StyleConstants.setItalic(estilo, italico);
        if (fundo != null) {
            StyleConstants.setBackground(estilo, fundo);
        }
        return estilo;
    }

    private static File pastaDownloadsPadrao() {
        File downloads = new File(System.getProperty("user.home"), "Downloads");
        return downloads.isDirectory() ? downloads : new File(System.getProperty("user.home"));
    }

    private static class ModeloArquivos extends AbstractTableModel {

        private static final String[] COLUNAS = {"Nome", "Tamanho", "Autor"};
        private List<InfoArquivo> arquivos = new ArrayList<>();

        void definir(List<InfoArquivo> novos) {
            arquivos = new ArrayList<>(novos);
            fireTableDataChanged();
        }

        InfoArquivo get(int linha) {
            return arquivos.get(linha);
        }

        @Override
        public int getRowCount() {
            return arquivos.size();
        }

        @Override
        public int getColumnCount() {
            return COLUNAS.length;
        }

        @Override
        public String getColumnName(int coluna) {
            return COLUNAS[coluna];
        }

        @Override
        public Object getValueAt(int linha, int coluna) {
            InfoArquivo a = arquivos.get(linha);
            return switch (coluna) {
                case 0 -> a.nome();
                case 1 -> a.tamanhoLegivel();
                default -> a.autor();
            };
        }
    }
}
