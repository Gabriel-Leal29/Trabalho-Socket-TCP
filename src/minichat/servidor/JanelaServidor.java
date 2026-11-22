package minichat.servidor;

import minichat.comum.InfoArquivo;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JanelaServidor extends JFrame {

    private final ServidorChat servidor;
    private final DefaultListModel<String> modeloUsuarios = new DefaultListModel<>();
    private final DefaultListModel<String> modeloArquivos = new DefaultListModel<>();
    private final JList<String> listaUsuarios = new JList<>(modeloUsuarios);
    private final JList<String> listaArquivos = new JList<>(modeloArquivos);
    private final JTextArea areaLog = new JTextArea();

    public JanelaServidor(ServidorChat servidor) {
        super("Servidor de Chat (TCP)");
        this.servidor = servidor;

        JLabel endereco = new JLabel("<html>Servidor ativo. Nos clientes, use o IP <b>"
                + String.join("</b> ou <b>", enderecosLocais()) + "</b> e a porta <b>" + servidor.porta() + "</b></html>");
        endereco.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        endereco.setFont(endereco.getFont().deriveFont(15f));

        JScrollPane rolagemUsuarios = new JScrollPane(listaUsuarios);
        JScrollPane rolagemArquivos = new JScrollPane(listaArquivos);
        JPanel listas = new JPanel(new GridLayout(2, 1, 0, 8));
        listas.add(rolagemUsuarios);
        listas.add(rolagemArquivos);

        areaLog.setEditable(false);
        areaLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane rolagemLog = new JScrollPane(areaLog);
        rolagemLog.setBorder(BorderFactory.createTitledBorder("log_servidor.txt"));

        JSplitPane divisao = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listas, rolagemLog);
        divisao.setDividerLocation(260);
        divisao.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        JButton abrirLog = new JButton("Abrir log_servidor.txt");
        abrirLog.addActionListener(e -> abrir(servidor.pastaBase().resolve("log_servidor.txt").toFile()));
        JButton abrirPasta = new JButton("Abrir pasta central");
        abrirPasta.addActionListener(e -> abrir(servidor.pastaBase().resolve("arquivos_servidor").toFile()));
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botoes.add(abrirLog);
        botoes.add(abrirPasta);

        add(endereco, BorderLayout.NORTH);
        add(divisao, BorderLayout.CENTER);
        add(botoes, BorderLayout.SOUTH);

        servidor.log().aoRegistrar(linha -> SwingUtilities.invokeLater(() -> {
            areaLog.append(linha + "\n");
            areaLog.setCaretPosition(areaLog.getDocument().getLength());
        }));
        servidor.aoMudar(() -> SwingUtilities.invokeLater(this::atualizarListas));
        atualizarListas();

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 600);
        setLocationRelativeTo(null);
    }

    private void atualizarListas() {
        modeloUsuarios.clear();
        servidor.nicknames().forEach(modeloUsuarios::addElement);
        modeloArquivos.clear();
        for (InfoArquivo a : servidor.repositorio().listar()) {
            modeloArquivos.addElement(a.nome() + "  (" + a.tamanhoLegivel() + ", " + a.autor() + ")");
        }
        definirTitulo(listaUsuarios, "Usuários conectados (" + modeloUsuarios.size() + ")");
        definirTitulo(listaArquivos, "Pasta central (" + modeloArquivos.size() + ")");
    }

    private static void definirTitulo(JList<String> lista, String titulo) {
        JScrollPane rolagem = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, lista);
        TitledBorder borda = BorderFactory.createTitledBorder(titulo);
        rolagem.setBorder(borda);
        rolagem.repaint();
    }

    private void abrir(File arquivo) {
        try {
            Desktop.getDesktop().open(arquivo);
        } catch (IOException | UnsupportedOperationException e) {
            JOptionPane.showMessageDialog(this, "Não consegui abrir " + arquivo + ":\n" + e.getMessage());
        }
    }

    /** IPs desta máquina na rede local (o que deve ser digitado nos clientes). */
    static List<String> enderecosLocais() {
        List<String> enderecos = new ArrayList<>();
        try {
            for (NetworkInterface placa : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!placa.isUp() || placa.isLoopback() || placa.isVirtual()) {
                    continue;
                }
                for (InetAddress endereco : Collections.list(placa.getInetAddresses())) {
                    if (endereco instanceof Inet4Address && !endereco.isLinkLocalAddress()) {
                        enderecos.add(endereco.getHostAddress());
                    }
                }
            }
        } catch (SocketException ignorada) {
        }
        if (enderecos.isEmpty()) {
            enderecos.add("127.0.0.1");
        }
        return enderecos;
    }
}
