package live_server;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.Console;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 원본 서버 코드(Main.java)와 동일한 구조의 WebSocket 서버.
 *
 * 원본:  serverSocket = new ServerSocket(listenPort);
 *        while (true) {
 *            Socket clientSocket = serverSocket.accept();
 *            new Thread(new ClientHandler(clientSocket)).start();
 *        }
 *
 * 차이점: 브라우저와 통신하려면 TCP 위에 WebSocket 프로토콜(핸드셰이크 + 프레임)을 구현해야 함.
 *        그 부분은 ChatClientHandler에서 처리.
 */
@Slf4j
@Component
public class ChatServer {

    private ServerSocket serverSocket;
    private Thread serverThread;
    private static final int LISTEN_PORT = 20000;

    // Spring Boot 시작 후, 자동으로 소켓 서버 실행
    @PostConstruct
    public void start() {
        serverThread = new Thread(() -> {
            try {
                // 1. ServerSocket 생성
                log.info("WebSocket Server start... port: " + LISTEN_PORT);
                serverSocket = new ServerSocket(LISTEN_PORT);

                // 2. 클라이언트 접속 대기 루프
                while (!Thread.currentThread().isInterrupted()) {
                    // 3. accept()로 클라이언트 연결 수락
                    Socket clientSocket = serverSocket.accept();
                    log.info("New client connected...");
                    log.info("[" +
                            clientSocket.getInetAddress() + ":" +
                            clientSocket.getPort() + "]");

                    // Handler 생성 후 Thread 시작
                    ChatClientHandler handler = new ChatClientHandler(clientSocket);
                    Thread thread = new Thread(handler);
                    thread.start();
                }
            } catch (IOException ex) {
                if (!Thread.currentThread().isInterrupted()) {
                    log.error(ex.getMessage());
                }
            }
        });
        //스프링 부트 애플리케이션을 중지시켰을 때, 채팅 서버용 스레드가 계속 살아있어서 프로세스가 죽지 않는 현상을 방지하기 위해서 true
        serverThread.setDaemon(true);
        serverThread.start();
    }

    // 4. 서버 종료
    @PreDestroy
    public void stop() {
        try {
            if (serverThread != null) serverThread.interrupt();
            if (serverSocket != null) serverSocket.close();
            log.info("WebSocket Server stop...");
        } catch (IOException ex) {
            log.error(ex.getMessage());
        }
    }
}
