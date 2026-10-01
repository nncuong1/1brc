package dev.morling.onebrc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class EasySolution {
    // run with 115,913 ms
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
        Map<String, Measurement> measurementMaps = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(Constants.RAM_DISK_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] split = line.split(";");
                double temperature = Double.parseDouble(split[1]);
                measurementMaps.compute(split[0], (k, measurement) -> {
                    if (measurement == null) {
                        measurement = new Measurement(0.0, 0L);
                    }
                    measurement.min = Math.min(measurement.min, temperature);
                    measurement.max = Math.max(measurement.max, temperature);
                    measurement.count += 1;
                    measurement.sum += temperature;
                    return measurement;
                });
            }
        }
        Map<String, Measurement> sortedMeasurements = new TreeMap<>(measurementMaps);
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
