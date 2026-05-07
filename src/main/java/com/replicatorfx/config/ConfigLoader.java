package com.replicatorfx.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {

    private ConfigLoader() {}

    public static SimulatorConfig load(Path configPath) throws IOException {
        var opts = new LoaderOptions();
        var yaml = new Yaml(new Constructor(SimulatorConfig.class, opts));
        try (InputStream is = Files.newInputStream(configPath)) {
            return yaml.load(is);
        }
    }
}
