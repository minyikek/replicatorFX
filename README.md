# replicatorFX

A low-latency FX rates simulator that publishes `OrderBookSnapshot` messages over [Aeron](https://github.com/real-logic/aeron) IPC using [SBE](https://github.com/real-logic/simple-binary-encoding) encoding. Rates follow Geometric Brownian Motion (GBM), are hot-reloadable via a watched config file, and the internal pipeline is built on [Conduit](https://github.com/green-leaves/conduit) (LMAX Disruptor-backed event processing).

## Features

- **GBM price model** — per-pair tunable drift (μ), volatility (σ), spread, and tick rate
- **Sub-millisecond tick rates** — `tickIntervalMs` is a `double`; set `0.5` for 2 ticks/ms per pair
- **SBE wire format** — `OrderBookSnapshot` messages encoded with zero-allocation SBE codecs
- **Aeron IPC transport** — all pairs multiplexed on a single stream
- **Conduit pipeline** — LMAX Disruptor ring buffer decouples tick generation from Aeron publishing; new processing stages (filters, spike injectors) slot in as additional nodes
- **Hot-reload config** — edit `config.yaml` while running to add, remove, or retune pairs instantly
- **Built-in subscriber** — `SubscriberMain` decodes and prints live rates for verification

## Requirements

- Java 21
- Gradle 9+
- [Aeron MediaDriver](https://github.com/real-logic/aeron) running as a standalone process

## Build

```bash
gradle build
```

SBE codec classes are generated automatically from `src/main/resources/sbe/fx-rates.xml` into `build/generated-sources/sbe/` before compilation.

## Running

Start the three components in separate terminals:

**1. Aeron MediaDriver** (must be started first)
```bash
gradle run -PmainClass=io.aeron.driver.MediaDriver
```

**2. Subscriber** (optional — prints decoded rates to stdout)
```bash
gradle run -PmainClass=com.replicatorfx.SubscriberMain
```

**3. Simulator**
```bash
gradle run
```

Pass a custom config path as the first argument:
```bash
gradle run --args="path/to/my-config.yaml"
```

## Configuration

`config.yaml` controls everything. The simulator watches this file and applies changes within ~50ms of a save — no restart needed.

```yaml
aeron:
  channel: "aeron:ipc"
  streamId: 1

global:
  fixSession:   "FX-SIM-SESSION"
  takerCompID:  "TAKER001"
  senderCompID: "SENDER001"
  sourceSystem: "REPLICATORFX"

pairs:
  - ccyPair:         "EUR/USD"   # 7-char currency pair (char[7])
    instrument:      "FXSPOT"    # up to 10 chars (char[10])
    lpName:          "SIMLP1"    # liquidity provider name
    initialMidPrice: 1.08500     # starting mid price
    volatility:      0.005       # annualised σ (e.g. 0.005 = 50 bps/year)
    drift:           0.0001      # annualised μ (directional bias)
    spreadPips:      2.0         # bid/ask spread in pips
    tickIntervalMs:  0.5         # 0.5ms = 2 ticks/ms; use 1.0 for 1ms, 100.0 for 100ms
    bidSize:         1000000.0   # notional size on the bid
    askSize:         1000000.0   # notional size on the ask
```

### Tick rate reference

| `tickIntervalMs` | Ticks/ms per pair | Ticks/sec (10 pairs) |
|---|---|---|
| `0.5` | 2 | 20,000 |
| `1.0` | 1 | 10,000 |
| `10.0` | 0.1 | 1,000 |
| `100.0` | 0.01 | 100 |

### Adding or removing pairs at runtime

Append a new entry to the `pairs` list and save the file — the pair starts publishing immediately. Remove an entry and save to stop it.

## Architecture

```
config.yaml
    │
    │  WatchService (config-watcher thread)
    │  tickerNode::onConfig
    ▼
GbmTickerNode  (rate-ticker thread)
  implements EventDispatcher<GbmTick>
  ├─ ConcurrentHashMap<ccyPair, PairState>
  ├─ AtomicReference<PairConfig> per pair  (hot-reload safe)
  └─ busy spin loop: GBM tick → dispatch(GbmTick)
         │
         │  Conduit EventDispatcher<GbmTick>
         ▼
  ┌─────────────────────────────────────┐
  │  LMAX Disruptor ring buffer         │  ← decouples generation
  │  2048 slots · BusySpinWaitStrategy  │    from Aeron back-pressure
  └─────────────────────────────────────┘
         │
         ▼
PublisherNode  (Disruptor consumer thread)
  extends DisruptorNode1<GbmTick>
  ├─ MessageEncoder  (SBE, pre-allocated DirectBuffer)
  └─ AeronPublisher.offer() → Aeron IPC stream 1
                                      │
                              SubscriberMain
                              (poll + decode + print)
```

### Threading model

| Thread | Role |
|---|---|
| `rate-ticker` | GBM spin loop; produces `GbmTick` events into the ring buffer |
| Disruptor consumer | Single thread; SBE encodes and calls `publication.offer()` |
| `config-watcher` | Blocks on `WatchService.take()`; calls `tickerNode::onConfig` on change |
| `shutdown-hook` | Sets `running = false`; closes publisher |

### Extending the pipeline

To add a processing stage (e.g. a rate spike injector or mid-price filter), create a new `DisruptorNode1<GbmTick>` that also implements `EventDispatcher<GbmTick>`, then insert it between the ticker and publisher in `Main`:

```java
SpikeInjectorNode spikeNode = new SpikeInjectorNode();
spikeNode.subscribe1(tickerNode);   // receives from ticker
spikeNode.start();

publisherNode.subscribe1(spikeNode); // forwards to publisher
publisherNode.start();
```

No changes to `GbmTickerNode` or `PublisherNode` are required.

## SBE Message Schema

**Message:** `OrderBookSnapshot` (template id=6)

| Field | Type | Notes |
|---|---|---|
| `seqId` | uint64 | `System.nanoTime()` — unique per message |
| `sendingTime` | uint64 | epoch nanos at publish |
| `processTime` | uint64 | epoch nanos at GBM generation |
| `instrument` | char[10] | from config, null-padded |
| `tenor` | char[10] | always `"SP"` (spot) |
| `forward` | BooleanType | always `FALSE` |
| `ccyPair` | char[7] | e.g. `"EUR/USD"` |
| `lastUpdateTimeStamp` | uint64 | epoch nanos |
| `isModifiedByInvoke` | BooleanType | always `FALSE` |
| `bids` group (1 entry) | — | GBM mid − spread/2 |
| `asks` group (1 entry) | — | GBM mid + spread/2 |
| `fixSession` | varString | from global config |
| `takerCompID` | varString | from global config |
| `senderCompID` | varString | from global config |
| `sourceSystem` | varString | from global config |
| `valueDate` | varString | T+2 business days (YYYYMMDD) |
| `traceId` | varString | UUID per message |

Each bid/ask group entry contains: `price`, `size`, `tradeable=TRUE`, `valueDate`, `lpName`, `entryId` (UUID).

## Price Model

Each tick applies the GBM formula:

```
S(t+dt) = S(t) × exp( (μ − σ²/2) × dt  +  σ × √dt × Z )
```

where `Z ~ N(0,1)` and `dt = tickIntervalMs / (365 × 24 × 3,600,000)` (time step in years).

**JPY pairs** use a pip size of 0.01; all other pairs use 0.00001.

## Dependencies

| Library | Purpose |
|---|---|
| [Aeron](https://github.com/real-logic/aeron) | IPC transport |
| [SBE](https://github.com/real-logic/simple-binary-encoding) | Binary message encoding |
| [Conduit](https://github.com/green-leaves/conduit) (vendored) | Disruptor-backed event pipeline |
| [LMAX Disruptor](https://github.com/LMAX-Exchange/disruptor) | Ring buffer (via Conduit) |
| [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml) | Config file parsing |
| [Agrona](https://github.com/real-logic/agrona) | Off-heap buffers (via Aeron) |

Conduit source is vendored under `io.lightning.conduit` — no external Maven dependency required.
