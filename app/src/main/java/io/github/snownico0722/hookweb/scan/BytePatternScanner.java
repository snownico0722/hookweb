package io.github.snownico0722.hookweb.scan;

import io.github.snownico0722.hookweb.model.EngineKind;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

final class BytePatternScanner {
    private static final int BUFFER_SIZE = 128 * 1024;
    private static final int OVERLAP = 256;

    private BytePatternScanner() {}

    static Map<EngineKind, String> scan(
            InputStream input,
            long maxBytes,
            BooleanSupplier cancelled
    ) throws IOException {
        EnumMap<EngineKind, String> found = new EnumMap<>(EngineKind.class);
        EnumSet<EngineKind> remaining = EnumSet.copyOf(EngineCatalog.DEX_PATTERNS.keySet());
        byte[] buffer = new byte[BUFFER_SIZE];
        byte[] overlap = new byte[OVERLAP];
        int overlapLength = 0;
        long total = 0;

        while (!remaining.isEmpty() && total < maxBytes) {
            checkCancelled(cancelled);
            int limit = (int) Math.min(buffer.length, maxBytes - total);
            int count = input.read(buffer, 0, limit);
            if (count < 0) break;
            total += count;

            byte[] combined = new byte[overlapLength + count];
            if (overlapLength > 0) System.arraycopy(overlap, 0, combined, 0, overlapLength);
            System.arraycopy(buffer, 0, combined, overlapLength, count);
            String chunk = new String(combined, StandardCharsets.ISO_8859_1);

            for (EngineKind engine : EnumSet.copyOf(remaining)) {
                for (String pattern : EngineCatalog.DEX_PATTERNS.get(engine)) {
                    if (chunk.contains(pattern)) {
                        found.put(engine, pattern);
                        remaining.remove(engine);
                        break;
                    }
                }
            }

            overlapLength = Math.min(OVERLAP, combined.length);
            System.arraycopy(combined, combined.length - overlapLength, overlap, 0, overlapLength);
        }
        return found;
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted()
                || (cancelled != null && cancelled.getAsBoolean())) {
            throw new CancellationException("scan cancelled");
        }
    }
}
