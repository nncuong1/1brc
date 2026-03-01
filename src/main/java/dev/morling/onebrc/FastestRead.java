/*
 *  Copyright 2023 The original authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package dev.morling.onebrc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FastestRead {
    public static void main(String[] args) throws IOException {
        String file = "./measurements.txt";
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
}
