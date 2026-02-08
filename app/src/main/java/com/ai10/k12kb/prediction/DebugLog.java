package com.ai10.k12kb.prediction;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Debug logger that writes to a file for diagnosing dictionary loading issues.
 * File: /data/data/com.ai10.k12kb/files/debug_log.txt
 */
public class DebugLog {

    private static final String TAG = "DebugLog";
    private static File logFile;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    public static void init(Context context) {
        logFile = new File(context.getFilesDir(), "debug_log.txt");
        w("=== DEBUG LOG STARTED === pid=" + android.os.Process.myPid()
                + " tid=" + Thread.currentThread().getId());
    }

    public static synchronized void w(String msg) {
        String line = sdf.format(new Date()) + " [" + Thread.currentThread().getName() + "] " + msg;
        Log.d(TAG, line);
        if (logFile == null) return;
        try {
            PrintWriter pw = new PrintWriter(new FileWriter(logFile, true));
            pw.println(line);
            pw.close();
        } catch (Exception ignored) {}
    }

    public static File getLogFile() {
        return logFile;
    }

    public static void clear() {
        if (logFile != null && logFile.exists()) {
            logFile.delete();
        }
    }
}
