package live_server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * WebSocket 프로토콜 처리 담당.
 * 원본 코드의 BufferedReader/PrintWriter 역할을 대체한다.
 *
 * 사용법:
 *   WebSocketConnection conn = new WebSocketConnection(socket);
 *   conn.performHandshake();          // 최초 1회
 *   String msg = conn.readMessage();  // reader.readLine() 대체
 *   conn.sendMessage(msg);            // writer.println() 대체
 */
public class WebSocketConnection {

    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    private static final String WEBSOCKET_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public WebSocketConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
    }

    /**
     * WebSocket 핸드셰이크 수행.
     * 브라우저의 HTTP Upgrade 요청을 받아 101 응답을 보낸다.
     */
    public boolean performHandshake() throws IOException {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        int b;
        while ((b = inputStream.read()) != -1) {
            headerBytes.write(b);
            byte[] buf = headerBytes.toByteArray();
            if (buf.length >= 4 &&
                    buf[buf.length - 4] == '\r' && buf[buf.length - 3] == '\n' &&
                    buf[buf.length - 2] == '\r' && buf[buf.length - 1] == '\n') {
                break;
            }
        }

        String headers = headerBytes.toString(StandardCharsets.UTF_8);

        String webSocketKey = null;
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                webSocketKey = line.substring("sec-websocket-key:".length()).trim();
            }
        }

        if (webSocketKey == null) return false;

        try {
            String acceptKey = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1")
                            .digest((webSocketKey + WEBSOCKET_MAGIC)
                                    .getBytes(StandardCharsets.UTF_8))
            );

            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + acceptKey + "\r\n" +
                    "\r\n";

            outputStream.write(response.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            return true;
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    /**
     * WebSocket 프레임을 읽어서 텍스트 메시지를 반환한다.
     * 원본의 reader.readLine()을 대체.
     */
    public String readMessage() throws IOException {
        int firstByte = inputStream.read();
        if (firstByte == -1) return null;

        int opcode = firstByte & 0x0F;

        if (opcode == 0x8) return null; // 종료 프레임

        if (opcode == 0x9) { // 핑 → 퐁 응답
            int secondByte = inputStream.read();
            boolean masked = (secondByte & 0x80) != 0;
            int len = secondByte & 0x7F;
            byte[] maskKey = new byte[4];
            if (masked) inputStream.readNBytes(maskKey, 0, 4);
            byte[] payload = inputStream.readNBytes(len);
            if (masked) {
                for (int i = 0; i < payload.length; i++) payload[i] ^= maskKey[i % 4];
            }
            sendPong(payload);
            return readMessage();
        }

        int secondByte = inputStream.read();
        if (secondByte == -1) return null;

        boolean masked = (secondByte & 0x80) != 0;
        int payloadLength = secondByte & 0x7F;

        if (payloadLength == 126) {
            payloadLength = (inputStream.read() << 8) | inputStream.read();
        } else if (payloadLength == 127) {
            long longLength = 0;
            for (int i = 0; i < 8; i++) {
                longLength = (longLength << 8) | inputStream.read();
            }
            payloadLength = (int) longLength;
        }

        byte[] maskKey = new byte[4];
        if (masked) {
            inputStream.readNBytes(maskKey, 0, 4);
        }

        byte[] payload = inputStream.readNBytes(payloadLength);

        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= maskKey[i % 4];
            }
        }

        return new String(payload, StandardCharsets.UTF_8);
    }

    /**
     * 텍스트 메시지를 WebSocket 프레임으로 감싸서 전송한다.
     * 원본의 writer.println()을 대체.
     */
    public void sendMessage(String message) throws IOException {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream frame = new ByteArrayOutputStream();

        frame.write(0x81);

        if (payload.length < 126) {
            frame.write(payload.length);
        } else if (payload.length < 65536) {
            frame.write(126);
            frame.write((payload.length >> 8) & 0xFF);
            frame.write(payload.length & 0xFF);
        } else {
            frame.write(127);
            for (int i = 7; i >= 0; i--) {
                frame.write((int) ((payload.length >> (8 * i)) & 0xFF));
            }
        }

        frame.write(payload);

        synchronized (outputStream) {
            outputStream.write(frame.toByteArray());
            outputStream.flush();
        }
    }

    private void sendPong(byte[] payload) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x8A);
        frame.write(payload.length);
        frame.write(payload);
        synchronized (outputStream) {
            outputStream.write(frame.toByteArray());
            outputStream.flush();
        }
    }

    public void close() throws IOException {
        socket.close();
    }

    // ========================================================
    //  원본 코드의 BufferedReader / PrintWriter 역할을 하는 래퍼
    //  reader.readLine(), writer.println() 을 그대로 쓸 수 있게 해줌
    // ========================================================

    public Reader getReader() { return new Reader(this); }
    public Writer getWriter() { return new Writer(this); }

    public static class Reader {
        private final WebSocketConnection conn;
        Reader(WebSocketConnection conn) { this.conn = conn; }

        /** BufferedReader.readLine() 과 동일한 역할 */
        public String readLine() throws IOException {
            return conn.readMessage();
        }

        public void close() throws IOException { }
    }

    public static class Writer {
        private final WebSocketConnection conn;
        Writer(WebSocketConnection conn) { this.conn = conn; }

        /** PrintWriter.println() 과 동일한 역할 */
        public void println(String msg) {
            try {
                conn.sendMessage(msg);
            } catch (IOException e) {
            }
        }

        public void close() throws IOException { }
    }
}
