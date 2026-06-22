package com.kanon.dingpunchguard;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class AppLog {
    static final String TAG = "CheckinReminder";

    private static final long MAX_LOG_BYTES = 128 * 1024L;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS");

    private AppLog() {
    }

    static void i(Context context, String message) {
        Log.i(TAG, message);
        write(context, "I", message, null);
    }

    static void e(Context context, String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
        write(context, "E", message, throwable);
    }

    static File file(Context context) {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        return new File(dir, "guard-events.log");
    }

    private static synchronized void write(Context context, String level, String message, Throwable throwable) {
        if (context == null) {
            return;
        }
        try {
            File file = file(context);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            if (file.exists() && file.length() > MAX_LOG_BYTES) {
                File old = new File(parent, "guard-events.old.log");
                if (old.exists()) {
                    old.delete();
                }
                file.renameTo(old);
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
                writer.print(LocalDateTime.now().format(FORMATTER));
                writer.print(' ');
                writer.print(level);
                writer.print(' ');
                writer.println(message);
                if (throwable != null) {
                    throwable.printStackTrace(writer);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
