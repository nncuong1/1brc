package dev.morling.onebrc;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class AvoidUtf8Parsing {
    // run with  92,895 ms
    class Constants {
        // /dev/shm is a Linux tmpfs RAM disk — reads always come from RAM, never from SSD.
        // Copy measurements.txt there first: cp measurements.txt /dev/shm/measurements.txt
        public static final String RAM_DISK_FILE = "/dev/shm/measurements.txt";

        public static double round(double value) {
            return Math.round(value * 10.0) / 10.0;
        }
    }

    static class Measurement {
        public double min = Double.POSITIVE_INFINITY;
        public double max = Double.NEGATIVE_INFINITY;
        public double sum;
        public long count;

        public Measurement(double sum, long count) {
            this.sum = sum;
            this.count = count;
        }

    }

    public static void main(String[] args) throws IOException {
        var start = System.nanoTime();
        // ByteBuffer equals/hashCode compare remaining bytes, so a wrapped slice can look up a stored key
        Map<ByteBuffer, Measurement> measurementMaps = new HashMap<>();
        try (InputStream in = new FileInputStream(Constants.RAM_DISK_FILE)) {
            byte[] buf = new byte[1 << 16];
            int filled = 0;
            int read;
            while ((read = in.read(buf, filled, buf.length - filled)) > 0) {
                filled += read;
                int lineStart = 0;
                for (int i = 0; i < filled; i++) {
                    if (buf[i] != '\n') {
                        continue;
                    }
                    int semicolon = i - 1;
                    while (buf[semicolon] != ';') {
                        semicolon--;
                    }
                    // ISO_8859_1 copies bytes without validation, the analogue of from_utf8_unchecked
                    double temperature = Double.parseDouble(new String(buf, semicolon + 1, i - semicolon - 1, StandardCharsets.ISO_8859_1));
                    ByteBuffer station = ByteBuffer.wrap(buf, lineStart, semicolon - lineStart);
                    Measurement measurement = measurementMaps.get(station);
                    if (measurement == null) {
                        measurement = new Measurement(0.0, 0L);
                        measurementMaps.put(ByteBuffer.wrap(Arrays.copyOfRange(buf, lineStart, semicolon)), measurement);
                    }
                    measurement.min = Math.min(measurement.min, temperature);
                    measurement.max = Math.max(measurement.max, temperature);
                    measurement.count += 1;
                    measurement.sum += temperature;
                    lineStart = i + 1;
                }
                System.arraycopy(buf, lineStart, buf, 0, filled - lineStart);
                filled -= lineStart;
            }
        }
        Map<String, Measurement> sortedMeasurements = new TreeMap<>();
        measurementMaps.forEach((station, measurement) -> sortedMeasurements.put(StandardCharsets.UTF_8.decode(station).toString(), measurement));
        System.out.print("{");
        Iterator<Map.Entry<String, Measurement>> it = sortedMeasurements.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Measurement> entry = it.next();
            System.out.print(entry.getKey() + "=" + Constants.round(entry.getValue().min) + "/" + Constants.round(entry.getValue().sum / entry.getValue().count) + "/" + Constants.round(entry.getValue().max));
            if (it.hasNext()) {
                System.out.print(", ");
            }
        }
        System.out.print("}");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.err.format("Took %,d ms\n", elapsedMs);
    }
}