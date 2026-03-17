# Hotpath

A CLI tool that analyzes Java Flight Recorder (JFR) files and async-profiler collapsed stacks, converting them into a human-readable HTML report.

```bash
# JFR analysis
java -jar hotpath.jar recording.jfr

# Flame graph analysis (async-profiler collapsed stacks)
java -jar hotpath.jar profile.collapsed
```

Produces a single `report.html` file. [View sample report](https://yyubin.github.io/hotpath/sample-report.html)

---

## Download

Download `hotpath.jar` from [GitHub Releases](https://github.com/yyubin/hotpath/releases/latest), or grab it directly:

```bash
curl -L https://github.com/yyubin/hotpath/releases/latest/download/hotpath.jar -o hotpath.jar
java -jar hotpath.jar recording.jfr
```

Requires JDK 21+. No installation needed.

---

## Features

- **Native JFR parsing** — Uses the built-in `jdk.jfr.consumer` API with zero external dependencies
- **Flame graph analysis** — Analyzes async-profiler collapsed stacks with an interactive d3-flamegraph view
- **Single-pass streaming** — Handles recordings of hundreds of MB without memory pressure
- **Self-contained HTML output** — Works offline with no CDN dependencies
- **Auto input detection** — Detects JFR vs. collapsed stacks automatically from magic bytes, extension, or file structure
- **Fat JAR distribution** — Runs anywhere a JDK is installed

## What It Analyzes

### JFR Analysis

| Category | Metrics |
|----------|---------|
| **CPU** | JVM user/system CPU usage over time, Hot Methods Top 10 |
| **GC** | GC count, max/avg pause, total STW time, pause distribution histogram |
| **Memory** | Heap usage over time, total allocation, allocation rate per second, Top Allocators |
| **Threads** | Peak/avg thread count, lock contention total, longest monitor wait |
| **Findings** | Automatic anomaly detection (CRITICAL / WARNING / INFO) |

### Flame Graph Analysis

| Category | Metrics |
|----------|---------|
| **Flame Graph** | Interactive zoom/search view with self/total % on hover |
| **Hot Methods** | Self time Top 10, Total time Top 10 |
| **Package Breakdown** | CPU share by layer — Application / Framework / JDK / JVM Internal |
| **Findings** | CPU bottleneck detection, deep stack warning, recursion detection |

## Report Layout

### JFR Report
```
┌─ Summary      Finding cards by severity
├─ Findings     Issue list with cause and recommendation
├─ CPU          Load timeline chart + Hot Methods table
├─ GC           Pause timeline + distribution histogram + stats
├─ Memory       Heap usage chart + Top Allocators table
└─ Threads      Thread count stats + Lock Contention table
```

### Flame Graph Report
```
┌─ Summary          Finding cards by severity
├─ Findings         Issue list with cause and recommendation
├─ Overview         Total samples, max stack depth, unique frames
├─ Flame Graph      Interactive view — click to zoom, search to highlight
├─ Hot Methods      Self time Top 10 table
└─ Package          Layer breakdown pie chart + Total time bar chart
   Breakdown
```

---

## Requirements

- JDK 21+

## Usage

```bash
# JFR analysis — outputs report.html
java -jar hotpath.jar recording.jfr

# Flame graph analysis (async-profiler collapsed stacks)
java -jar hotpath.jar profile.collapsed
java -jar hotpath.jar profile.stacks

# Custom output path
java -jar hotpath.jar recording.jfr -o my-report.html

# Force input type (AUTO by default)
java -jar hotpath.jar profile.txt --type flamegraph

# Help
java -jar hotpath.jar --help
```

### Generating Collapsed Stacks with async-profiler

```bash
# Profile a running process and export collapsed stacks
./asprof -d 30 -o collapsed -f profile.collapsed <PID>

# Or via Java agent
java -agentpath:/path/to/libasyncProfiler.so=start,event=cpu,collapsed,file=profile.collapsed -jar app.jar
```

## Build from Source

```bash
git clone https://github.com/yyubin/hotpath.git
cd hotpath
./gradlew :hotpath-cli:shadowJar
```

Output: `hotpath-cli/build/libs/hotpath.jar` (~3 MB)

---

## JFR Recording

To capture all metrics, record with the `profile.jfc` configuration:

```bash
# Record on application startup
java -XX:StartFlightRecording=filename=recording.jfr,settings=profile,dumponexit=true \
     -jar app.jar

# Attach to a running process
jcmd <PID> JFR.start name=hotpath settings=profile filename=recording.jfr
jcmd <PID> JFR.dump name=hotpath filename=recording.jfr
```

For per-event details, overhead guidelines, and a custom JFC configuration, see [JFR-RECORDING-GUIDE.md](./JFR-RECORDING-GUIDE.md).

---

## Architecture

### Module Structure

```
hotpath/
├── hotpath-core/                  Analysis logic
│   └── src/main/java/.../
│       ├── model/                 Data models (records)
│       │   ├── AnalysisResult
│       │   ├── CpuSummary / GcSummary / MemorySummary / ThreadSummary
│       │   ├── Finding            (CRITICAL / WARNING / INFO)
│       │   ├── TimeBucket         1-second aggregation slot
│       │   └── flamegraph/
│       │       ├── FlameNode      Recursive call tree node
│       │       ├── FlameGraphResult
│       │       ├── FlameGraphSummary
│       │       ├── HotFrame / HotPath
│       │       └── FlameGraphMeta
│       ├── reader/                Parsing layer
│       │   ├── JfrReader          Single-pass JFR streaming
│       │   ├── EventRouter        Dispatches events by type
│       │   ├── TimelineBuilder    Aggregates into 1s buckets
│       │   ├── handler/
│       │   │   ├── CpuHandler     jdk.CPULoad, jdk.ExecutionSample
│       │   │   ├── GcHandler      jdk.GarbageCollection, jdk.GCHeapSummary
│       │   │   ├── MemoryHandler  jdk.ObjectAllocation*
│       │   │   ├── ThreadHandler  jdk.JavaMonitorEnter, jdk.JavaThreadStatistics
│       │   │   └── MetaHandler    jdk.JVMInformation
│       │   └── flamegraph/
│       │       ├── CollapsedStacksReader  Parses collapsed stacks format
│       │       └── FlameGraphBuilder      Builds FlameNode tree
│       ├── analyzer/              Anomaly detection + Finding generation
│       │   ├── CpuAnalyzer / GcAnalyzer / MemoryAnalyzer / ThreadAnalyzer
│       │   └── flamegraph/
│       │       └── FlameGraphAnalyzer     Hot frames, package breakdown, Findings
│       └── renderer/
│           ├── HtmlRenderer             AnalysisResult → report.html
│           └── FlameGraphHtmlRenderer   FlameGraphResult → flamegraph report
└── hotpath-cli/                   CLI entry point
    └── src/main/java/.../
        ├── HotpathCommand         Auto-detects input type, routes pipeline
        └── Main
```

### Pipelines

```
.jfr file                                    .collapsed / .stacks file
  │                                                │
  ▼  RecordingFile (single pass)                  ▼  line-by-line text parsing
[JfrReader + EventRouter]              [CollapsedStacksReader]
  │                                                │
  ├─► CpuHandler / GcHandler / ...               ▼  build recursive tree
  │                                    [FlameGraphBuilder]  →  FlameNode root
  ▼  1-second bucket aggregation                  │
[TimelineBuilder]                                 ▼  hot frames, package breakdown
  │                                    [FlameGraphAnalyzer]
  ▼  anomaly detection                            │
[Analyzers]                                       ▼
  │                                    [FlameGraphHtmlRenderer]
  ▼                                               │
[HtmlRenderer]                                    ▼
  │                                       flamegraph-report.html
  ▼
report.html
```

---

## Roadmap

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | JFR → HTML report (CPU, GC, Memory, Threads) | ✅ |
| 2 | async-profiler collapsed stacks → flame graph HTML report | ✅ |
| 3 | `gc.log` + Gatling `stats.json` integration + cross-source correlation | Planned |
| 4 | Before/after performance comparison (`before.jfr` vs `after.jfr`) | Planned |
| 5 | Gradle Plugin wrapper | Planned |

---

## Tech Stack

| Role | Technology |
|------|------------|
| JFR parsing | `jdk.jfr.consumer` (JDK built-in) |
| Collapsed stacks parsing | Pure Java (no external dependencies) |
| CLI | Picocli 4.7 |
| JSON | Jackson + jackson-datatype-jsr310 |
| Charts | Plotly.js (CDN) |
| Flame graph | d3-flamegraph (CDN) |
| Build | Gradle + Shadow Plugin |

---

## Contributing

Contributions of all kinds are welcome — bug reports, feature requests, ideas, and pull requests.

- **Bug / question** → [Open an issue](https://github.com/yyubin/hotpath/issues)
- **Feature suggestion** → Issues or Discussions, any format is fine
- **Pull request** → Fork → branch → PR, no strict process required

There are no contribution guidelines yet. Just open an issue or send a PR — it will be reviewed.

---

## License

[MIT](./LICENSE)

---

한국어 문서: [README.ko.md](./README.ko.md)
