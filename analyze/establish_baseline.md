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
# Step 1: Force a true cold read by dropping the OS page cache
# - 'sync' flushes any dirty write buffers to disk first (safe practice)
# - 'echo 3' drops all page cache, dentries, and inodes from RAM
#   so the next read is guaranteed to come off the SSD, not from RAM
sudo sh -c 'sync; echo 3 > /proc/sys/vm/drop_caches'

# Step 2: Read the full 13 GB file with direct I/O to measure raw SSD throughput
# - 'iflag=direct' uses O_DIRECT: data goes SSD → DMA buffer → /dev/null,
#   completely bypassing the OS page cache on the read path
#   This gives a clean, reproducible measurement of the actual hardware speed
# - 'status=progress' shows live throughput during the read
sudo dd if=measurements.txt of=/dev/null bs=4M iflag=direct status=progress
```

> **Why not just `dd if=measurements.txt of=/dev/null bs=4M`?**
> Without `drop_caches` + `iflag=direct`, the result depends on cache state:
> - If the file is already cached in RAM → measures **RAM speed** (~10–20 GB/s), not SSD speed
> - `iflag=direct` (O_DIRECT) forces every read to go through the storage controller, making results consistent and reproducible across runs

**Result:**
```
13795437122 bytes (14 GB, 13 GiB) copied, 2.88565 s, 4.8 GB/s
```

**Conclusion:** The NVMe SSD can deliver **~4.8 GB/s** sequential read. At that rate, reading the full 13 GB file takes roughly **2.9 seconds** off a cold (not OS-cached) disk.

---

### Step 3 — Check File System Cache Behaviour

**Context:** This laptop has 30 GB RAM. The measurements.txt file is 13 GB. After the first run, Linux will keep most or all of the file in the OS page cache (buff/cache), so repeated runs effectively read from RAM, not SSD.

```bash
free -h
# Before first run:
#   Mem: 30Gi total | 16Gi used | 13Gi buff/cache | 14Gi available
```

**Implication:** Unlike the official server (pure RAM disk, always hot), on our laptop:
- **First run** = cold SSD read (~2.9 s floor for I/O alone)
- **Subsequent runs** = hot OS cache (~RAM speeds, well above 4.8 GB/s)

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

**Command** (run from project root — the file has a `package dev.morling.onebrc` declaration):

To simulate the official contest's RAM disk (data always in RAM), **warm up the OS page cache first** before timing:

```bash
# Warm-up pass 1 — loads file into OS page cache
dd if=measurements.txt of=/dev/null bs=4M
# Warm-up pass 2 — confirms the file is fully cached (should be near-instant compared to pass 1)
dd if=measurements.txt of=/dev/null bs=4M

# Now time the Java read — data is served from RAM, not SSD
javac src/main/java/dev/morling/onebrc/FastestRead.java && \
java -cp src/main/java dev.morling.onebrc.FastestRead
```

> Without the warm-up, Java reads from the SSD at ~3.1 GB/s (4403 ms) — the OS cache is populated on the fly.
> With the warm-up, all 13 GB sit in the page cache and Java reads at **RAM speed**.

**Result (with warm-up / hot cache):**
```
FileChannel+DirectByteBuffer: 13156 MB in 1058 ms (13.0 GB/s)
```

**Conclusion:** When the file is in the OS page cache (matching the official RAM disk), Java FileChannel can consume the full 13 GB in **~1 second** at **~13 GB/s**. This is the true I/O lower bound for our optimisation target.

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

| Measurement                                   | Time    | Notes                                        |
|-----------------------------------------------|---------|----------------------------------------------|
| Raw SSD read (dd + O_DIRECT)                  | ~2.9 s  | OS-level ceiling, cold read                  |
| Java FileChannel read — cold/semi-warm        | ~4.4 s  | Page cache not fully populated               |
| Java FileChannel read — hot cache (warm-up×2) | ~1.1 s  | File fully in RAM, matches official RAM disk |
| Official baseline (CalculateAverage_baseline) | ~124 s  | Naive implementation                         |
| **Gap to close**                              | **~123 s** | ~113x slower than pure I/O (hot cache)   |

**Why is the baseline so slow?**
1. `Files.lines()` creates a `String` object per line — 1 billion allocations = massive GC pressure
2. `line.split(";")` creates a `String[]` + two more `String` objects per line
3. `Double.parseDouble()` is a heavy-weight parse of a heap-allocated String
4. Single-threaded — does not use the 14 cores available

**What the numbers tell us:**
- When data is cached (matching the official RAM disk), the Java I/O floor is **~1 second**. We cannot beat that without skipping byte reads entirely (e.g. `mmap`)
- Everything above ~1 second is CPU/parsing overhead — roughly **123 seconds** of pure compute waste in the baseline
- With 14 cores and smarter parsing, a well-optimised solution should target **2–4 seconds** total (I/O + compute in parallel)

---

### Summary

| Question                          | Answer                                          |
|-----------------------------------|-------------------------------------------------|
| SSD speed                         | ~4.8 GB/s sequential read                       |
| File size                         | 13 GB (measurements.txt)                        |
| I/O floor (dd + O_DIRECT)         | ~2.9 s cold                                     |
| Java I/O floor — semi-warm        | ~4.4 s (page cache partially populated)         |
| Java I/O floor — fully hot cache  | **~1.1 s at 13.0 GB/s** (matches RAM disk)     |
| File cached in RAM after 1st run? | Yes — 30 GB RAM, 13 GB file fits                |
| Official baseline time            | ~2 minutes 4 seconds                            |
| Theoretical best (I/O bound)      | ~1.1 s hot cache / ~2.9 s cold SSD             |
| Cores available                   | 14 cores / 18 threads                           |
| Key bottleneck in baseline        | GC pressure + single-threaded String parsing    |
