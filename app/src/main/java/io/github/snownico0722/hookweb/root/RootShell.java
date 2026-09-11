package io.github.snownico0722.hookweb.root;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class RootShell {
    public static final class Result {
        public final int exitCode;
        public final String output;
        public final boolean timedOut;

        Result(int exitCode, String output, boolean timedOut) {
            this.exitCode = exitCode;
            this.output = output;
            this.timedOut = timedOut;
        }

        public boolean ok() {
            return !timedOut && exitCode == 0;
        }
    }

    private final Context context;
    private volatile Boolean rootAvailable;

    public RootShell(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean requestRoot() {
        Result result = exec("id", 15_000);
        boolean ok = result.ok() && result.output.contains("uid=0");
        rootAvailable = ok;
        return ok;
    }

    public boolean isRootAvailable() {
        Boolean value = rootAvailable;
        return value != null && value;
    }

    public Result exec(String command, long timeoutMs) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            Process running = process;
            AtomicReference<String> outputRef = new AtomicReference<>("");
            Thread reader = new Thread(() -> {
                try {
                    outputRef.set(readAll(running.getInputStream()));
                } catch (Throwable t) {
                    outputRef.set(t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }, "hookweb-su-reader");
            reader.start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.join(1_000);
                return new Result(-1, outputRef.get(), true);
            }
            reader.join(1_000);
            return new Result(process.exitValue(), outputRef.get(), false);
        } catch (Throwable t) {
            return new Result(-1, t.getClass().getSimpleName() + ": " + t.getMessage(), false);
        } finally {
            if (process != null) process.destroy();
        }
    }

    public File makeReadableCopy(String sourcePath) {
        if (!isRootAvailable()) return null;
        File dest = new File(context.getCacheDir(), "hookweb-apk-" + UUID.randomUUID() + ".apk");
        String command = "cp " + quote(sourcePath) + " " + quote(dest.getAbsolutePath())
                + " && chmod 0644 " + quote(dest.getAbsolutePath());
        Result result = exec(command, 45_000);
        if (!result.ok() || !dest.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            dest.delete();
            return null;
        }
        return dest;
    }

    public static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static String readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
