package vn.demo.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TcpServerTest {
    @Test void acceptsConcurrentClientsAndRejectsInvalidPayload() throws Exception {
        CopyOnWriteArrayList<Measurement> saved = new CopyOnWriteArrayList<>();
        AtomicLong ids = new AtomicLong();
        try (TcpServer server = new TcpServer(0, 8, measurement -> {
            saved.add(measurement);
            return ids.incrementAndGet();
        }); var clients = Executors.newFixedThreadPool(4)) {
            Thread serverThread = new Thread(() -> {
                try { server.serve(); } catch (Exception e) { throw new RuntimeException(e); }
            });
            serverThread.start();
            try {
                var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for (int i = 0; i < 4; i++) {
                    final int n = i;
                    tasks.add(clients.submit(() -> {
                        try (Socket socket = new Socket("127.0.0.1", server.port());
                             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                            socket.setSoTimeout(5000);
                            out.write("{\"clientId\":\"sensor-" + n + "\",\"metric\":\"temperature\",\"value\":21.5,\"timestamp\":\"" + Instant.now() + "\"}\n");
                            out.flush();
                            assertEquals("ok", new ObjectMapper().readTree(in.readLine()).path("status").asText());
                            out.write("{\"clientId\":\"sensor-" + n + "\",\"value\":\"bad\"}\n");
                            out.flush();
                            assertEquals("error", new ObjectMapper().readTree(in.readLine()).path("status").asText());
                        } catch (Exception e) { throw new RuntimeException(e); }
                    }));
                }
                for (var task : tasks) task.get();
                assertEquals(4, saved.size());
            } finally {
                server.close();
                serverThread.join(5000);
            }
        }
    }
}
