import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ChatServer {
    private static int nextClientId = 1;
    private static Map<SocketChannel, ClientInfo> clients = new HashMap<>();

    private static class ClientInfo {
        int id;
        String name;
        String room;

        ClientInfo() {
            this.id = nextClientId++;
            this.name = "Client_" + this.id;
            this.room = "outside";
        };
    }

    // A pre-allocated buffer for the received data
    private static final ByteBuffer buffer = ByteBuffer.allocate(16384);

    // Decoder for incoming text -- assume UTF-8
    private static final Charset charset = Charset.forName("UTF8");
    private static final CharsetDecoder decoder = charset.newDecoder();

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
    private static boolean processInput(
        SocketChannel sc,
        Set<SelectionKey> keys
    ) throws IOException {
        // Read the message to the buffer
        buffer.clear();
        int bytesRead = sc.read(buffer);

        // If no data or connection closed, close the connection
        if (bytesRead == -1) {
            return false;
        }

        // Prepare buffer for writing
        buffer.flip();
        String message = decoder.decode(buffer).toString();
        ClientInfo client = clients.get(sc);

        String formattedMsg = client.name + ": " + message + "\n";
        broadcast(sc, formattedMsg);

        // Simple echo back to the same client
        sc.write(buffer);

        return true;
    }

    private static void broadcast(SocketChannel sender, String message) {
        ByteBuffer msgBuffer = ByteBuffer.wrap(message.getBytes());
        String senderRoom = clients.get(sender).room;

        for (Map.Entry<SocketChannel, ClientInfo> c : clients.entrySet()) {
            if (c.getValue().room == senderRoom) {
                try {
                    msgBuffer.rewind();
                    sender.write(msgBuffer);
                } catch (IOException e) {
                    System.out.println("Client " + c.getValue().name + " disconnected");
                }
            }
        }
    }
}
