import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ChatServer {

    // Decoder for incoming text -- assume UTF-8
    private static final Charset charset = Charset.forName("UTF8");
    private static final CharsetDecoder decoder = charset.newDecoder();

    // enumerador para representar os tipos de mensagens (descrições copiadas do enunciado)
    enum MsgType {
        MSG_OK,             // Usado para indicar sucesso do comando enviado pelo cliente.
        MSG_ERROR,          // Usado para indicar insucesso do comando enviado pelo cliente.
        MSG_MESSAGE,        // Usado para difundir aos utilizadores numa sala a mensagem (simples) enviada pelo utilizador nome, também nessa sala.
        MSG_NEWNICK,        // Usado para indicar a todos os utilizadores duma sala que o utilizador nome_antigo, que está nessa sala, mudou de nome para nome_novo.
        MSG_JOINED,         // Usado para indicar aos utilizadores numa sala que entrou um novo utilizador, com o nome nome, nessa sala.
        MSG_LEFT,           // Usado para indicar aos utilizadores numa sala que o utilizador com o nome nome, que também se encontrava nessa sala, saiu.
        MSG_BYE,            // Usado para confirmar a um utilizador que invocou o comando /bye a sua saída.
        MSG_PRIVATE
    }
    // enumerador para representar informação do estado de cada cliente (descrições copiadas do enunciado)
    enum ClientState {
        INIT,           // Estado inicial de um utilizador que acabou de estabelecer a conexão ao servidor e, portanto, ainda não tem um nome associado.
        OUTSIDE,        // O utilizador já tem um nome associado, mas não está em nenhuma sala de chat.
        INSIDE          // O utilizador está numa sala de chat, podendo enviar mensagens simples (para essa sala) e devendo receber todas as mensagens que os outros utilizadores nessa sala enviem.
    }
    // enumerador para representar os comandos disponíveis
    enum Command {
        NICK,           // Usado para escolher um nome ou para mudar de nome. O nome escolhido não pode estar já a ser usado por outro utilizador.  
        JOIN,           // Usado para entrar numa sala de chat ou para mudar de sala. Se a sala ainda não existir, é criada.
        LEAVE,          // Usado para o utilizador sair da sala de chat em que se encontra.
        BYE,            // Usado para sair do chat.
        MESSAGE,
        PRIVATE
    }

    // Informação de cada cliente
    private static int nextClientId = 1;
    private static Map<SocketChannel, ClientInfo> clients = new HashMap<>();

    private static class ClientInfo {
        int id;
        String name;
        String room;
        ByteBuffer buffer;
        ClientState state;

        ClientInfo() {
            this.id = nextClientId++;
            this.name = "Client_" + this.id;
            this.room = "";
            this.buffer = ByteBuffer.allocate(16384);
            this.state = ClientState.OUTSIDE;
        };
    }

    // Salas de chat
    private static Set<String> rooms = new HashSet<>();
    // Todos os nomes
    private static Set<String> names = new HashSet<>();
    

    public static void main(String args[]) throws Exception {
        // Parse port from command line
        int port = Integer.parseInt(args[0]);

        try {
            // Instead of creating a ServerSocket, create a ServerSocketChannel
            ServerSocketChannel ssc = ServerSocketChannel.open();

            // Set it to non-blocking, so we can use select
            ssc.configureBlocking(false);

            // Get the Socket connected to this channel, and bind it to the
            // listening port
            ServerSocket ss = ssc.socket();
            InetSocketAddress isa = new InetSocketAddress(port);
            ss.bind(isa);

            // Create a new Selector for selecting
            Selector selector = Selector.open();

            // Register the ServerSocketChannel, so we can listen for incoming
            // connections
            ssc.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Listening on port " + port);

            while (true) {
                // See if we've had any activity -- either an incoming connection,
                // or incoming data on an existing connection
                int num = selector.select();

                // If we don't have any activity, loop around and wait again
                if (num == 0) {
                    continue;
                }

                // Get the keys corresponding to the activity that has been
                // detected, and process them one by one
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();
                while (it.hasNext()) {
                    // Get a key representing one of bits of I/O activity
                    SelectionKey key = it.next();

                    // What kind of activity is it?
                    if (key.isAcceptable()) {
                        // It's an incoming connection.  Register this socket with
                        // the Selector so we can listen for input on it
                        Socket s = ss.accept();
                        // Make sure to make it non-blocking, so we can use a selector
                        // on it.
                        SocketChannel sc = s.getChannel();
                        sc.configureBlocking(false);

                        // Criar e Guardar Novo Cliente
                        ClientInfo client = new ClientInfo();
                        clients.put(sc, client);
                        names.add(client.name);

                        // Register it with the selector, for reading
                        sc.register(selector, SelectionKey.OP_READ);
                        System.out.println("New client: " + client.name + " from " + s);

                    } else if (key.isReadable()) {
                        SocketChannel sc = null;

                        try {
                            // It's incoming data on a connection -- process it
                            sc = (SocketChannel) key.channel();
                            boolean ok = processInput(sc, selector.keys());

                            // If the connection is dead, remove it from the selector
                            // and close it
                            if (!ok) {
                                key.cancel();

                                Socket s = null;
                                try {
                                    s = sc.socket();
                                    System.out.println(
                                        "Closing connection to " + s
                                    );
                                    s.close();
                                } catch (IOException ie) {
                                    System.err.println(
                                        "Error closing socket " + s + ": " + ie
                                    );
                                }
                            }
                        } catch (IOException ie) {
                            // On exception, remove this channel from the selector
                            key.cancel();

                            try {
                                sc.close();
                            } catch (IOException ie2) {
                                System.out.println(ie2);
                            }

                            System.out.println("Closed " + sc);
                        }
                    }
                }
                // We remove the selected keys, because we've dealt with them.
                keys.clear();
            }
        } catch (IOException ie) {
            System.err.println(ie);
        }
    }

    // Processar mensagem
    private static boolean processInput(SocketChannel sc, Set<SelectionKey> keys) throws IOException {
        // usar buffer de cada cliente
        ClientInfo client = clients.get(sc);
        ByteBuffer clientBuffer = client.buffer;
        
        int bytesRead = sc.read(clientBuffer);

        if (bytesRead == -1) {
            return false;
        }

        processClientBuffer(sc, client);

        return true;
    }

    // lidar com a delineação de mensagens
    private static void processClientBuffer(SocketChannel sc, ClientInfo client) {
        ByteBuffer buffer = client.buffer;

        buffer.flip();

        String received = null;
        try {
            received = decoder.decode(buffer).toString();
        } catch (CharacterCodingException e) {
            e.printStackTrace();
        }
        // percorrer a string
        int start = 0;
        while (true) {
            // marcar a primeira instância de '\n'
            int newlinePosition = received.indexOf('\n', start);
            // se não houver nenhuma, guardar a string no buffer novamente (ainda não recebemos a mensagem toda)
            if (newlinePosition == -1) {
                buffer.clear();
                buffer.put(received.getBytes());
                break;
            }
            // se houver, processar a linha até essa posição
            String line = received.substring(start, newlinePosition).trim();
            // não enviar linhas vazias
            if (!line.isEmpty()) {
                processLine(sc, client, line);
            }
            // processar próxima linha, isto é, marcar start como o próximo index depois do último newline
            start = newlinePosition + 1;
        }
    }

    private static void processLine(SocketChannel sc, ClientInfo client, String line) {
        // método semelhante (mas inverso) ao implementado no cliente para fazer escape de '/'
        String escapedLine = "";
        if (line.charAt(0) == '/') {
            escapedLine = line.substring(1);
        }
        else {
            escapedLine = line;
        }

        if (escapedLine.startsWith("/nick")) {
            String newNick = escapedLine.substring(6);
            processTransition(Command.NICK, sc, newNick);
        }
        else if (escapedLine.startsWith("/join")) {
            String newRoom = escapedLine.substring(6);
            processTransition(Command.JOIN, sc, newRoom);
        }
        else if (escapedLine.startsWith("/leave")) {
            processTransition(Command.LEAVE, sc, "null");
        }
        else if (escapedLine.startsWith("/bye")) {
            processTransition(Command.BYE, sc, "null");
        }
        else if (escapedLine.startsWith("/priv")) {
            String privMsg = escapedLine.substring(6);
            processTransition(Command.PRIVATE, sc, privMsg);
        }
        else {
            processTransition(Command.MESSAGE, sc, escapedLine);
        }
    }

    private static List<SocketChannel> allUsersInRoom(SocketChannel sc, String room) {
        List<SocketChannel> output = new LinkedList<>();
        for (Map.Entry<SocketChannel, ClientInfo> c : clients.entrySet()) {
            if (c.getValue().room == room) {
                output.add(c.getKey());
            }
        }
        return output;
    }

    private static List<SocketChannel> otherUsersInRoom(SocketChannel sc, String room) {
        List<SocketChannel> output = new LinkedList<>();
        for (Map.Entry<SocketChannel, ClientInfo> c : clients.entrySet()) {
            if (c.getKey() != sc && c.getValue().room == room) {
                output.add(c.getKey());
            }
        }
        return output;
    }

    private static void processTransition(Command cmd, SocketChannel sc, String msg) {
        // máquina de estados
        switch (cmd) {

            case NICK:
                // nome já existe
                if (names.contains(msg)) {
                    unicast(MsgType.MSG_ERROR, sc, "null", "null");
                }
                else {
                    String oldName = clients.get(sc).name;
                    clients.get(sc).name = msg;
                    names.add(clients.get(sc).name);
                    names.remove(oldName);
                    unicast(MsgType.MSG_OK, sc, "null", "null");

                    if (clients.get(sc).state == ClientState.INSIDE) {
                        broadcast(MsgType.MSG_NEWNICK, sc, oldName);
                    }
                    else {
                        clients.get(sc).state = ClientState.OUTSIDE;
                    }                
                }
                break;

            case JOIN:
                if (clients.get(sc).state == ClientState.INIT) {
                    unicast(MsgType.MSG_ERROR, sc, "null", "null");
                } else {
                    if (!rooms.contains(msg)) {
                        rooms.add(msg);
                    }
                    String oldRoom = clients.get(sc).room;
                    clients.get(sc).room = msg;
                    unicast(MsgType.MSG_OK, sc, "null", "null");
                    broadcast(MsgType.MSG_JOINED, sc, "null");

                    if (clients.get(sc).state == ClientState.INSIDE) {
                        broadcast(MsgType.MSG_LEFT, sc, oldRoom);
                    }
                    else {
                        clients.get(sc).state = ClientState.INSIDE;
                    }
                }
                break;
            
            case LEAVE:
                if (clients.get(sc).state != ClientState.INSIDE) {
                    unicast(MsgType.MSG_ERROR, sc, "null", "null");
                } 
                else {
                    String oldRoom = clients.get(sc).room;
                    clients.get(sc).room = "";
                    clients.get(sc).state = ClientState.OUTSIDE;

                    unicast(MsgType.MSG_OK, sc, "null", "null");
                    broadcast(MsgType.MSG_LEFT, sc, oldRoom);
                }
                break;
            
            case BYE:
                names.remove(clients.get(sc).name);
                unicast(MsgType.MSG_BYE, sc, "null", "null");
                if (clients.get(sc).state == ClientState.INSIDE) {
                    broadcast(MsgType.MSG_LEFT, sc, "null");
                }
                break;
            
            case MESSAGE:
                if (clients.get(sc).state == ClientState.INSIDE) {
                    broadcast(MsgType.MSG_MESSAGE, sc, msg);
                }
                else {
                    unicast(MsgType.MSG_ERROR, sc, "null", "null");
                }
                break;

            case PRIVATE:
                // mensagem privada, o token antes do primeiro espaço é o nome do destinatário
                String[] parts = msg.split(" ", 2);
                if (parts.length == 2) {
                    String name = parts[0];
                    String privMsg = parts[1];
                    // procurar no conjunto de nomes se o destinatário existe
                    if (names.contains(name)) {
                        unicast(MsgType.MSG_OK, sc, "null", "null");
                        // transformar a socket na socket do destinatário
                        for (Map.Entry<SocketChannel, ClientInfo> c : clients.entrySet()) {
                            if (c.getValue().name == name) {
                                sc = c.getKey();
                                break;
                            }
                        }
                        unicast(MsgType.MSG_PRIVATE, sc, privMsg, name);
                    }
                    else {
                        unicast(MsgType.MSG_ERROR, sc, "null", "null");
                    }
                }
                else {
                    unicast(MsgType.MSG_ERROR, sc, "null", "null");
                }
                break;
        }
    }

    private static void unicast(MsgType type, SocketChannel sc, String msg, String name) {
        String output = null;

        switch (type) {
            case MSG_OK:
                output = "OK\n";
                break;
            case MSG_ERROR:
                output = "ERROR\n";
                break;
            case MSG_PRIVATE:
                output = "PRIVATE" + " " + name + " " + msg + "\n";
                break;
            default:
                break;
        }
        ByteBuffer outputBuffer = ByteBuffer.wrap(output.getBytes());
        try {
            outputBuffer.rewind();
            sc.write(outputBuffer);
        } catch (IOException e) {}
    }

    private static void broadcast(MsgType type, SocketChannel sc, String msg) {
        String room = clients.get(sc).room;
        String output = null;
        List<SocketChannel> dest = null;

        switch (type) {
        
            case MSG_NEWNICK:
                // msg guarda o nick antigo
                output = "NEWNICK" + " " + msg + " " + clients.get(sc).name + "\n";
                dest = otherUsersInRoom(sc, room);
                break;
            
            case MSG_JOINED:
                output = "JOINED" + " " + clients.get(sc).name + "\n";
                dest = otherUsersInRoom(sc, room);
                break;

            case MSG_LEFT:
                output = "LEFT" + " " + clients.get(sc).name + "\n";
                // msg guarda o room antigo
                dest = otherUsersInRoom(sc, msg);
                break;
            
            case MSG_MESSAGE:
                // msg1 guarda a mensagem
                output = "MESSAGE" + " " + clients.get(sc).name + " " + msg + "\n";
                dest = allUsersInRoom(sc, room);
                break;

            default:
                break;
        }
        
        ByteBuffer outputBuffer = ByteBuffer.wrap(output.getBytes());
        for (SocketChannel c : dest) {
            try {
                outputBuffer.rewind();
                c.write(outputBuffer);
            } catch (IOException e) {}
        }   

    }
}
