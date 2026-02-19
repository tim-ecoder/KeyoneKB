package com.ai10.k12kb.prediction;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Native CDB-based translation dictionary. Uses mmap for instant loading
 * and O(1) key lookups via D.J. Bernstein's CDB format.
 *
 * Drop-in replacement for TranslationDictionary with same public API.
 */
public class NativeTranslationDictionary {
    private static final String TAG = "NativeTransDict";

    private static boolean libraryLoaded = false;
    static {
        try {
            System.loadLibrary("nativesymspell");
            libraryLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library: " + e);
        }
    }

    /* Native methods */
    private static native long nativeOpen(String path);
    private static native long nativeOpenFd(int fd, long offset, long length);
    private static native void nativeClose(long ptr);
    private static native String[] nativeLookup(long ptr, String key);
    private static native String[] nativeTranslate(long ptr, String word, String previousWord);
    private static native int nativeBuildCdbFromTsv(String tsvPath, String freqPath, String cdbPath, int maxEntries);

    private long nativePtr = 0;
    private String sourceLang;
    private String targetLang;
    private boolean loaded = false;
    private boolean lastWasPhraseMatch = false;
    private int lastPhraseResultCount = 0;
    private int maxEntries = 0; // 0 = full (use pre-built CDB)

    public NativeTranslationDictionary() {
    }

    public void setMaxEntries(int max) {
        this.maxEntries = Math.max(0, max);
    }

