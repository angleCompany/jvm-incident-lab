package dev.jvmlab;

import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Main {
    public static void main(String[] args) throws Exception {
        String command = args.length == 0 ? "serve" : args[0];
        if (Set.of("--help", "-h", "help").contains(command)) {
            System.out.println("JVM Incident Lab\n  ./jvmlab serve [port] [output-directory]\n  ./jvmlab run <starved|fixed|compare> [output-directory]\nJava 21+; no external dependencies. Server binds to 127.0.0.1 only.");
            return;
        }
        if (command.equals("run")) {
            if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("Usage: ./jvmlab run <starved|fixed|compare> [output-directory]");
            Path output = Path.of(args.length == 3 ? args[2] : "artifacts");
            List<String> modes = args[1].equals("compare") ? List.of("starved", "fixed") : List.of(args[1]);
            boolean passed = true;
            for (String mode : modes) {
                Experiment.Result result = Experiment.run(mode, output);
                System.out.println(result.json());
                passed &= result.expected();
            }
            if (!passed) System.exit(1);
            return;
        }
        if (!command.equals("serve") || args.length > 3) throw new IllegalArgumentException("Use ./jvmlab --help");
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8787;
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535");
        Path output = Path.of(args.length > 2 ? args[2] : "artifacts");
        byte[] page;
        try (var resource = Main.class.getResourceAsStream("/index.html")) {
            if (resource == null) throw new IllegalStateException("Missing dashboard resource");
            page = resource.readAllBytes();
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 16);
        ExecutorService requests = Executors.newFixedThreadPool(4);
        server.setExecutor(requests);
        AtomicBoolean running = new AtomicBoolean();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") && exchange.getRequestMethod().equals("GET")) {
                send(exchange, 200, "text/html; charset=utf-8", page);
            } else if (path.equals("/api/health") && exchange.getRequestMethod().equals("GET")) {
                json(exchange, 200, "{\"status\":\"ok\"}");
            } else if (path.equals("/api/run")) {
                if (!exchange.getRequestMethod().equals("POST")) {
                    exchange.getResponseHeaders().set("Allow", "POST");
                    json(exchange, 405, "{\"error\":\"Use POST\"}"); return;
                }
                // Exact host and origin checks prevent DNS rebinding and cross-site triggers.
                String host = exchange.getRequestHeaders().getFirst("Host");
                String origin = exchange.getRequestHeaders().getFirst("Origin");
                Set<String> hosts = Set.of("127.0.0.1:" + port, "localhost:" + port);
                if (!hosts.contains(host == null ? "" : host) ||
                    (origin != null && !Set.of("http://127.0.0.1:" + port, "http://localhost:" + port).contains(origin))) {
                    json(exchange, 403, "{\"error\":\"Local origin required\"}"); return;
                }
                String query = exchange.getRequestURI().getRawQuery();
                String mode = "mode=starved".equals(query) ? "starved" : "mode=fixed".equals(query) ? "fixed" : null;
                if (mode == null) { json(exchange, 400, "{\"error\":\"Choose mode=starved or mode=fixed\"}"); return; }
                if (!running.compareAndSet(false, true)) { json(exchange, 409, "{\"error\":\"An experiment is already running\"}"); return; }
                try {
                    json(exchange, 200, Experiment.run(mode, output).json());
                } catch (Exception error) {
                    error.printStackTrace(System.err);
                    json(exchange, 500, "{\"error\":\"Experiment failed; inspect server stderr\"}");
                } finally { running.set(false); }
            } else { json(exchange, 404, "{\"error\":\"Not found\"}"); }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.stop(1); requests.shutdownNow(); }));
        server.start();
        System.out.println("JVM Incident Lab → http://127.0.0.1:" + port + "\nArtifacts → " + output.toAbsolutePath());
    }

    private static void json(HttpExchange exchange, int status, String body) throws java.io.IOException {
        send(exchange, status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws java.io.IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.sendResponseHeaders(status, body.length);
        try (var stream = exchange.getResponseBody()) { stream.write(body); }
        finally { exchange.close(); }
    }
}
