package vn.demo.socket;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class TcpServer implements AutoCloseable {
    private static final int MAX_LINE = 16_384;
    private final ServerSocket listener;
    private final ThreadPoolExecutor workers;
    private final MeasurementRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile boolean running = true;

    public TcpServer(int port, int maxClients, MeasurementRepository repository) throws IOException {
        if (maxClients < 1) throw new IllegalArgumentException("MAX_CLIENTS phải lớn hơn 0");
        this.listener = new ServerSocket(port);
        this.workers = new ThreadPoolExecutor(maxClients, maxClients, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(maxClients));
        this.repository = repository;
    }

    public int port() { return listener.getLocalPort(); }

    public void serve() throws IOException {
        System.out.println("TCP server listening on port " + port());
        while (running) {
            Socket socket;
            try {
                socket = listener.accept();
            } catch (SocketException e) {
                if (!running) break;
                throw e;
            }
            try {
                socket.setSoTimeout(120_000);
                workers.execute(() -> handle(socket));
            } catch (java.util.concurrent.RejectedExecutionException e) {
                try (socket) { writeResponse(socket, Map.of("status", "error", "message", "server busy")); }
                catch (IOException ignored) { }
            }
        }
    }

    private void handle(Socket socket) {
        try (socket;
             InputStreamReader reader = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            while (true) {
                String line;
                try { line = readLine(reader); }
                catch (IllegalArgumentException e) {
                    writeResponse(writer, Map.of("status", "error", "message", e.getMessage()));
                    break;
                }
                if (line == null) break;
                try {
                    Measurement measurement = Measurement.parse(line, mapper);
                    long id = repository.save(measurement);
                    writeResponse(writer, Map.of("status", "ok", "id", id));
                } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
                    writeResponse(writer, Map.of("status", "error", "message", e.getMessage()));
                } catch (Exception e) {
                    System.err.println("Database error: " + e.getMessage());
                    writeResponse(writer, Map.of("status", "error", "message", "database unavailable"));
                }
            }
        } catch (IOException e) {
            System.err.println("Client disconnected: " + e.getMessage());
        }
    }

    private static String readLine(InputStreamReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int ch;
        while ((ch = reader.read()) != -1) {
            if (ch == '\n') break;
            if (line.length() >= MAX_LINE) throw new IllegalArgumentException("JSON vượt quá 16384 ký tự");
            line.append((char) ch);
        }
        if (ch == -1 && line.isEmpty()) return null;
        if (ch == -1) throw new IllegalArgumentException("Thiếu ký tự xuống dòng sau JSON");
        if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') line.setLength(line.length() - 1);
        return line.toString();
    }

    private void writeResponse(Socket socket, Map<String, ?> response) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        writeResponse(writer, response);
    }

    private void writeResponse(BufferedWriter writer, Map<String, ?> response) throws IOException {
        writer.write(mapper.writeValueAsString(response));
        writer.newLine();
        writer.flush();
    }

    @Override public void close() throws IOException {
        running = false;
        listener.close();
        workers.shutdownNow();
    }
}
