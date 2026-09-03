package com.ai10.k12kb.prediction;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

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

    /**
      * Guards nativePtr. A ReentrantLock rather than `synchronized` so lookups can
      * tryLock and bail out instead of parking the UI thread for the whole of a
      * dictionary build (extracting a TSV and building a CDB takes seconds).
      */
    private final ReentrantLock lock = new ReentrantLock();
    /** Смена направления, пришедшая во время загрузки: результат уже не нужен. */
    private volatile boolean stale = false;
    private long nativePtr = 0;
    private volatile String sourceLang;
    private volatile String targetLang;
    private volatile boolean loaded = false;
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
    public void load(Context context, String fromLang, String toLang) {
        lock.lock();
        try {
            stale = false;
            loadLocked(context, fromLang, toLang);
            if (stale) {
                // Пока грузили, направление успело смениться: показывать этот
                // словарь нельзя, он уже не тот, что просят.
                close();
            }
        } finally {
            lock.unlock();
        }
    }

    private void loadLocked(Context context, String fromLang, String toLang) {
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

        // 2. Готовый индекс важнее ограничения размера: он маппится прямо из APK
        //    (свой или языкового пакета), открывается мгновенно и не занимает
        //    кучу. Раньше при любом ненулевом лимите — а по умолчанию он 35000 —
        //    сюда не доходили вовсе: словарь каждый раз пересобирался из TSV, и
        //    готовый .cdb в пакете просто лежал без дела. Урезание осталось для
        //    случая, когда готового индекса нет и есть только TSV.
        AssetFileDescriptor afd = null;
        try {
            try {
                afd = context.getAssets().openFd("dict/" + cdbName);
            } catch (Exception e) {
                // Своего словаря нет — ищем в установленных пакетах языков.
                afd = LanguagePacks.openFd(context, "dict/" + cdbName);
                if (afd == null)
                    throw e;
            }
            nativePtr = nativeOpenFd(afd.getParcelFileDescriptor().getFd(),
                    afd.getStartOffset(), afd.getLength());
            if (nativePtr != 0) {
                loaded = true;
                Log.i(TAG, "Loaded CDB from APK (zero-copy): " + cdbName);
                return;
            }
        } catch (Exception e) {
            // openFd() throws if asset is compressed — fall through to copy
        } finally {
            // Дескриптор закрываем в любом случае: cdb_open_fd им не владеет,
            // а на пути ошибки он оставался открытым в чужой APK.
            LanguagePacks.Close(afd);
        }

        // 4. Fallback: копия .cdb рядом с кэшем, дальше mmap с неё.
        File cacheDir = new File(context.getFilesDir(), "dict_cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        // Версия пакета в имени: после обновления словаря старая копия
        // перестаёт подходить по имени, вместо того чтобы жить вечно.
        String assetPath = "dict/" + cdbName;
        int version = LanguagePacks.assetVersion(context, assetPath);
        String copyName = fromLang + "_" + toLang + "-v" + version + ".cdb";
        File cdbFile = new File(cacheDir, copyName);
        LanguagePacks.dropStaleCopies(cacheDir, fromLang + "_" + toLang + "-v", copyName);

        if (!cdbFile.exists() && !LanguagePacks.copyAsset(context, assetPath, cdbFile)) {
            // Готового .cdb нет нигде — собираем из TSV. Тут ограничение
            // размера и работает: при нулевом лимите словарь будет полным.
            Log.i(TAG, "Нет готового CDB для " + fromLang + " -> " + toLang
                    + ", собираем из TSV");
            loadTrimmed(context, fromLang, toLang);
            return;
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
    /**
     * Распаковать файл во временный: потоки закрываются на любом пути, иначе
     * сорвавшееся чтение оставляло открытым дескриптор в чужой APK.
     */
    private static boolean ExtractTo(Context context, String assetPath, File target) {
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            is = OpenAssetOrPack(context, assetPath);
            fos = new FileOutputStream(target);
            byte[] buf = new byte[262144];
            int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            fos = null;
            return true;
        } catch (Throwable ex) {
            return false;
        } finally {
            LanguagePacks.Close(is);
            LanguagePacks.Close(fos);
        }
    }

    /** Файл из assets клавиатуры, а если его там нет — из пакета языка. */
    private static InputStream OpenAssetOrPack(Context context, String assetPath) throws Exception {
        try {
            return context.getAssets().open(assetPath);
        } catch (Exception e) {
            InputStream is = LanguagePacks.open(context, assetPath);
            if (is == null)
                throw e;
            return is;
        }
    }

    private void loadTrimmed(Context context, String fromLang, String toLang) {
        File cacheDir = new File(context.getFilesDir(), "dict_cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();

        // Версия пакета в имени: пересобранный из обновлённого TSV словарь
        // не должен подменяться прежним кэшем.
        int tsvVersion = LanguagePacks.assetVersion(context,
                "dict/" + fromLang + "_" + toLang + ".tsv");
        String trimmedName = fromLang + "_" + toLang + "_trimmed_" + maxEntries
                + "-v" + tsvVersion + ".cdb";
        LanguagePacks.dropStaleCopies(cacheDir, fromLang + "_" + toLang + "_trimmed_",
                trimmedName);
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
        if (!ExtractTo(context, tsvName, tsvTemp)) {
            Log.w(TAG, "No TSV for " + fromLang + " -> " + toLang);
            tsvTemp.delete();
            return;
        }

        // Extract frequency dictionary for source language (for usage-based sorting)
        String freqName = "dictionaries/" + fromLang + "_base.txt";
        File freqTemp = new File(cacheDir, fromLang + "_freq.tmp");
        if (!ExtractTo(context, freqName, freqTemp)) {
            Log.w(TAG, "No freq dict for " + fromLang + ", will use file order");
            // freqTemp не появится — сборщик спокойно обходится без него
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
    public List<String> translate(String word, String previousWord) {
        // A load is in flight — skip this lookup rather than blocking the UI thread.
        // The onDictionaryLoaded callback repaints once it finishes.
        if (!lock.tryLock()) return new ArrayList<>();
        try {
            return translateLocked(word, previousWord);
        } finally {
            lock.unlock();
        }
    }

    private List<String> translateLocked(String word, String previousWord) {
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

    public List<String> translate(String word) {
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

    /**
     * Пометить словарь ненужным. Вызывается с главного потока службы ввода при
     * смене раскладки, поэтому ждать замок нельзя: загрузчик держит его всё
     * время сборки словаря, и переключение раскладки замораживало клавиатуру
     * на секунды. Если замок занят — оставляем метку, и загрузчик сам закроет
     * то, что успел собрать.
     */
    public void invalidate() {
        // Метку ставим до попытки взять замок: иначе загрузчик успевает
        // проверить её между неудачным tryLock и присваиванием и опубликует
        // словарь, который уже не нужен.
        stale = true;
        if (lock.tryLock()) {
            try {
                stale = false;
                close();
            } finally {
                lock.unlock();
            }
        }
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
