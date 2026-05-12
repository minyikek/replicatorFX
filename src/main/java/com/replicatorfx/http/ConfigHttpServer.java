package com.replicatorfx.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.replicatorfx.config.AeronConfig;
import com.replicatorfx.config.ConfigLoader;
import com.replicatorfx.config.GlobalConfig;
import com.replicatorfx.config.PairConfig;
import com.replicatorfx.config.SimulatorConfig;
import com.replicatorfx.model.GbmTick;
import com.replicatorfx.pipeline.GbmTickerNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class ConfigHttpServer implements Closeable {

    static final int PORT = 8080;

    private final Path configPath;
    private final HttpServer server;
    private final ObjectMapper jackson = new ObjectMapper();
    private final List<SseClient> sseClients = new CopyOnWriteArrayList<>();
    private final ArrayBlockingQueue<GbmTick> tickQueue = new ArrayBlockingQueue<>(4096);
    private final ReentrantLock writeLock = new ReentrantLock();
    private volatile boolean running = true;

    private record SseClient(OutputStream os, CountDownLatch latch) {}

    public ConfigHttpServer(Path configPath, GbmTickerNode tickerNode) throws IOException {
        this.configPath = configPath.toAbsolutePath();

        // More-specific contexts must be registered before the catch-all "/"
        server = HttpServer.create(new InetSocketAddress(PORT), 50);
        server.createContext("/events",    this::handleSSE);
        server.createContext("/api/pairs", this::handlePairs);
        server.createContext("/",          this::serveIndex);
        server.setExecutor(Executors.newCachedThreadPool());

        // Non-blocking offer — the hot loop is never delayed by the UI
        tickerNode.register(tick -> tickQueue.offer(tick));

        Thread bt = new Thread(this::broadcastLoop, "sse-broadcast");
        bt.setDaemon(true);
        bt.start();

        server.start();
        System.out.printf("[http] config UI → http://localhost:%d%n", PORT);
    }

    // ── SSE handler ───────────────────────────────────────────────────────────────
    // Holds a thread per connected client (fine for a handful of users).

    private void handleSSE(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type",  "text/event-stream");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.getResponseHeaders().set("Connection",    "keep-alive");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, 0); // 0 = chunked/unknown length

        OutputStream  os    = ex.getResponseBody();
        CountDownLatch latch = new CountDownLatch(1);
        SseClient client    = new SseClient(os, latch);
        sseClients.add(client);
        try {
            latch.await(); // block until disconnected or server closes
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sseClients.remove(client);
            try { ex.close(); } catch (Exception ignored) {}
        }
    }

    // ── Broadcast loop ────────────────────────────────────────────────────────────

    private void broadcastLoop() {
        while (running) {
            try {
                GbmTick tick = tickQueue.poll(200, TimeUnit.MILLISECONDS);
                if (tick != null) pushTick(tick);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void pushTick(GbmTick tick) {
        long   scale      = pow10(tick.decimalPlaces());
        double mid        = tick.mid() / (double) scale;
        double halfSpread = (tick.spreadPips() / 2.0) * tick.pipSize();
        String json = String.format(
            "{\"pair\":%s,\"mid\":%.6f,\"bid\":%.6f,\"ask\":%.6f,\"rateId\":%d,\"ts\":%d}",
            toJsonStr(tick.ccyPair()),
            mid, mid - halfSpread, mid + halfSpread,
            tick.rateId(),
            tick.timestampMs()
        );
        pushSSE("tick", json);
    }

    public void broadcastConfigChanged(String message) {
        pushSSE("cfg", "{\"msg\":" + toJsonStr(message) + "}");
    }

    private void pushSSE(String event, String data) {
        byte[] frame = ("event: " + event + "\ndata: " + data + "\n\n")
                .getBytes(StandardCharsets.UTF_8);
        List<SseClient> dead = new ArrayList<>();
        for (SseClient c : sseClients) {
            try {
                c.os().write(frame);
                c.os().flush();
            } catch (IOException e) {
                dead.add(c);
            }
        }
        // Release handler threads for clients that have disconnected
        for (SseClient c : dead) {
            sseClients.remove(c);
            c.latch().countDown();
        }
    }

    // ── REST: /api/pairs ──────────────────────────────────────────────────────────

    private void handlePairs(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }
        try {
            switch (ex.getRequestMethod()) {
                case "GET"    -> getAllPairs(ex);
                case "POST"   -> createPair(ex);
                case "PUT"    -> updatePair(ex);
                case "DELETE" -> deletePair(ex);
                default       -> sendErr(ex, 405, "Method not allowed");
            }
        } catch (Exception e) {
            sendErr(ex, 500, e.getMessage() != null ? e.getMessage() : "Internal error");
        }
    }

    private void getAllPairs(HttpExchange ex) throws IOException {
        SimulatorConfig cfg = readConfig();
        List<PairConfigDto> dtos = cfg.pairs.stream().map(PairConfigDto::from).toList();
        sendJson(ex, 200, jackson.writeValueAsString(dtos));
    }

    private void createPair(HttpExchange ex) throws IOException {
        PairConfigDto dto = jackson.readValue(ex.getRequestBody().readAllBytes(), PairConfigDto.class);
        writeLock.lock();
        try {
            SimulatorConfig cfg = readConfig();
            if (cfg.pairs.stream().anyMatch(p -> p.ccyPair.equals(dto.ccyPair))) {
                sendErr(ex, 409, "Pair already exists: " + dto.ccyPair);
                return;
            }
            cfg.pairs.add(dto.toPairConfig());
            writeConfig(cfg);
        } finally {
            writeLock.unlock();
        }
        broadcastConfigChanged("Pair added: " + dto.ccyPair);
        sendJson(ex, 201, "{\"status\":\"created\"}");
    }

    private void updatePair(HttpExchange ex) throws IOException {
        PairConfigDto dto = jackson.readValue(ex.getRequestBody().readAllBytes(), PairConfigDto.class);
        writeLock.lock();
        try {
            SimulatorConfig cfg = readConfig();
            int idx = -1;
            for (int i = 0; i < cfg.pairs.size(); i++) {
                if (cfg.pairs.get(i).ccyPair.equals(dto.ccyPair)) { idx = i; break; }
            }
            if (idx < 0) {
                sendErr(ex, 404, "Pair not found: " + dto.ccyPair);
                return;
            }
            cfg.pairs.set(idx, dto.toPairConfig());
            writeConfig(cfg);
        } finally {
            writeLock.unlock();
        }
        broadcastConfigChanged(dto.ccyPair + " updated");
        sendJson(ex, 200, "{\"status\":\"updated\"}");
    }

    private void deletePair(HttpExchange ex) throws IOException {
        String pair = queryParam(ex, "pair");
        if (pair == null) { sendErr(ex, 400, "Missing ?pair= query parameter"); return; }
        writeLock.lock();
        try {
            SimulatorConfig cfg = readConfig();
            if (!cfg.pairs.removeIf(p -> p.ccyPair.equals(pair))) {
                sendErr(ex, 404, "Pair not found: " + pair);
                return;
            }
            writeConfig(cfg);
        } finally {
            writeLock.unlock();
        }
        broadcastConfigChanged(pair + " removed");
        sendJson(ex, 200, "{\"status\":\"deleted\"}");
    }

    // ── Static file ───────────────────────────────────────────────────────────────

    private void serveIndex(HttpExchange ex) throws IOException {
        if (!"/".equals(ex.getRequestURI().getPath())) {
            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }
        try (InputStream is = getClass().getResourceAsStream("/index.html")) {
            if (is == null) { sendErr(ex, 500, "index.html not found in classpath"); return; }
            byte[] body = is.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        }
    }

    // ── Config I/O ────────────────────────────────────────────────────────────────

    private SimulatorConfig readConfig() throws IOException {
        return ConfigLoader.load(configPath);
    }

    private void writeConfig(SimulatorConfig cfg) throws IOException {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setIndent(2);
        Yaml yaml = new Yaml(opts);

        // Use plain Map to avoid SnakeYAML emitting Java class type tags
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> aeronMap = new LinkedHashMap<>();
        aeronMap.put("channel",  cfg.aeron.channel);
        aeronMap.put("streamId", cfg.aeron.streamId);
        root.put("aeron", aeronMap);

        Map<String, Object> globalMap = new LinkedHashMap<>();
        globalMap.put("fixSession",   cfg.global.fixSession);
        globalMap.put("takerCompID",  cfg.global.takerCompID);
        globalMap.put("senderCompID", cfg.global.senderCompID);
        globalMap.put("sourceSystem", cfg.global.sourceSystem);
        root.put("global", globalMap);

        List<Map<String, Object>> pairsList = new ArrayList<>();
        for (PairConfig pc : cfg.pairs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ccyPair",         pc.ccyPair);
            m.put("instrument",      pc.instrument);
            m.put("lpName",          pc.lpName);
            m.put("initialMidPrice", pc.initialMidPrice);
            m.put("decimalPlaces",   pc.decimalPlaces);
            m.put("volatility",      pc.volatility);
            m.put("drift",           pc.drift);
            m.put("spreadPips",      pc.spreadPips);
            m.put("pipSize",         pc.pipSize);
            m.put("tickIntervalMs",  pc.tickIntervalMs);
            m.put("bidSize",         pc.bidSize);
            m.put("askSize",         pc.askSize);
            pairsList.add(m);
        }
        root.put("pairs", pairsList);

        Path tmp = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        Files.writeString(tmp, yaml.dump(root));
        // Atomic rename: ConfigWatcher always sees a complete file
        Files.move(tmp, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private void sendErr(HttpExchange ex, int status, String msg) throws IOException {
        sendJson(ex, status, "{\"error\":" + toJsonStr(msg) + "}");
    }

    private String toJsonStr(String s) {
        try { return jackson.writeValueAsString(s); } catch (Exception e) { return "\"?\""; }
    }

    private static String queryParam(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) return null;
        for (String kv : q.split("&")) {
            String[] parts = kv.split("=", 2);
            if (parts.length == 2 && parts[0].equals(key))
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
        }
        return null;
    }

    private static long pow10(int n) {
        long r = 1L;
        for (int i = 0; i < n; i++) r *= 10;
        return r;
    }

    @Override
    public void close() {
        running = false;
        for (SseClient c : sseClients) c.latch().countDown(); // unblock all SSE threads
        server.stop(1);
    }
}
