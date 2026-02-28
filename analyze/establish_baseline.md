## Tasks

### Task 1 — SSD Speed & Machine Specs
What is the speed of the SSD on the computer running the 1 Billion Row Challenge?

### Task 2 — File System Cache: Cold vs Hot Reads
Do we expect to read from the OS file system cache, or directly from disk?

When you read files, the OS caches those reads in memory opportunistically. The key question is:
- **Cold read** — data comes off the SSD with no prior caching
- **Hot read** — data is already in the OS page cache (effectively RAM speeds)

If the benchmark is run in a loop and the machine has enough RAM to hold the whole file, repeated runs will be served from cache.

> **From the official challenge specification:**
>
> *"Results are determined by running the program on a Hetzner AX161 dedicated server (32 core AMD EPYC™ 7502P (Zen2), 128 GB RAM). Programs are run from a **RAM disk** (i.e. the IO overhead for loading the file from disk is not relevant), using 8 cores of the machine. Each contender must pass the 1BRC test suite (`/test.sh`). The `hyperfine` program is used for measuring execution times of the launch scripts of all entries, i.e. end-to-end times are measured. Each contender is run five times in a row. The slowest and the fastest runs are discarded. The mean value of the remaining three runs is the result for that contender. The exact same `measurements.txt` file is used for evaluating all contenders."*

### Task 3 — Fastest Java File Read
What is the fastest way to read the file in Java?

Key questions to answer:
- What is the SSD throughput ceiling (e.g. if 13 GB takes 4 seconds, that is our hard floor)?
- Will Java's I/O APIs approach that ceiling, or leave headroom to exploit?
- If reading is already the bottleneck, we focus on parsing efficiency and cache-friendliness. If reading is fast, we may need to rethink the architecture (e.g. memory-mapped files, multiple reader threads).

## Solve

### Step 1 — Gather Machine Specs

**Command:**
```bash
lscpu | grep -E "Model name|CPU\(s\)|Thread|Core|MHz"
free -h
lsblk -d -o NAME,SIZE,ROTA,TYPE | grep -v loop
```

**Results on this laptop (Intel Core Ultra 5 125H):**

| Resource | Value |
|----------|-------|
| CPU      | Intel Core Ultra 5 125H |
| Cores    | 14 cores / 18 threads (with hyperthreading) |
| Max Clock| 4,500 MHz |
| RAM      | 30 GB total, ~14 GB available |
| Storage  | NVMe SSD — nvme0n1, ~954 GB |
| OS       | Ubuntu Linux 6.17.0 |
| Java     | OpenJDK 21 (Temurin) |

**Key difference from the official challenge server (Hetzner AX161):**
- Official: 32-core AMD EPYC 7502P, 128 GB RAM, RAM disk (I/O is irrelevant)
- Our machine: 14-core Intel, 30 GB RAM, NVMe SSD (I/O matters here)

---

### Step 2 — Measure the SSD Sequential Read Speed

**Command:**
```bash
# Read the full 13 GB measurements file into /dev/null to measure raw throughput
dd if=measurements.txt of=/dev/null bs=4M
```

**Result:**
```
13,795,437,122 bytes (13 GB) copied, 4.97 s, 2.8 GB/s
```

**Conclusion:** The NVMe SSD can deliver **~2.8 GB/s** sequential read. At that rate, reading the full 13 GB file takes roughly **5 seconds** off a cold (not OS-cached) disk.

---

### Step 3 — Check File System Cache Behaviour

**Context:** This laptop has 30 GB RAM. The measurements.txt file is 13 GB. After the first run, Linux will keep most or all of the file in the OS page cache (buff/cache), so repeated runs effectively read from RAM, not SSD.

```bash
free -h
# Before first run:
#   Mem: 30Gi total | 16Gi used | 13Gi buff/cache | 14Gi available
```

**Implication:** Unlike the official server (pure RAM disk, always hot), on our laptop:
- **First run** = cold SSD read (~5 s floor for I/O alone)
- **Subsequent runs** = hot OS cache (~RAM speeds, well above 2.8 GB/s)