    /**
     * Delete trimmed CDB caches so they're rebuilt with new size.
     */
    public static void clearTrimmedCaches(Context context) {
        File cacheDir = new File(context.getFilesDir(), "dict_cache");
        if (!cacheDir.exists()) return;
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().contains("_trimmed_")) {
                f.delete();
            }
        }
    }

    /**
     * Load CDB dictionary. Tries in order:
     * 1. External file on sdcard (for user-provided dictionaries)
     * 2. If maxEntries > 0: trimmed CDB from cache (built from TSV)
     * 3. Direct mmap from APK asset (zero-copy, requires noCompress)
     * 4. Copy asset to files dir, then mmap (fallback for compressed assets)
     */
    public synchronized void load(Context context, String fromLang, String toLang) {
        close();
        this.sourceLang = fromLang;
        this.targetLang = toLang;
        loaded = false;

        if (!libraryLoaded) {
            Log.w(TAG, "Native library not loaded, cannot open CDB");
            return;
        }

        String cdbName = fromLang + "_" + toLang + ".cdb";

        // 1. Try external file first (always overrides)
        File externalFile = new File("/sdcard/k12kb/dict/" + cdbName);
        if (externalFile.exists()) {
            nativePtr = nativeOpen(externalFile.getAbsolutePath());
            if (nativePtr != 0) {
                loaded = true;
                Log.i(TAG, "Loaded external CDB: " + externalFile);
                return;
            }
        }

        // 2. If size-limited, build/use trimmed CDB from TSV
        if (maxEntries > 0) {
            loadTrimmed(context, fromLang, toLang);
            return;
        }

        // 3. Full mode: try direct mmap from APK asset (zero-copy, instant)
        try {
            AssetFileDescriptor afd = context.getAssets().openFd("dict/" + cdbName);
            nativePtr = nativeOpenFd(afd.getParcelFileDescriptor().getFd(),
                    afd.getStartOffset(), afd.getLength());
            afd.close();
            if (nativePtr != 0) {
                loaded = true;
                Log.i(TAG, "Loaded CDB from APK (zero-copy): " + cdbName);
                return;
            }
        } catch (Exception e) {
            // openFd() throws if asset is compressed — fall through to copy
        }

        // 4. Fallback: copy full CDB from assets to app files dir, then mmap
        File cacheDir = new File(context.getFilesDir(), "dict_cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        File cdbFile = new File(cacheDir, cdbName);

        if (!cdbFile.exists()) {
            try {
                InputStream is = context.getAssets().open("dict/" + cdbName);
                FileOutputStream fos = new FileOutputStream(cdbFile);
                byte[] buf = new byte[262144]; // 256KB buffer
                int n;
                while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
                fos.close();
                is.close();
                Log.i(TAG, "Copied CDB to: " + cdbFile + " (" + cdbFile.length() + " bytes)");
            } catch (Exception e) {
                Log.w(TAG, "No CDB found for " + fromLang + " -> " + toLang + ": " + e);
                return;
            }
        }

        nativePtr = nativeOpen(cdbFile.getAbsolutePath());
        if (nativePtr != 0) {
            loaded = true;
            Log.i(TAG, "Loaded CDB dict: " + cdbFile);
        } else {
            Log.w(TAG, "Failed to open CDB: " + cdbFile);
        }
    }

    /**
     * Load a trimmed CDB: check cache, or build from TSV asset sorted by word frequency.
     */
    private void loadTrimmed(Context context, String fromLang, String toLang) {
        File cacheDir = new File(context.getFilesDir(), "dict_cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();

        String trimmedName = fromLang + "_" + toLang + "_trimmed_" + maxEntries + ".cdb";
        File trimmedCdb = new File(cacheDir, trimmedName);

        // Try cached trimmed CDB
        if (trimmedCdb.exists()) {
            nativePtr = nativeOpen(trimmedCdb.getAbsolutePath());
            if (nativePtr != 0) {
                loaded = true;
                Log.i(TAG, "Loaded trimmed CDB from cache: " + trimmedCdb);
                return;
            }
            trimmedCdb.delete(); // stale cache
        }

        // Extract TSV to temp file
        String tsvName = "dict/" + fromLang + "_" + toLang + ".tsv";
        File tsvTemp = new File(cacheDir, fromLang + "_" + toLang + ".tsv.tmp");
        try {
            InputStream is = context.getAssets().open(tsvName);
            FileOutputStream fos = new FileOutputStream(tsvTemp);
            byte[] buf = new byte[262144];
            int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            is.close();
        } catch (Exception e) {
            Log.w(TAG, "No TSV for " + fromLang + " -> " + toLang + ": " + e);
            tsvTemp.delete();
            return;
        }

        // Extract frequency dictionary for source language (for usage-based sorting)
        String freqName = "dictionaries/" + fromLang + "_base.txt";
        File freqTemp = new File(cacheDir, fromLang + "_freq.tmp");
        try {
            InputStream is = context.getAssets().open(freqName);
            FileOutputStream fos = new FileOutputStream(freqTemp);
            byte[] buf = new byte[262144];
            int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            is.close();
        } catch (Exception e) {
            Log.w(TAG, "No freq dict for " + fromLang + ", will use file order: " + e);
            // freqTemp won't exist — native builder handles null gracefully
        }

        String freqPath = freqTemp.exists() ? freqTemp.getAbsolutePath() : null;
        int count = nativeBuildCdbFromTsv(tsvTemp.getAbsolutePath(), freqPath,
                trimmedCdb.getAbsolutePath(), maxEntries);
        tsvTemp.delete();
        freqTemp.delete();

        if (count > 0) {
            nativePtr = nativeOpen(trimmedCdb.getAbsolutePath());
            if (nativePtr != 0) {
                loaded = true;
                Log.i(TAG, "Built and loaded trimmed CDB: " + count + " entries (freq-sorted)");
                return;
            }
        }
        Log.w(TAG, "Failed to build trimmed CDB for " + fromLang + " -> " + toLang);
    }

    /**
     * Translate a word with optional previous word context.
     * Returns list of translation strings (phrase results first).
     */
    public synchronized List<String> translate(String word, String previousWord) {
        List<String> result = new ArrayList<>();
        lastWasPhraseMatch = false;
        lastPhraseResultCount = 0;
        if (!loaded || nativePtr == 0 || word == null || word.isEmpty()) return result;

        String[] raw = nativeTranslate(nativePtr,
                word.toLowerCase(), previousWord != null ? previousWord.toLowerCase() : null);
        if (raw == null || raw.length < 2) return result;

        // First element is phrase result count
        try {
            lastPhraseResultCount = Integer.parseInt(raw[0]);
            lastWasPhraseMatch = lastPhraseResultCount > 0;
        } catch (NumberFormatException e) {
            lastPhraseResultCount = 0;
        }

        for (int i = 1; i < raw.length; i++) {
            if (raw[i] != null && !raw[i].isEmpty()) {
                result.add(raw[i]);
            }
        }
        return result;
    }

    public synchronized List<String> translate(String word) {
        return translate(word, null);
    }

    public boolean wasLastPhraseMatch() {
        return lastWasPhraseMatch;
    }

    public int getLastPhraseResultCount() {
        return lastPhraseResultCount;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public int size() {
        return loaded ? 1 : 0; // CDB doesn't expose entry count; nonzero = loaded
    }

    public void invalidate() {
        close();
    }

    public String getSourceLang() {
        return sourceLang;
    }

    public String getTargetLang() {
        return targetLang;
    }

    private void close() {
        if (nativePtr != 0) {
            nativeClose(nativePtr);
            nativePtr = 0;
        }
        loaded = false;
    }
}
