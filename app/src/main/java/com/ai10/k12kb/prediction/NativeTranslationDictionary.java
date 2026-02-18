package com.ai10.k12kb.prediction;

import android.content.Context;
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
    private static native void nativeClose(long ptr);
    private static native String[] nativeLookup(long ptr, String key);
    private static native String[] nativeTranslate(long ptr, String word, String previousWord);

    private long nativePtr = 0;
    private String sourceLang;
    private String targetLang;
    private boolean loaded = false;
    private boolean lastWasPhraseMatch = false;
    private int lastPhraseResultCount = 0;

    public NativeTranslationDictionary() {
    }

    /**
     * Load CDB dictionary. Copies from assets to filesDir on first use.
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

        // Try external file first
        File externalFile = new File("/sdcard/k12kb/dict/" + cdbName);
        if (externalFile.exists()) {
            nativePtr = nativeOpen(externalFile.getAbsolutePath());
            if (nativePtr != 0) {
                loaded = true;
                Log.i(TAG, "Loaded external CDB: " + externalFile);
                return;
            }
        }

        // Copy from assets to app files dir (CDB needs a real file path for mmap)
        File cacheDir = new File(context.getFilesDir(), "dict_cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        File cdbFile = new File(cacheDir, cdbName);

        if (!cdbFile.exists()) {
            try {
                InputStream is = context.getAssets().open("dict/" + cdbName);
                FileOutputStream fos = new FileOutputStream(cdbFile);
                byte[] buf = new byte[8192];
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
     * Translate a word with optional previous word context.
     * Returns list of translation strings (phrase results first).
     */
    public synchronized List<String> translate(String word, String previousWord) {
        List<String> result = new ArrayList<>();
        lastWasPhraseMatch = false;
        lastPhraseResultCount = 0;
        if (!loaded || nativePtr == 0 || word == null || word.isEmpty()) return result;

        String[] raw = nativeTranslate(nativePtr, word, previousWord);
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
