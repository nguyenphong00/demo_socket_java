package vn.demo.socket;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DemoClient {
    private DemoClient() { }

    public static void run(String[] args) throws Exception {
        String host = args.length > 1 ? args[1] : "127.0.0.1";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 9090;
        int clients = args.length > 3 ? Integer.parseInt(args[3]) : 3;
        int messages = args.length > 4 ? Integer.parseInt(args[4]) : 5;
        if (clients < 1 || messages < 1) throw new IllegalArgumentException("clients và messages phải > 0");
        try (var pool = Executors.newFixedThreadPool(clients)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 1; i <= clients; i++) {
                final int clientNumber = i;
                tasks.add(pool.submit(() -> {
                    try { send(host, port, clientNumber, messages); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }));
            }
            pool.shutdown();
            if (!pool.awaitTermination((long) messages * 2 + 30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Demo client quá thời gian chờ");
            }
            for (var task : tasks) task.get();
        }
    }

    private static void send(String host, int port, int clientNumber, int messages) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            socket.setSoTimeout(10_000);
            for (int i = 1; i <= messages; i++) {
                writer.write(mapper.writeValueAsString(Map.of(
                    "clientId", "sensor-" + clientNumber,
                    "metric", "temperature",
                    "value", 20.0 + clientNumber + i * 0.1,
                    "timestamp", Instant.now().toString())));
                writer.newLine();
                writer.flush();
                String response = reader.readLine();
                if (response == null || !"ok".equals(mapper.readTree(response).path("status").asText())) {
                    throw new IllegalStateException("Server trả lỗi: " + response);
                }
                System.out.println("sensor-" + clientNumber + " -> " + response);
                Thread.sleep(1000);
            }
        }
    }
}
