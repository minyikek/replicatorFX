package com.replicatorfx.config;

import java.util.List;

public class SimulatorConfig {
    public AeronConfig    aeron  = new AeronConfig();
    public GlobalConfig   global = new GlobalConfig();
    public List<PairConfig> pairs;
}
