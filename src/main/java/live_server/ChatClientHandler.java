package live_server;

import java.io.*;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatClientHandler implements Runnable {
    private Socket clientSocket = null;
    private WebSocketConnection.Reader reader = null;
    private WebSocketConnection.Writer writer = null;

    private static final Set<ChatClientHandler> clients = ConcurrentHashMap.newKeySet();

    public ChatClientHandler(Socket socket) {
        this.clientSocket = socket;
        clients.add(this);
    }

    private void sendMessageAll(String msg) {
        for (ChatClientHandler client : clients) {
            client.writer.println(msg);
        }
    }

    @Override
    public void run() {
        try {
            // WebSocket 핸드셰이크 (브라우저 연결을 위해 필요)
            WebSocketConnection conn = new WebSocketConnection(clientSocket);
            conn.performHandshake();

            // 원본: reader = new BufferedReader(new InputStreamReader(inputStream));
            // 원본: writer = new PrintWriter(outputStream, true);
            reader = conn.getReader();
            writer = conn.getWriter();

            String msg;
            while ((msg = reader.readLine()) != null) {

                System.out.println(msg);
                sendMessageAll(msg);
                if ("exit".equalsIgnoreCase(msg))
                    break;
            }
        }
        catch (IOException ex) {
            System.out.println("Client exception: " + ex.getMessage());
        }
        finally {
            clients.remove(this);
            try {
                if (reader != null)         reader.close();
                if (writer != null)         writer.close();
                if (clientSocket != null)   clientSocket.close();

                System.out.println("Disconnected: " +
                        clientSocket.getInetAddress() + ":" +
                        clientSocket.getPort());
            }
            catch (IOException ex) {
                System.out.println("ERROR: " + ex.getMessage());
            }
        }
    }
}
