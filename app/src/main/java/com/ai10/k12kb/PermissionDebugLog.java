package com.ai10.k12kb;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PermissionDebugLog {

    private static final String TAG = "K12KB_PermDebug";
    private static final String LOG_FILENAME = "permission_debug.log";
    private static StringBuilder toastBuffer = new StringBuilder();

    public static void logPermissionCheck(Context context, String section, String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        String line = timestamp + " [" + section + "] " + message;
        Log.d(TAG, line);
        appendToFile(context, line);
    }

    public static void logFilePermissionCheck(Context context) {
        toastBuffer.setLength(0);
        StringBuilder sb = new StringBuilder();
        sb.append("=== FILE PERMISSION CHECK ===\n");

        // Device & app info
        sb.append("  Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("  Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            sb.append("  App targetSdk: ").append(pi.applicationInfo.targetSdkVersion).append("\n");
            sb.append("  App versionName: ").append(pi.versionName).append("\n");
            toastBuffer.append("tgt=").append(pi.applicationInfo.targetSdkVersion);
        } catch (PackageManager.NameNotFoundException e) {
            sb.append("  App info: ERROR ").append(e.getMessage()).append("\n");
        }

        // Permission check via different contexts
        int resultAppCtx = ActivityCompat.checkSelfPermission(context.getApplicationContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        sb.append("  checkSelfPermission(appContext, WRITE_EXTERNAL_STORAGE) = ")
                .append(resultAppCtx == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED")
                .append(" (raw=").append(resultAppCtx).append(")\n");

        int resultCtx = context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        sb.append("  context.checkSelfPermission(WRITE_EXTERNAL_STORAGE) = ")
                .append(resultCtx == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED")
                .append(" (raw=").append(resultCtx).append(")\n");

        toastBuffer.append(" WRITE=").append(resultCtx == PackageManager.PERMISSION_GRANTED ? "G" : "D");

        // Check READ too
        int readResult = context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
        sb.append("  context.checkSelfPermission(READ_EXTERNAL_STORAGE) = ")
                .append(readResult == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED")
                .append(" (raw=").append(readResult).append(")\n");

        toastBuffer.append(" READ=").append(readResult == PackageManager.PERMISSION_GRANTED ? "G" : "D");

        // External storage state
        String state = Environment.getExternalStorageState();
        sb.append("  ExternalStorageState: ").append(state).append("\n");

        // PATH info
        String extPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        sb.append("  ExternalStorageDirectory: ").append(extPath).append("\n");

        String k12kbPath = extPath + "/K12Kb/";
        File k12kbDir = new File(k12kbPath);
        sb.append("  K12Kb dir exists: ").append(k12kbDir.exists()).append("\n");
        sb.append("  K12Kb dir canRead: ").append(k12kbDir.canRead()).append("\n");
        sb.append("  K12Kb dir canWrite: ").append(k12kbDir.canWrite()).append("\n");

        toastBuffer.append(" dir=").append(k12kbDir.exists() ? "Y" : "N");
        toastBuffer.append(" w=").append(k12kbDir.canWrite() ? "Y" : "N");

        // Try to check if mkdir works
        if (!k12kbDir.exists()) {
            boolean mkdirResult = k12kbDir.mkdirs();
            sb.append("  K12Kb mkdirs() result: ").append(mkdirResult).append("\n");
            sb.append("  K12Kb dir exists after mkdirs: ").append(k12kbDir.exists()).append("\n");
            toastBuffer.append(" mkdir=").append(mkdirResult ? "OK" : "FAIL");
        }

        // AnyJsonExists would be checked in the caller, just log the path
        sb.append("  FileJsonUtils.PATH: ").append(FileJsonUtils.PATH).append("\n");

        // Internal files dir (always accessible)
        sb.append("  context.getFilesDir: ").append(context.getFilesDir().getAbsolutePath()).append("\n");

        // External files dir (no permission needed)
        File extFilesDir = context.getExternalFilesDir(null);
        sb.append("  context.getExternalFilesDir: ").append(extFilesDir != null ? extFilesDir.getAbsolutePath() : "null").append("\n");

        // Check package installer
        String installer = context.getPackageManager().getInstallerPackageName(context.getPackageName());
        sb.append("  InstallerPackage: ").append(installer != null ? installer : "null (sideloaded)").append("\n");

        // allowBackup from manifest
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            boolean allowBackup = (pi.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP) != 0;
            sb.append("  allowBackup: ").append(allowBackup).append("\n");
        } catch (PackageManager.NameNotFoundException ignored) {}

        sb.append("=== END FILE PERMISSION CHECK ===");

        logPermissionCheck(context, "FILE_PERM", sb.toString());

        // Show toast with key info
        try {
            Toast.makeText(context, "PermDbg: " + toastBuffer.toString(), Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {}
    }

    public static void logCallPermissionCheck(Context context, boolean manageCallEnabled) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CALL PERMISSION CHECK ===\n");
        sb.append("  manageCall setting enabled: ").append(manageCallEnabled).append("\n");

        if (manageCallEnabled) {
            int callPhone = ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE);
            int answerCalls = ActivityCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS);
            int readPhone = ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE);

            sb.append("  CALL_PHONE: ")
                    .append(callPhone == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED")
                    .append(" (raw=").append(callPhone).append(")\n");
            sb.append("  ANSWER_PHONE_CALLS: ")
                    .append(answerCalls == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED")
                    .append(" (raw=").append(answerCalls).append(")\n");
            sb.append("  READ_PHONE_STATE: ")
                    .append(readPhone == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED")
                    .append(" (raw=").append(readPhone).append(")\n");

            boolean anyNeeded = (callPhone != PackageManager.PERMISSION_GRANTED)
                    || (answerCalls != PackageManager.PERMISSION_GRANTED)
                    || (readPhone != PackageManager.PERMISSION_GRANTED);
            sb.append("  Any permission needed: ").append(anyNeeded).append("\n");
        }

        sb.append("=== END CALL PERMISSION CHECK ===");

        logPermissionCheck(context, "CALL_PERM", sb.toString());
    }

    private static void appendToFile(Context context, String text) {
        // 1. Always write to internal storage (guaranteed to work)
        try {
            File internalDir = context.getFilesDir();
            File logFile = new File(internalDir, LOG_FILENAME);
            PrintWriter pw = new PrintWriter(new FileOutputStream(logFile, true));
            pw.println(text);
            pw.flush();
            pw.close();
        } catch (Exception e) {
            Log.w(TAG, "Failed to write to internal dir: " + e.getMessage());
        }

        // 2. Try K12Kb directory on external storage
        boolean wroteToK12Kb = false;
        try {
            String k12kbPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/K12Kb/";
            File dir = new File(k12kbPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File logFile = new File(dir, LOG_FILENAME);
            PrintWriter pw = new PrintWriter(new FileOutputStream(logFile, true));
            pw.println(text);
            pw.flush();
            pw.close();
            wroteToK12Kb = true;
        } catch (Exception e) {
            Log.w(TAG, "Failed to write to K12Kb dir: " + e.getMessage());
        }

        // 3. Also write to app's external files dir (no permission needed)
        try {
            File extFilesDir = context.getExternalFilesDir(null);
            if (extFilesDir != null) {
                File logFile = new File(extFilesDir, LOG_FILENAME);
                PrintWriter pw = new PrintWriter(new FileOutputStream(logFile, true));
                pw.println(text);
                if (!wroteToK12Kb) {
                    pw.println("  [NOTE: K12Kb dir write FAILED - permission likely denied]");
                }
                pw.flush();
                pw.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to write to externalFilesDir: " + e.getMessage());
        }
    }
}
