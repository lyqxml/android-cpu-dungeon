package com.lyq.cpudungeon;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Last-resort diagnostics for device-only failures (no adb needed). */
final class CrashLog {
    private CrashLog() {
    }

    static void write(Context context, Throwable t) {
        try {
            File file = new File(context.getFilesDir(), "last-crash.txt");
            if (file.exists() && System.currentTimeMillis() - file.lastModified() < 4000L) {
                return;
            }
            StringWriter buffer = new StringWriter();
            PrintWriter writer = new PrintWriter(buffer);
            t.printStackTrace(writer);
            writer.flush();
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            byte[] bytes = (stamp + "\n" + buffer + "\n").getBytes("UTF-8");
            FileOutputStream out = new FileOutputStream(file);
            out.write(bytes);
            out.close();
        } catch (Throwable ignored) {
            // logging must never take down the renderer
        }
    }
}
