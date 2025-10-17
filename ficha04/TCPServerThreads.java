import java.io.*;
import java.net.*;

class TCPServerThreads {
  public static void main(String argv[]) throws Exception {

    ServerSocket welcomeSocket = new ServerSocket(6789);

    while(true) {

      Socket connectionSocket = welcomeSocket.accept();

      Helper h = new Helper(connectionSocket);
      new Thread(h).start();

      
    }
    
  }
}
  
class Helper implements Runnable {

  Socket connectionSocket;

  Helper(Socket connectionSocket) {
    this.connectionSocket = connectionSocket;
  }

  public void run() {

    String clientSentence;
    String capitalizedSentence = "";

      try {

        System.out.println("Entrou dentro do try do run()");

        BufferedReader inFromClient =
          new BufferedReader(new
            InputStreamReader(connectionSocket.getInputStream()));

        DataOutputStream outToClient =
          new DataOutputStream(connectionSocket.getOutputStream());

        clientSentence = inFromClient.readLine();

        while(clientSentence != null) {

          capitalizedSentence = clientSentence.toUpperCase() + '\n';
          outToClient.writeBytes(capitalizedSentence);
          clientSentence = inFromClient.readLine();
        }

        connectionSocket.close();
      }
      catch(Exception e) {
        System.out.println(e);

      }

  }
}