package minichat;

import minichat.cliente.ClienteChat;
import minichat.servidor.ServidorChat;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;

public class Iniciar {

    public static void main(String[] args) throws Exception {
        String modo = args.length > 0 ? args[0].toLowerCase() : "";
        String[] resto = args.length > 0 ? Arrays.copyOfRange(args, 1, args.length) : args;

        if (modo.equals("servidor")) {
            ServidorChat.main(resto);
        } else if (modo.equals("cliente")) {
            ClienteChat.main(resto);
        } else if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Uso: java -jar MiniServidorChat.jar servidor|cliente");
        } else {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignorada) {
            }
            Object[] opcoes = {"Servidor", "Cliente"};
            int escolha = JOptionPane.showOptionDialog(null, "O que você quer abrir nesta máquina?",
                    "Mini Servidor de Chat (TCP)", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, opcoes, opcoes[1]);
            if (escolha == 0) {
                ServidorChat.main(resto);
            } else if (escolha == 1) {
                ClienteChat.main(resto);
            }
        }
    }
}
