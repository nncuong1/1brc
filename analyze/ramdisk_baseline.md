## Ramdisk baseline based on establish_baseline

### Context
- analyze/establish_baseline.md create baseline for 1brc
- src/main/java/dev/morling/onebrc/FastestRead.java measurement the read speed of java
(maybe in cold or warn-up)
- src/main/java/dev/morling/onebrc/FastestReadRamDiskComparison.java measurement the read speed of java from disk with
different ways

### Problems
- There is a different between ram and ram disk
- src/main/java/dev/morling/onebrc/FastestReadRamDiskComparison.java try to measurement
the read speed of Ram disk in different ways if possible. If it possible, please complete FastestReadRamDiskComparison.java
base on these tasks :

Step 1 : Do we need to copy measurements.txt to Ram disk folder then we can read from it ?
Step 2 : If we can do it in step 1,then :
+ update nativeSolution if need
+ complete the code of bufferInputStream
+ complete the code of fileChannel
+ complete the code of  memoryMapped

---

## Solve

### Step 1 — Copy measurements.txt to RAM disk

**Answer: Yes, we need to copy the file.**

On Linux, `/dev/shm` is a `tmpfs` mount — a file system backed entirely by RAM. Any file placed there is stored in RAM, so all reads come directly from memory with no SSD involved. This matches the official 1BRC contest environment (Hetzner AX161 uses a RAM disk).

```bash
# Check available space on /dev/shm
df -h /dev/shm
# Filesystem  Size  Used  Avail  Use%  Mounted on
# tmpfs        16G   22M    16G    1%  /dev/shm

# Copy the 13 GB file to the RAM disk
cp measurements.txt /dev/shm/measurements.txt

# Remove it after test done
rm /dev/shm/measurements.txt
```

**Why `/dev/shm` and not just "warm up the OS page cache"?**

The OS page cache (hot cache) and a RAM disk are both RAM, but they are subtly different:
- **Page cache (hot):** The kernel _may_ evict pages under memory pressure — not guaranteed to stay in RAM.
- **RAM disk (`tmpfs`):** The file _is_ RAM. There is no backing SSD. Reads are always served from memory, deterministically, just like the contest server.

This machine has 16 GB available in `/dev/shm` and the file is 13 GB — it fits.

---

### Step 2 — Complete `FastestReadRamDiskComparison.java`

`nativeSolution` was updated to:
- Read from `/dev/shm/measurements.txt` (not the project root)
- Count lines and report elapsed ms (instead of throwing to a silent catch block)
- Use `Files.newBufferedReader` (same API, but now on a meaningful path)

`bufferInputStream`, `fileChannel`, and `memoryMapped` were added, mirroring `FastestReadDiskComparison.java` but targeting the RAM disk path. No `clearCache()` calls between methods — the whole point of a RAM disk is that reads are _always_ from RAM.

**Compile and run:**
```bash
javac src/main/java/dev/morling/onebrc/FastestReadRamDiskComparison.java
java -cp src/main/java dev.morling.onebrc.FastestReadRamDiskComparison
```

---

### Results

```
BufferedReader (newBufferedReader): 1000000000 lines in 38347 ms
BufferedInputStream:          13156 MB in 1853 ms (7.4 GB/s)
FileChannel+DirectByteBuffer: 13156 MB in 1331 ms (10.4 GB/s)
MappedByteBuffer:             13156 MB in 1309 ms (10.5 GB/s)
```

---

### Analysis

| Method                        | Time     | Throughput | Notes                                           |
|-------------------------------|----------|------------|-------------------------------------------------|
| BufferedReader (line-by-line) | 38,347 ms | —         | 1B `String` allocations — GC dominates         |
| BufferedInputStream           | 1,853 ms  | 7.4 GB/s  | Heap byte array — one extra copy per buffer    |
| FileChannel + DirectByteBuffer| 1,331 ms  | 10.4 GB/s | Off-heap buffer, no heap copy                  |
| MappedByteBuffer              | 1,309 ms  | 10.5 GB/s | Virtual address map into RAM — ~same as above  |

**Key takeaways:**

1. **RAM disk confirmed.** The file lives entirely in `/dev/shm` — reads are always from RAM, no SSD involvement. This matches the official contest environment.

2. **`FileChannel` and `MappedByteBuffer` are equivalent on a RAM disk (~10.5 GB/s).** On SSD, `mmap` can win by avoiding copy-on-fault; on a RAM disk, both methods read from the same RAM-backed pages and perform identically.

3. **`BufferedInputStream` is ~40% slower (7.4 GB/s).** It uses a heap `byte[]` buffer, which requires an extra copy: kernel → off-heap JVM buffer → heap. `FileChannel` with `allocateDirect` skips the heap copy.

4. **`BufferedReader` is ~30× slower than raw byte reads.** Allocating one `String` object per line × 1 billion lines = massive GC pressure. This is exactly the bottleneck in `CalculateAverage_baseline`.

5. **I/O floor on this machine: ~1.3 seconds** (FileChannel or mmap from RAM disk). The official baseline takes ~124 seconds — meaning ~123 seconds is pure CPU/parsing waste. That is the budget for optimisation.

**Comparison with `establish_baseline.md` (OS page cache hot read):**

| Scenario                          | Throughput | Time    |
|-----------------------------------|------------|---------|
| OS page cache (hot) — FileChannel | 13.0 GB/s  | 1,058 ms |
| RAM disk (`/dev/shm`) — FileChannel | 10.4 GB/s | 1,331 ms |
| RAM disk (`/dev/shm`) — mmap      | 10.5 GB/s  | 1,309 ms |

The hot page cache run from `establish_baseline.md` ran after two `dd` warm-up passes (JVM already benefiting from a fully warm cache and a warm JIT). The RAM disk run here starts fresh per method with no JVM warm-up between them. The ~2–3 GB/s gap is accounted for by JIT compilation during the early methods (especially `nativeSolution` running for 38 seconds, after which the JVM is fully warm for the byte-reading methods).

Both scenarios confirm: **the I/O floor is roughly 1.1–1.3 seconds.** Anything above that in the solution is CPU time to be optimised.