When benchmarking locally, always do at least one warm-up run, then measure the warm run — this better simulates the official contest conditions (RAM disk).

---

### Step 4 — Measure the Fastest Java File Read

To know our I/O ceiling, benchmark the fastest way Java can consume the file (no parsing, just reading bytes).

**Test code — FileChannel with a 4 MB DirectByteBuffer:**
```java
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;

public class FastestRead {
    public static void main(String[] args) throws Exception {
        String file = "./measurements.txt";
        long start = System.nanoTime();  // nanoTime() for elapsed time: monotonic, unaffected by system clock changes
        long totalBytes = 0;
        try (FileChannel fc = FileChannel.open(Path.of(file), StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocateDirect(4 * 1024 * 1024);
            while (fc.read(buf) != -1) {
                totalBytes += buf.position();
                buf.clear();
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("FileChannel+DirectByteBuffer: %d MB in %d ms (%.1f GB/s)%n",
            totalBytes / (1024*1024), elapsedMs, (totalBytes / 1e9) / (elapsedMs / 1000.0));
    }
}
```

> **Why `System.nanoTime()` and not `System.currentTimeMillis()`?**
> - `currentTimeMillis()` reads wall-clock time, which can jump backward or skip forward due to NTP sync or system clock adjustments — making elapsed-time measurements unreliable.
> - `nanoTime()` uses a **monotonic clock** that only moves forward, guaranteed to give accurate elapsed time. It is the correct choice for any benchmark or duration measurement.

**Command:**
```bash
javac FastestRead.java && java FastestRead
```

**Result:**
```
FileChannel+DirectByteBuffer: 13156 MB in 4403 ms (3.1 GB/s)
```

**Conclusion:** Java FileChannel with a direct buffer can read the 13 GB file in **~4.4 seconds** at **3.1 GB/s**. This is the I/O lower bound — no solution can be faster than this for I/O-bound work on this machine.

---

### Step 5 — Run the Official Baseline

The project includes an official naive baseline implementation: `CalculateAverage_baseline.java`. It uses `Files.lines()` with string splitting and a stream collector — the simplest possible approach.

**Command:**
```bash
time ./calculate_average_baseline.sh
```

**Result:**
```
real    2m 3.842s
user    2m 0.684s
sys     0m 4.749s
```

**Baseline time: ~2 minutes 4 seconds**

---

### Step 6 — Analyse the Gap

| Measurement                        | Time      | Notes                              |
|------------------------------------|-----------|------------------------------------|
| Raw SSD read (dd)                  | ~5.0 s    | OS-level ceiling, cold read        |
| Java FileChannel read (no parsing) | ~4.4 s    | Java I/O ceiling, warm (cached)    |
| Official baseline (CalculateAverage_baseline) | ~124 s | Naive implementation |
| **Gap to close**                   | **~120 s**| 28x slower than pure I/O           |

**Why is the baseline so slow?**
1. `Files.lines()` creates a `String` object per line — 1 billion allocations = massive GC pressure
2. `line.split(";")` creates a `String[]` + two more `String` objects per line
3. `Double.parseDouble()` is a heavy-weight parse of a heap-allocated String
4. Single-threaded — does not use the 14 cores available

**What the numbers tell us:**
- Our I/O floor is ~4-5 seconds (either warm or cold). We cannot beat that without algorithmic changes (e.g. skipping reads with memory-mapped files + multiple threads)
- Everything above 5 seconds is CPU/parsing overhead
- With 14 cores and smarter parsing, a well-optimised solution should target **under 5 seconds** total

---

### Summary

| Question | Answer |
|----------|--------|
| SSD speed | ~2.8 GB/s sequential read |
| File size | 13 GB (measurements.txt) |
| I/O floor (dd) | ~5 seconds cold |
| Java I/O floor (FileChannel) | ~4.4 seconds warm |
| File cached in RAM after 1st run? | Yes — 30 GB RAM, 13 GB file fits |
| Official baseline time | ~2 minutes 4 seconds |
| Theoretical best (I/O bound) | ~4-5 seconds |
| Cores available | 14 cores / 18 threads |
| Key bottleneck in baseline | GC pressure + single-threaded String parsing |
