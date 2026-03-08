package dev.morling.onebrc;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FastestReadRamDiskComparison {

    // /dev/shm is a Linux tmpfs RAM disk — reads always come from RAM, never from SSD.
    // Copy measurements.txt there first: cp measurements.txt /dev/shm/measurements.txt
    static final String RAM_DISK_FILE = "/dev/shm/measurements.txt";

    public static void main(String[] args) throws IOException {
        nativeSolution();
        bufferInputStream();
        fileChannel();
        memoryMapped();
    }

    // Highest-level Java API: reads line-by-line via BufferedReader.
    // Useful as a "native" baseline — shows the cost of String allocation per line.
    static void nativeSolution() throws IOException {
        long start = System.nanoTime();
        long lines = 0;
        try (BufferedReader br = Files.newBufferedReader(Path.of(RAM_DISK_FILE))) {
            while (br.readLine() != null) {
                lines++;
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("BufferedReader (newBufferedReader): %d lines in %d ms%n", lines, elapsedMs);
    }

    // Reads raw bytes via BufferedInputStream — avoids String allocation, shows raw byte throughput.
    static void bufferInputStream() throws IOException {
        long start = System.nanoTime();
        long totalBytes = 0;
        byte[] buf = new byte[4 * 1024 * 1024];
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(RAM_DISK_FILE), 4 * 1024 * 1024)) {
            int n;
            while ((n = bis.read(buf)) != -1) {
                totalBytes += n;
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("BufferedInputStream:          %d MB in %d ms (%.1f GB/s)%n",
                totalBytes / (1024 * 1024), elapsedMs, (totalBytes / 1e9) / (elapsedMs / 1000.0));
    }

    // Reads via FileChannel + DirectByteBuffer — DMA-friendly, avoids heap copies.
    static void fileChannel() throws IOException {
        long start = System.nanoTime();
        long totalBytes = 0;
        try (FileChannel fc = FileChannel.open(Path.of(RAM_DISK_FILE), StandardOpenOption.READ)) {
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

    // Maps the file into virtual address space — on a RAM disk, this is essentially a zero-copy view of RAM.
    // MappedByteBuffer.map() is limited to Integer.MAX_VALUE (2 GB) per mapping,
    // so we iterate in chunks for the 13 GB file.
    static void memoryMapped() throws IOException {
        long start = System.nanoTime();
        long totalBytes = 0;
        byte[] tmp = new byte[4 * 1024 * 1024];
        try (FileChannel fc = FileChannel.open(Path.of(RAM_DISK_FILE), StandardOpenOption.READ)) {
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
}