import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.swing.*;

public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();

    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String server;
    private int port;

    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    // Construtor
    public ChatClient(String server, int port) throws IOException {
        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(
            new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        // Sempre que uma mensagem é inserida na caixa de texto, esta função é chamada.
                        newMessage(chatBox.getText());
                    } catch (IOException ex) {} finally {
                        chatBox.setText("");
                    }
                }
            }
        );
        frame.addWindowListener(
            new WindowAdapter() {
                public void windowOpened(WindowEvent e) {
                    chatBox.requestFocusInWindow();
                }
            }
        );
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui

        this.server = server;
        this.port = port;
    }

    // Método invocado sempre que o utilizador insere uma mensagem na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor
        if (out != null) {
            // fazer escape de '/' se necessário
            String escapedMessage = processMessage(message);
            // imprimir a mensagem (não para o stdout como é hábito, mas para o servidor)
            out.println(escapedMessage);
            out.flush();
        }
    }
    // processar a mensagem
    private static String processMessage(String message) {
        String escapedMessage = "";
        // se a mensagem começar por '/'
        if (message.charAt(0) == '/') {
            // adicionar um '/' ao início da mensagem
            escapedMessage = '/' + message;
        }
        else {
            escapedMessage = message;
        }
        return escapedMessage;
    }

    // Método principal do objecto
    public void run() throws IOException {
        try {
            // Create socket connection
            socket = new Socket(server, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(
                new InputStreamReader(socket.getInputStream())
            );

            // Start a thread to listen for messages from server
            Thread listenerThread = new Thread(new ServerListener());
            listenerThread.start();

        } catch (IOException e) {
            throw e;
        }
    }

    // Escutar o Servidor para receber mensagens
    private class ServerListener implements Runnable {

        @Override
        public void run() {
            try {
                String serverMessage;
                while ((serverMessage = in.readLine()) != null) {
                    // This will be called from a non-EDT thread, so use SwingUtilities
                    final String msg = serverMessage + "\n";
                    SwingUtilities.invokeLater(
                        new Runnable() {
                            @Override
                            public void run() {
                                printMessage(msg);
                            }
                        }
                    );
                }
            } catch (IOException e) {
            }
        }
    }

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }
}
