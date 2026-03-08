package dev.morling.onebrc;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FastestReadDiskComparison {
    public static void main(String[] args) throws IOException {
        String file = "./measurements.txt";

        clearCache();
        bufferedInputStream(file); // Java 1.0 - 1966

        clearCache();
        fileChannel(file); // Java NIO 1.4 - 2002

        clearCache();
        mappedByteBuffer(file); // Java NIO 1.4 - 2002
    }

    static void bufferedInputStream(String file) throws IOException {
        long start = System.nanoTime();
        long totalBytes = 0;
        byte[] buf = new byte[4 * 1024 * 1024];
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file), 4 * 1024 * 1024)) {
            int n;
            while ((n = bis.read(buf)) != -1) {
                totalBytes += n;
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("BufferedInputStream:          %d MB in %d ms (%.1f GB/s)%n",
                totalBytes / (1024 * 1024), elapsedMs, (totalBytes / 1e9) / (elapsedMs / 1000.0));
    }

    static void fileChannel(String file) throws IOException {
        long start = System.nanoTime(); // nanoTime() for elapsed time: monotonic, unaffected by system clock changes
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
                totalBytes / (1024 * 1024), elapsedMs, (totalBytes / 1e9) / (elapsedMs / 1000.0));
    }

    static void mappedByteBuffer(String file) throws IOException {
        long start = System.nanoTime();
        long totalBytes = 0;
        // MappedByteBuffer.map() is limited to Integer.MAX_VALUE (2 GB) per mapping,
        // so we iterate in 2 GB chunks for large files.
        byte[] tmp = new byte[4 * 1024 * 1024];
        try (FileChannel fc = FileChannel.open(Path.of(file), StandardOpenOption.READ)) {
            long size = fc.size();
            long position = 0;
            while (position < size) {
                long mapSize = Math.min(size - position, Integer.MAX_VALUE);
                MappedByteBuffer mbb = fc.map(FileChannel.MapMode.READ_ONLY, position, mapSize);
                while (mbb.hasRemaining()) {
                    int n = Math.min(mbb.remaining(), tmp.length);
                    mbb.get(tmp, 0, n);
                    totalBytes += n;
                }
                position += mapSize;
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("MappedByteBuffer:             %d MB in %d ms (%.1f GB/s)%n",
                totalBytes / (1024 * 1024), elapsedMs, (totalBytes / 1e9) / (elapsedMs / 1000.0));
    }

    static void clearCache() throws IOException {
        // Drop all OS page cache, dentries, and inodes so the next read comes off the SSD.
        // Requires passwordless sudo for this command (or run the JVM as root).
        try {
            int exit = new ProcessBuilder("sudo", "sh", "-c", "sync; echo 3 > /proc/sys/vm/drop_caches")
                    .inheritIO()
                    .start()
                    .waitFor();
            if (exit != 0) {
                System.err.println("clearCache: drop_caches returned exit code " + exit + " (sudo permission missing?)");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
