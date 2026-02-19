package com.ai10.k12kb.prediction;

import android.util.Log;

/**
 * JNI bridge to native SymSpell C library (libnativesymspell.so).
 *
 * Provides fast fuzzy string matching with keyboard-weighted distance.
 * All heavy computation (delete index, lookup, distance calc) runs in native code.
 *
 * Usage:
 *   NativeSymSpell ns = new NativeSymSpell(2, 7);
 *   ns.addWord("hello", "Hello", 100);
 *   ns.buildIndex();
 *   NativeSymSpell.SuggestItem[] results = ns.lookup("helo", 5);
 *   ns.destroy();
 */
public class NativeSymSpell {

    private static final String TAG = "NativeSymSpell";
    private static boolean libraryLoaded = false;
    private static boolean libraryFailed = false;

    static {
        try {
            System.loadLibrary("nativesymspell");
            libraryLoaded = true;
            Log.i(TAG, "Native SymSpell library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            libraryFailed = true;
            Log.w(TAG, "Native SymSpell library not available: " + e.getMessage());
        }
    }

    public static boolean isAvailable() {
        return libraryLoaded && !libraryFailed;
    }

    public static class SuggestItem {
        public final String term;
        public final String original;
        public final int distance;
        public final int frequency;
        public final float weightedDistance;

        public SuggestItem(String term, String original, int distance, int frequency, float weightedDistance) {
            this.term = term;
            this.original = original;
            this.distance = distance;
            this.frequency = frequency;
            this.weightedDistance = weightedDistance;
        }
    }

    public static class BigramItem {
        public final String word;
        public final int frequency;

        public BigramItem(String word, int frequency) {
            this.word = word;
            this.frequency = frequency;
        }
    }

    private long nativePtr;

    /**
     * Create a new native SymSpell instance.
     */
    public NativeSymSpell(int maxEditDistance, int prefixLength) {
        if (!libraryLoaded) return;
        nativePtr = nativeCreate(maxEditDistance, prefixLength);
    }

    /**
     * Private constructor for mmap-loaded instances.
     */
    private NativeSymSpell(long ptr) {
        this.nativePtr = ptr;
    }

    /**
     * Load from binary cache file (fast — uses mmap).
     * Returns null if loading fails.
     */
    public static NativeSymSpell loadFromCache(String path) {
        if (!libraryLoaded) return null;
        long ptr = nativeLoadMmapStatic(path);
        if (ptr == 0) return null;
        return new NativeSymSpell(ptr);
    }

    public boolean isValid() {
        return nativePtr != 0;
    }

    public void addWord(String normalized, String original, int frequency) {
        if (nativePtr == 0) return;
        nativeAddWord(nativePtr, normalized, original, frequency);
    }

    public void buildIndex() {
        if (nativePtr == 0) return;
        nativeBuildIndex(nativePtr);
    }

    /**
     * Lookup suggestions for input.
     */
    public SuggestItem[] lookup(String input, int maxSuggestions) {
        if (nativePtr == 0 || input == null || input.isEmpty()) return new SuggestItem[0];
        String[] raw = nativeLookup(nativePtr, input, maxSuggestions);
        return parseResults(raw, false);
    }

    /**
     * Lookup with keyboard-weighted distance.
     * @param layout keyboard layout name: "qwerty" or "йцукен"
     */
    public SuggestItem[] lookupWeighted(String input, int maxSuggestions, String layout) {
        if (nativePtr == 0 || input == null || input.isEmpty()) return new SuggestItem[0];
        String[] raw = nativeLookupWeighted(nativePtr, input, maxSuggestions, layout);
        return parseResults(raw, true);
    }

    /**
     * Save the index to binary cache file.
     */
    public boolean save(String path) {
        if (nativePtr == 0) return false;
        return nativeSave(nativePtr, path);
    }

    public int size() {
        if (nativePtr == 0) return 0;
        return nativeSize(nativePtr);
    }

    public int getFrequency(String word) {
        if (nativePtr == 0) return 0;
        return nativeGetFrequency(nativePtr, word);
    }

    public boolean containsWord(String word) {
        if (nativePtr == 0) return false;
        return nativeContains(nativePtr, word);
    }

    public void addBigram(String word1, String word2, String original2, int frequency) {
        if (nativePtr == 0) return;
        nativeAddBigram(nativePtr, word1, word2, original2, frequency);
    }

    public void buildBigramIndex() {
        if (nativePtr == 0) return;
        nativeBuildBigramIndex(nativePtr);
    }

    public BigramItem[] bigramLookup(String word1, int maxResults) {
        if (nativePtr == 0 || word1 == null || word1.isEmpty()) return new BigramItem[0];
        String[] raw = nativeBigramLookup(nativePtr, word1, maxResults);
        if (raw == null || raw.length < 2) return new BigramItem[0];
        int count = raw.length / 2;
        BigramItem[] items = new BigramItem[count];
        for (int i = 0; i < count; i++) {
            int freq = 0;
            try { freq = Integer.parseInt(raw[i * 2 + 1]); }
            catch (NumberFormatException e) { /* ignore */ }
            items[i] = new BigramItem(raw[i * 2], freq);
        }
        return items;
    }

    public int getBigramFrequency(String word1, String word2) {
        if (nativePtr == 0) return 0;
        return nativeBigramFrequency(nativePtr, word1, word2);
    }

    public int bigramCount() {
        if (nativePtr == 0) return 0;
        return nativeBigramCount(nativePtr);
    }

    public void destroy() {
        if (nativePtr != 0) {
            nativeDestroy(nativePtr);
            nativePtr = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        destroy();
        super.finalize();
    }

    /**
     * Prefix completion lookup — returns matches sorted by frequency desc.
     */
    public SuggestItem[] prefixLookup(String prefix, int maxResults) {
        if (nativePtr == 0 || prefix == null || prefix.isEmpty()) return new SuggestItem[0];
        String[] raw = nativePrefixLookup(nativePtr, prefix, maxResults);
        if (raw == null || raw.length < 2) return new SuggestItem[0];
        int count = raw.length / 2;
        SuggestItem[] items = new SuggestItem[count];
        for (int i = 0; i < count; i++) {
            String original = raw[i * 2];
            int frequency = 0;
            try { frequency = Integer.parseInt(raw[i * 2 + 1]); }
            catch (NumberFormatException e) { /* ignore */ }
            items[i] = new SuggestItem(original, original, 0, frequency, -1f);
        }
        return items;
    }

    /* Parse raw String[] quads from JNI into SuggestItem[] */
    private SuggestItem[] parseResults(String[] raw, boolean weighted) {
        if (raw == null || raw.length < 4) return new SuggestItem[0];
        int count = raw.length / 4;
        SuggestItem[] items = new SuggestItem[count];
        for (int i = 0; i < count; i++) {
            String term = raw[i * 4];
            String original = raw[i * 4 + 1];
            int distance = 0;
            float wd = -1f;
            int frequency = 0;
            try {
                if (weighted) {
                    wd = Float.parseFloat(raw[i * 4 + 2]);
                    distance = Math.round(wd);
                } else {
                    distance = Integer.parseInt(raw[i * 4 + 2]);
                }
                frequency = Integer.parseInt(raw[i * 4 + 3]);
            } catch (NumberFormatException e) {
                // ignore
            }
            items[i] = new SuggestItem(term, original, distance, frequency, wd);
        }
        return items;
    }

    /* Static wrapper for nativeLoadMmap (called before instance exists) */
    private static native long nativeLoadMmapStatic(String path);

    /* Native methods */
    private native long nativeCreate(int maxEditDistance, int prefixLength);
    private native void nativeAddWord(long ptr, String word, String original, int frequency);
    private native void nativeBuildIndex(long ptr);
    private native String[] nativeLookup(long ptr, String input, int maxSuggestions);
    private native String[] nativeLookupWeighted(long ptr, String input,
                                                  int maxSuggestions, String layout);
    private native String[] nativePrefixLookup(long ptr, String prefix, int maxResults);
    private native boolean nativeSave(long ptr, String path);
    private native void nativeDestroy(long ptr);
    private native int nativeSize(long ptr);
    private native int nativeGetFrequency(long ptr, String word);
    private native boolean nativeContains(long ptr, String word);

    /* Bigram native methods */
    private native void nativeAddBigram(long ptr, String word1, String word2,
                                         String original2, int frequency);
    private native void nativeBuildBigramIndex(long ptr);
    private native String[] nativeBigramLookup(long ptr, String word1, int maxResults);
    private native int nativeBigramFrequency(long ptr, String word1, String word2);
    private native int nativeBigramCount(long ptr);
}
