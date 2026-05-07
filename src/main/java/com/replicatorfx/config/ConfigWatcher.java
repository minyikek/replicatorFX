package com.replicatorfx.config;

import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;

public final class ConfigWatcher implements Runnable, AutoCloseable {

    private final Path configPath;
    private final Consumer<SimulatorConfig> onReload;
    private volatile boolean running = true;
    private final WatchService watchService;

    public ConfigWatcher(Path configPath, Consumer<SimulatorConfig> onReload) throws IOException {
        this.configPath   = configPath.toAbsolutePath();
        this.onReload     = onReload;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.configPath.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
    }

    @Override
    public void run() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ClosedWatchServiceException e) {
                return;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                @SuppressWarnings("unchecked")
                Path changed = ((WatchEvent<Path>) event).context();
                if (configPath.getFileName().equals(changed)) {
                    try {
                        Thread.sleep(50); // debounce: wait for the editor's write to complete
                        SimulatorConfig newConfig = ConfigLoader.load(configPath);
                        onReload.accept(newConfig);
                        System.out.println("[config] reloaded: " + configPath);
                    } catch (Exception e) {
                        System.err.println("[config] reload failed: " + e.getMessage());
                    }
                }
            }
            key.reset();
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        watchService.close();
    }
}
