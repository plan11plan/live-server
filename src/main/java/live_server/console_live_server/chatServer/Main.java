package live_server.console_live_server.chatServer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class ClientHandler implements Runnable {
    private Socket clientSocket = null;
    private BufferedReader reader = null;
    private PrintWriter writer = null;

    private static final Set<ClientHandler> clients =
                                ConcurrentHashMap.newKeySet();

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        clients.add(this);
    }

    private void sendMessageAll(String msg) {
        for (ClientHandler client : clients) {
            client.writer.println(msg);
        }
    }

    @Override
    public void run() {
        try {
            InputStream inputStream = clientSocket.getInputStream();
            reader = new BufferedReader(new InputStreamReader(inputStream));
            OutputStream outputStream = clientSocket.getOutputStream();
            writer = new PrintWriter(outputStream, true);

            String msg;
            while ((msg = reader.readLine()) != null) {

                System.out.println(msg);
                //writer.println(msg);
                sendMessageAll(msg);
                if ("exit".equalsIgnoreCase(msg)) {
                    break;
                }
            }
        }
        catch (IOException ex) {
            System.out.println("Client exception: " + ex.getMessage());
        }
        finally {
            try {
                if (reader != null) {
                    reader.close();
                }
                if (writer != null) {
                    writer.close();
                }
                if (clientSocket != null) {
                    clientSocket.close();
                }

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

public class Main {
    public static void main(String[] args) {
        ServerSocket serverSocket = null;
        int listenPort = 20000;

        try {
            //1
            System.out.println("Server start...");
            serverSocket = new ServerSocket(listenPort);

            //2
            while (true) {
                //3
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected...");
                System.out.println("[" +
                        clientSocket.getInetAddress() + ":" +
                        clientSocket.getPort() + "]");

                ClientHandler handler = new ClientHandler(clientSocket);
                Thread thread = new Thread(handler);
                thread.start();
            }
        }
        catch (IOException ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }
        finally {
            //4
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
                System.out.println("Server stop...");
            } catch (IOException ex) {
                System.out.println(ex.getMessage());
            }
        }
    }
}
