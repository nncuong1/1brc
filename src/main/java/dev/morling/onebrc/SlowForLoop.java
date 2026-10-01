package dev.morling.onebrc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;


public class SlowForLoop {
    // /dev/shm is a Linux tmpfs RAM disk — reads always come from RAM, never from SSD.
    // Copy measurements.txt there first: cp measurements.txt /dev/shm/measurements.txt
    public static final String RAM_DISK_FILE = "/dev/shm/measurements.txt";

    public static void main(String[] args) throws IOException {
        fileChannel();
    }

    // Reads via FileChannel + DirectByteBuffer — DMA-friendly, avoids heap copies.
    static void fileChannel() throws IOException {
        long start = System.nanoTime();
        long totalBytes = 0;
        try (FileChannel fc = FileChannel.open(Path.of(RAM_DISK_FILE), StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(4 * 1024 * 1024);
            long offset = 0;
            while (fc.read(buf) != -1) {
//            while (fc.read(buf, offset) != -1) {
//                buf.flip();

//                byte[] array = buf.array();
//                int lastNewLineIndex = findLastNewLine(array);
//
//                for (int i = 0; i < lastNewLineIndex;) {
//                    int semicolon = find(array, i, ';');
//                    int newline = find(array, semicolon + 1, '\n');
//
//                    i = newline + 1;
//                }

                totalBytes += buf.position();
                buf.clear();
                //offset += lastNewLineIndex;

            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("FileChannel+DirectByteBuffer: %d MB in %d ms (%.1f GB/s)%n",
                totalBytes / (1024 * 1024), elapsedMs, (totalBytes / 1e9) / (elapsedMs / 1000.0));
    }

    static int find(byte[] array, int i, char character) {
        for (int count = i; count <= array.length - 1; count++) {
            if (array[count] == character) {
                return count;
            }
        }
        throw new IllegalArgumentException("Did not find character " + character);
    }

    static int findLastNewLine(byte[] array) {
        for (int count = array.length - 1; count >= 0; count--) {
            if (array[count] == '\n') {
                return count;
            }
        }
        throw new IllegalArgumentException("did not find last new line");
    }
}