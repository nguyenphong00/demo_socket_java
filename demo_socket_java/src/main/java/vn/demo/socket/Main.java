package vn.demo.socket;

public final class Main {
    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "server" : args[0];
        if ("client".equals(mode)) {
            DemoClient.run(args);
            return;
        }
        if (!"server".equals(mode)) {
            throw new IllegalArgumentException("Cách dùng: java -jar app.jar [server|client]");
        }
        int port = intEnv("PORT", 9090);
        int maxClients = intEnv("MAX_CLIENTS", 32);
        String url = env("DB_URL", "jdbc:mysql://localhost:3306/socket_demo?useSSL=false&allowPublicKeyRetrieval=true");
        MysqlMeasurementRepository repository = new MysqlMeasurementRepository(
            url, env("DB_USER", "socket_user"), env("DB_PASSWORD", "socket_pass"));
        repository.initialize();
        TcpServer server = new TcpServer(port, maxClients, repository);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { server.close(); } catch (Exception ignored) { }
        }));
        try (server) { server.serve(); }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int intEnv(String name, int fallback) {
        return Integer.parseInt(env(name, Integer.toString(fallback)));
    }
}
