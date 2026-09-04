package com.ai10.k12kb.prediction;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Native SymSpell prediction engine.
 *
 * Delegates fuzzy matching AND prefix lookups to the C library
 * (libnativesymspell.so). All word indexes live in native memory —
 * no Java-side HashMaps needed. On cache hit, loading is just mmap + ready.
 */
public class NativeSymSpellEngine implements PredictionEngine {

    private static final String TAG = "NativeSymSpellEngine";
    private static final int DEFAULT_MAX_WORDS = 35000;
    private static final String CACHE_DIR = "native_dict_cache";
    private int maxWords = DEFAULT_MAX_WORDS;

    /**
     * Immutable (dictionary, locale) pair. Publishing a whole snapshot with one
     * volatile write keeps the two in sync — separate volatile fields could be
     * read torn, so a lookup could run against another locale's dictionary.
     */
    private static final class Snapshot {
        final NativeSymSpell ss;
        /**
         * Слова пользователя отдельным маленьким индексом. Готовый индекс из
         * пакета лежит в файле и читается отображением — дописать в него нельзя,
         * поэтому слова из словаря Android живут рядом и опрашиваются вместе.
         */
        final NativeSymSpell userSs;
        final String locale;

        Snapshot(NativeSymSpell ss, NativeSymSpell userSs, String locale) {
            this.ss = ss;
            this.userSs = userSs;
            this.locale = locale;
        }
    }

    /** The dictionary lookups run against. Null means "nothing loaded yet". */
    private volatile Snapshot active;
    /** Надстройки со словами пользователя — по языку, рядом с loaded. */
    private final Map<String, NativeSymSpell> userLoaded = new LinkedHashMap<>();
    /**
     * Dictionaries already built, keyed by locale, most recently used last.
     *
     * The engine used to hold exactly one: every language switch rebuilt the
     * incoming dictionary from its cache file and destroyed the outgoing one
     * (130-700 ms on a KEY2). Until that finished, lookups were answered by the
     * previous language's dictionary — that is, no suggestions at all for what
     * the user was typing. With Ctrl bound to language switch that window opens
     * constantly, which is what made the bar look like it worked only sometimes.
     * Keeping the built dictionaries makes a switch a pointer swap.
     *
     * Guarded by {@link #loadLock}.
     */
    private final LinkedHashMap<String, NativeSymSpell> loaded =
            new LinkedHashMap<>(4, 0.75f, true);
    /** How many dictionaries to keep alive. Two languages plus room to switch. */
    private static final int MAX_CACHED = 3;
    /** Serialises loads so two threads never build the same locale at once. */
    private final Object loadLock = new Object();
    /**
     * Guards native calls against the destroy() that follows a swap: taking the
     * write lock waits for every in-flight suggest() to finish, so the freed
     * instance can no longer be reached through a stale snapshot.
     */
    private final ReentrantReadWriteLock accessLock = new ReentrantReadWriteLock();
    private volatile ReadyListener readyListener;
    private String keyboardLayout = "qwerty";
    private boolean nextWordEnabled = true;
    private boolean keyboardAwareEnabled = true;

    @Override
    public void setReadyListener(ReadyListener listener) {
        this.readyListener = listener;
    }

    @Override
    public List<WordPredictor.Suggestion> suggest(String input, String previousWord, int limit) {
        accessLock.readLock().lock();
        try {
            Snapshot snap = active;
            if (snap == null) return Collections.emptyList();
            List<WordPredictor.Suggestion> main = suggestFrom(snap.ss, input, previousWord, limit);
            if (snap.userSs == null) return main;
            return MergeSuggestions(main,
                    suggestFrom(snap.userSs, input, previousWord, limit), limit);
        } finally {
            accessLock.readLock().unlock();
        }
    }

    /**
     * Слить подсказки основного словаря и надстройки: слово встречается в
     * обоих, поэтому берём лучший вариант и пересортировываем по оценке.
     */
    private static List<WordPredictor.Suggestion> MergeSuggestions(
            List<WordPredictor.Suggestion> a, List<WordPredictor.Suggestion> b, int limit) {
        if (b.isEmpty()) return a;
        ArrayList<WordPredictor.Suggestion> all = new ArrayList<>(a.size() + b.size());
        HashSet<String> seen = new HashSet<>();
        all.addAll(a);
        for (int i = 0; i < a.size(); i++)
            seen.add(a.get(i).word.toLowerCase(java.util.Locale.ROOT));
        for (int i = 0; i < b.size(); i++) {
            WordPredictor.Suggestion s = b.get(i);
            if (seen.add(s.word.toLowerCase(java.util.Locale.ROOT)))
                all.add(s);
        }
        Collections.sort(all, new Comparator<WordPredictor.Suggestion>() {
            public int compare(WordPredictor.Suggestion x, WordPredictor.Suggestion y) {
                return Double.compare(y.score, x.score);
            }
        });
        if (all.size() > limit)
            return new ArrayList<>(all.subList(0, limit));
        return all;
    }

    private List<WordPredictor.Suggestion> suggestFrom(NativeSymSpell ss, String input,
                                                       String previousWord, int limit) {
        String normalizedPrev = (previousWord != null && !previousWord.isEmpty())
                ? WordDictionary.normalize(previousWord) : null;

        // Next-word prediction: empty input with a previous word
        if (input == null || input.isEmpty()) {
            if (!nextWordEnabled) return Collections.emptyList();
            if (normalizedPrev == null || normalizedPrev.isEmpty()) return Collections.emptyList();
            return bigramNextWord(ss, normalizedPrev, limit);
        }

        String normalized = WordDictionary.normalize(input);
        if (normalized.isEmpty()) return Collections.emptyList();

        HashSet<String> seen = new HashSet<>();
        List<WordPredictor.Suggestion> top = new ArrayList<>();

        // 1. Prefix completions (from native prefix lookup)
        NativeSymSpell.SuggestItem[] completions = ss.prefixLookup(normalized, 100);
        for (NativeSymSpell.SuggestItem item : completions) {
            String word = item.original;
            String normEntry = WordDictionary.normalize(word);
            if (!normEntry.startsWith(normalized)) continue;
            if (word.length() <= input.length()) continue;
            if (word.equalsIgnoreCase(input)) continue;

            int effFreq = WordDictionary.effectiveFrequency(item.frequency);
            int minFreq = normalized.length() <= 2 ? 300 : (normalized.length() == 3 ? 250 : 150);
            if (effFreq < minFreq) continue;

            double score = computeScore(normalized, normEntry, word, item.frequency, 0, true, input.length());
            // Bigram boost for completions
            if (nextWordEnabled && normalizedPrev != null) {
                int bgFreq = ss.getBigramFrequency(normalizedPrev, normEntry);
                if (bgFreq > 0) {
                    score += bgFreq / 100.0;
                }
            }
            String key = word.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                insertSorted(top, new WordPredictor.Suggestion(word, 0, score), limit);
            }
        }

        // 2. Native SymSpell fuzzy matches (skip for single char, skip entirely if disabled)
        if (keyboardAwareEnabled && normalized.length() > 1) {
            int symLimit = normalized.length() <= 3 ? limit * 2 : limit * 4;
            NativeSymSpell.SuggestItem[] nativeResults =
                    ss.lookupWeighted(normalized, symLimit, keyboardLayout);

            for (NativeSymSpell.SuggestItem item : nativeResults) {
                if (normalized.length() <= 2 && item.distance > 1) continue;
                String word = item.original;
                if (word.equals(input)) continue;
                double score = computeScore(normalized, item.term, word, item.frequency,
                        item.distance, false, input.length());
                // Bonus for low weighted distance (adjacent key typos)
                if (item.weightedDistance >= 0 && item.weightedDistance < item.distance) {
                    score += (item.distance - item.weightedDistance) * 0.5;
                }
                // Bigram boost for fuzzy matches
                if (nextWordEnabled && normalizedPrev != null) {
                    int bgFreq = ss.getBigramFrequency(normalizedPrev, item.term);
                    if (bgFreq > 0) {
                        score += bgFreq / 100.0;
                    }
                }
                String key = word.toLowerCase(Locale.ROOT);
                if (seen.add(key)) {
                    insertSorted(top, new WordPredictor.Suggestion(word, item.distance, score), limit);
                }
            }
        }

        return top;
    }

    private List<WordPredictor.Suggestion> bigramNextWord(NativeSymSpell ss, String normalizedPrev, int limit) {
        NativeSymSpell.BigramItem[] items = ss.bigramLookup(normalizedPrev, limit);
        if (items.length == 0) return Collections.emptyList();

        List<WordPredictor.Suggestion> results = new ArrayList<>();
        for (NativeSymSpell.BigramItem item : items) {
            double score = item.frequency / 50.0;
            results.add(new WordPredictor.Suggestion(item.word, 0, score));
        }
        return results;
    }

    /**
     * @param max сколько слов держать; 0 — весь словарь.
     *
     * Ноль раньше отбрасывался как «значение не задано», поэтому выбор «Полный»
     * в настройках ничего не менял: движок оставался с прежним пределом.
     */
    public void setMaxWords(int max) {
        if (max >= 0) this.maxWords = max;
    }

    public void setKeyboardLayout(String layout) {
        if (layout != null && !layout.isEmpty()) {
            this.keyboardLayout = layout;
        }
    }

    public void setNextWordEnabled(boolean enabled) {
        this.nextWordEnabled = enabled;
    }

    public void setKeyboardAwareEnabled(boolean enabled) {
        this.keyboardAwareEnabled = enabled;
    }

    @Override
    public void loadDictionary(Context context, String locale) {
        synchronized (loadLock) {
            Snapshot cur = active;
            if (cur != null && locale.equals(cur.locale)) {
                return; // already the active dictionary
            }
            // Already built — switching languages is then just a pointer swap.
            NativeSymSpell cached = loaded.get(locale);
            if (cached != null) {
                publish(cached, locale);
                Log.w(TAG, "Switched to cached dictionary " + locale);
                return;
            }
            // Built into a fresh instance: the currently active dictionary keeps
            // serving suggestions for the whole rebuild instead of going dead.
            NativeSymSpell built = build(context, locale, true);
            if (built == null) {
                Log.e(TAG, "Load failed for " + locale + ", keeping previous dictionary");
                return;
            }
            loaded.put(locale, built);
            PutUserOverlay(context, locale, built);
            publish(built, locale);
            evictExtras();
        }
    }

    @Override
    public void preloadDictionary(Context context, String locale) {
        synchronized (loadLock) {
            Snapshot cur = active;
            if (cur != null && locale.equals(cur.locale)) return;
            if (loaded.containsKey(locale)) return;

            // Build it for real and keep it: the point of a preload is that the
            // switch to this language costs nothing. Materialising only the cache
            // file (what this did before) still left a full rebuild on the switch.
            NativeSymSpell built = build(context, locale, true);
            if (built != null) {
                loaded.put(locale, built);
                PutUserOverlay(context, locale, built);
                evictExtras();
                Log.w(TAG, "Preloaded " + locale
                        + ", active dict remains " + getLoadedLocale());
            }
        }
    }

    /**
     * Отдельный маленький индекс со словами пользователя.
     *
     * Нужен только к неизменяемому словарю: готовый индекс читается
     * отображением файла, дописать в него нельзя. При сборке из текста слова
     * пользователя подмешиваются прямо в словарь, и надстройка не нужна.
     */
    private void PutUserOverlay(Context context, String locale, NativeSymSpell main) {
        NativeSymSpell old = userLoaded.remove(locale);
        if (old != null) retire(old);
        if (main == null || !main.isMapped())
            return;
        if (!NativeSymSpell.isAvailable())
            return;
        NativeSymSpell user = new NativeSymSpell(2, 7);
        if (!user.isValid())
            return;
        loadUserWords(user, context);
        if (user.size() == 0) {
            user.destroy();
            return;
        }
        user.buildIndex();
        userLoaded.put(locale, user);
        Log.i(TAG, "Слова пользователя рядом с готовым индексом " + locale + ": " + user.size());
    }

    @Override
    public void closeAll() {
        synchronized (loadLock) {
            // Снимаем активный снимок под тем же замком, что и подсказки: пока
            // он не снят, кто-то может держать указатель на нативную память.
            accessLock.writeLock().lock();
            try {
                active = null;
            } finally {
                accessLock.writeLock().unlock();
            }
            for (NativeSymSpell ns : loaded.values()) ns.destroy();
            loaded.clear();
            for (NativeSymSpell ns : userLoaded.values()) ns.destroy();
            userLoaded.clear();
            Log.w(TAG, "Словари освобождены: состав языковых пакетов изменился");
        }
    }

    /**
     * Drop dictionaries past {@link #MAX_CACHED}, oldest first, never the active one.
     * Call under {@link #loadLock}.
     */
    private void evictExtras() {
        if (loaded.size() <= MAX_CACHED) return;
        String activeLocale = getLoadedLocale();
        Iterator<Map.Entry<String, NativeSymSpell>> it = loaded.entrySet().iterator();
        while (loaded.size() > MAX_CACHED && it.hasNext()) {
            Map.Entry<String, NativeSymSpell> e = it.next();
            if (e.getKey().equals(activeLocale)) continue;
            it.remove();
            NativeSymSpell overlay = userLoaded.remove(e.getKey());
            if (overlay != null) retire(overlay);
            retire(e.getValue());
        }
    }

    /**
     * Free a dictionary that is no longer reachable through {@link #active}.
     * Taking the write lock waits for every in-flight suggest() to return, so no
     * thread can still be holding it when the native memory goes away.
     */
    private void retire(NativeSymSpell ns) {
        if (ns == null) return;
        accessLock.writeLock().lock();
        accessLock.writeLock().unlock();
        ns.destroy();
    }

    @Override
    public boolean isReady() {
        return active != null;
    }

    @Override
    public String getLoadedLocale() {
        Snapshot snap = active;
        return (snap != null) ? snap.locale : "";
    }

    /**
     * Swap in a dictionary. The outgoing one is kept in {@link #loaded} for the next
     * switch back and is freed only by {@link #evictExtras()}, so nothing is
     * destroyed here.
     */
    private void publish(NativeSymSpell ns, String locale) {
        accessLock.writeLock().lock();
        try {
            active = new Snapshot(ns, userLoaded.get(locale), locale);
        } finally {
            accessLock.writeLock().unlock();
        }
        ReadyListener l = readyListener;
        if (l != null) {
            try {
                l.onEngineReady(locale);
            } catch (Throwable t) {
                Log.w(TAG, "ready listener failed: " + t);
            }
        }
    }

    /**
     * Имя кэша несёт и предел размера: словари, собранные под разные пределы,
     * не должны подменять друг друга. Раньше файл назывался только по языку, и
     * после смены размера мог подхватиться словарь от прежнего.
     */
    /** Слово с уже разобранной частотой. */
    private static final class Entry {
        final String word;
        final int freq;

        Entry(String word, int freq) {
            this.word = word;
            this.freq = freq;
        }
    }

    /** Число после позиции from, -1 если строка не разбирается. */
    private static int parseFreq(String line, int from) {
        int value = 0;
        int len = line.length();
        if (from >= len) return -1;
        for (int i = from; i < len; i++) {
            char c = line.charAt(i);
            if (c < '0' || c > '9') return i == from ? -1 : value;
            value = value * 10 + (c - '0');
        }
        return value;
    }

    /**
     * Готовый индекс: сначала папка приложения, затем языковой пакет.
     *
     * Из пакета файл открывается дескриптором и отображается прямо из APK —
     * если он лежит там несжатым. Сжатую запись отобразить нельзя (её начало
     * не файл, а deflate-поток), поэтому дальше идёт запасной путь: копия
     * рядом с кэшем, один раз, и отображение уже с неё.
     */
    private NativeSymSpell LoadPrebuiltIndex(Context context, String locale) {
        // Имя несёт предел размера: пакет привозит индекс под конкретное значение
        // настройки. Стоит пользователю выбрать другой объём — подходящего файла
        // не найдётся, и словарь соберётся на устройстве под новый предел.
        String name = cacheName(locale);

        File own = new File(context.getFilesDir(), CACHE_DIR + "/" + name);
        if (own.exists()) {
            NativeSymSpell ns = NativeSymSpell.loadFromCache(own.getAbsolutePath());
            if (ns != null && ns.size() > 0) {
                Log.i(TAG, "Готовый индекс из папки приложения: " + own);
                return ns;
            }
            if (ns != null) ns.destroy();
        }

        String asset = "dictionaries/" + name;

        // Отображение прямо из APK: ни распаковки, ни второй копии на диске.
        NativeSymSpell mapped = LoadMappedFromApk(context, asset);
        if (mapped != null) {
            // Копия, распакованная прежней версией клавиатуры, больше не нужна:
            // она бы так и лежала сотнями мегабайт, раз сюда уже не доходят.
            LanguagePacks.dropStaleCopies(new File(context.getFilesDir(), CACHE_DIR),
                    "pack-" + locale + "-", "");
            Log.i(TAG, "Готовый индекс отображён из пакета: " + asset);
            return mapped;
        }

        // В имени копии — версия пакета: после обновления пакета старая копия
        // больше не подходит по имени и перечитывается, а не живёт вечно.
        int version = LanguagePacks.assetVersion(context, asset);
        File dir = new File(context.getFilesDir(), CACHE_DIR);
        String copyName = "pack-" + locale + "-v" + version + "-" + name;
        File copied = new File(dir, copyName);
        // Копии от других размеров и прежних версий этого языка не нужны:
        // перебор размеров иначе оставлял на диске сотни мегабайт.
        LanguagePacks.dropStaleCopies(dir, "pack-" + locale + "-", copyName);
        if (!copied.exists() && !LanguagePacks.copyAsset(context, asset, copied))
            return null;

        NativeSymSpell ns = NativeSymSpell.loadFromCache(copied.getAbsolutePath());
        if (ns != null && ns.size() > 0) {
            Log.i(TAG, "Готовый индекс из пакета: " + copied);
            return ns;
        }
        if (ns != null) ns.destroy();
        // Файл есть, а индекс из него не поднялся — он испорчен или собран
        // прежним форматом. Держать его смысла нет, место он занимает.
        copied.delete();
        return null;
    }

    /**
     * Попытка отобразить индекс несжатой записью APK — своего или пакета.
     * Возвращает null, если файла нет или он лежит сжатым.
     */
    private static NativeSymSpell LoadMappedFromApk(Context context, String asset) {
        AssetFileDescriptor afd = null;
        try {
            try {
                afd = context.getAssets().openFd(asset);
            } catch (Throwable ownMiss) {
                afd = LanguagePacks.openFd(context, asset);
            }
            if (afd == null)
                return null;
            NativeSymSpell ns = NativeSymSpell.loadFromAssetFd(
                    afd.getParcelFileDescriptor().getFd(),
                    afd.getStartOffset(), afd.getLength());
            if (ns == null)
                return null;
            if (ns.size() > 0)
                return ns;
            ns.destroy();
            return null;
        } catch (Throwable ex) {
            return null;
        } finally {
            // Дескриптор наш: нативная сторона дублирует его себе, а открытым
            // он держал бы чужой APK.
            LanguagePacks.Close(afd);
        }
    }

    private String cacheName(String locale) {
        return locale + "-" + (maxWords > 0 ? String.valueOf(maxWords) : "full") + ".ssnd";
    }

    private String cachePath(Context context, String locale) {
        return new File(context.getFilesDir(), CACHE_DIR + "/" + cacheName(locale)).getAbsolutePath();
    }

    /**
     * Build a dictionary for a locale into a fresh, unpublished instance.
     * Never touches {@link #active}, so it is safe to run while lookups are in flight.
     *
     * @param withUserWords merge the user dictionary in (skip for cache-warming preloads)
     * @return the built instance, or null on failure — caller keeps the old dictionary
     */
    private NativeSymSpell build(Context context, String locale, boolean withUserWords) {
        long startTime = System.currentTimeMillis();
        String cachePath = cachePath(context, locale);

        // Порядок источников:
        //   1. готовый индекс в папке приложения (положен пользователем);
        //   2. готовый индекс из языкового пакета;
        //   3. свой кэш, собранный ранее;
        //   4. текстовый словарь — собрать и закэшировать (см. ниже).
        // Готовый индекс открывается отображением: ни сборки, ни расхода памяти.
        // Ограничение размера к нему не применяется — он собран целиком.
        NativeSymSpell ns = LoadPrebuiltIndex(context, locale);
        if (ns == null)
            ns = NativeSymSpell.loadFromCache(cachePath);
        boolean fromCache = (ns != null && ns.size() > 0);
        if (ns != null && !fromCache) {
            ns.destroy();
            ns = null;
        }

        if (fromCache) {
            // Индекс без биграмм: дочитываем их из JSON и пересохраняем. Для
            // отображённого индекса это невозможно — добавление в него ничего
            // не делает, и разбор файла каждый раз уходил бы впустую.
            if (ns.bigramCount() == 0 && !ns.isMapped()) {
                loadBigrams(ns, context, locale);
                if (ns.bigramCount() > 0) {
                    ns.buildBigramIndex();
                    saveNativeCache(ns, context, locale);
                    Log.w(TAG, "Upgraded cache to v3 with " + ns.bigramCount() + " bigrams");
                }
            }
            long elapsed = System.currentTimeMillis() - startTime;
            WordDictionary.recordLoadStats(locale, "native-cache", ns.size(), elapsed);
            Log.w(TAG, "Loaded " + locale + " from native cache: " + ns.size()
                    + " words, " + ns.bigramCount() + " bigrams in " + elapsed + "ms");
        } else {
            WordDictionary.recordLoadingStart(locale);

            // v1 cache exists but couldn't be loaded (version mismatch) — delete it
            File cacheFile = new File(cachePath);
            if (cacheFile.exists()) {
                cacheFile.delete();
                Log.w(TAG, "Deleted stale v1 cache: " + cachePath);
            }

            if (!NativeSymSpell.isAvailable()) {
                Log.w(TAG, "Native library not available, cannot load");
                return null;
            }
            ns = new NativeSymSpell(2, 7);
            if (!ns.isValid()) {
                Log.e(TAG, "Failed to create native SymSpell instance");
                return null;
            }

            int wordCount = loadFromAssets(ns, context, locale);
            if (wordCount <= 0) {
                Log.e(TAG, "No words loaded for locale " + locale);
                ns.destroy();
                return null;
            }

            // Слова пользователя подмешиваются ДО построения индекса. Раньше они
            // добавлялись после, и индекс на сотни тысяч слов строился второй раз
            // целиком: у английского это 5.7 секунды сборки плюс ещё 8.5 на
            // пересборку — вдвое дольше на ровном месте.
            if (withUserWords)
                loadUserWords(ns, context);

            long buildStart = System.currentTimeMillis();
            ns.buildIndex();
            ns.buildBigramIndex();
            Log.w(TAG, "Native buildIndex for " + locale + ": "
                    + (System.currentTimeMillis() - buildStart) + "ms"
                    + " (" + ns.bigramCount() + " bigrams)");

            long elapsed = System.currentTimeMillis() - startTime;
            WordDictionary.recordLoadStats(locale, "native-assets", wordCount, elapsed);
            Log.w(TAG, "Loaded " + locale + " from assets (native): " + wordCount
                    + " words in " + elapsed + "ms");
        }

        // Ниже — только для словаря, поднятого из готового индекса: при сборке из
        // текста слова пользователя уже подмешаны выше.
        if (withUserWords && !fromCache) {
            // ничего: слова добавлены до построения индекса
        } else if (withUserWords && ns.isMapped()) {
            // Готовый индекс неизменяем: он лежит в файле и читается
            // отображением. Слова пользователя живут отдельной надстройкой —
            // её собирает PutUserOverlay, и подсказки опрашивают обе.
            Log.i(TAG, "Готовый индекс " + locale + " неизменяем, слова пользователя идут надстройкой");
        } else if (withUserWords) {
            // Always merge user words (even after a cache load — user may have added new ones)
            int before = ns.size();
            loadUserWords(ns, context);
            if (ns.size() > before) {
                ns.buildIndex();
                Log.w(TAG, "Added " + (ns.size() - before) + " new user words, rebuilt index");
                saveNativeCache(ns, context, locale);
                return ns;
            }
        }
        if (!fromCache) {
            saveNativeCache(ns, context, locale);
        }
        return ns;
    }

    private void loadBigrams(NativeSymSpell ns, Context context, String locale) {
        String bigramFile = "dictionaries/" + locale + "_bigrams.json";
        BufferedReader reader = null;
        try {
            InputStream is;
            try {
                is = context.getAssets().open(bigramFile);
            } catch (java.io.FileNotFoundException e) {
                is = LanguagePacks.open(context, bigramFile);
                if (is == null)
                    throw e;
            }
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 8192);
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int read;
            while ((read = reader.read(buf)) != -1) sb.append(buf, 0, read);

            JSONObject root = new JSONObject(sb.toString());
            int count = 0;
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String word1 = keys.next();
                JSONArray pairs = root.getJSONArray(word1);
                for (int i = 0; i < pairs.length(); i++) {
                    JSONArray pair = pairs.getJSONArray(i);
                    String word2 = pair.getString(0);
                    int freq = pair.getInt(1);
                    String normalized2 = WordDictionary.normalize(word2);
                    ns.addBigram(word1, normalized2, word2, freq);
                    count++;
                }
            }
            Log.d(TAG, "Loaded " + count + " bigrams for " + locale);
        } catch (java.io.FileNotFoundException e) {
            Log.d(TAG, "No bigram file for " + locale);
        } catch (Exception e) {
            Log.w(TAG, "Failed to load bigrams for " + locale + ": " + e);
        } finally {
            // Поток закрываем и на пути ошибки: он держит дескриптор внутри
            // чужого APK, а такие загрузки повторяются на каждое переключение.
            LanguagePacks.Close(reader);
        }
    }

    private int loadFromAssets(NativeSymSpell ns, Context context, String locale) {
        String txtFilename = "dictionaries/" + locale + "_base.txt";
        String jsonFilename = "dictionaries/" + locale + "_base.json";
        BufferedReader reader = null;
        try {
            InputStream is;
            boolean useTxt;
            try {
                is = context.getAssets().open(txtFilename);
                useTxt = true;
            } catch (java.io.FileNotFoundException e) {
                try {
                    is = context.getAssets().open(jsonFilename);
                    useTxt = false;
                } catch (java.io.FileNotFoundException e2) {
                    // Своего словаря для языка нет — ищем в установленных пакетах.
                    is = LanguagePacks.open(context, txtFilename);
                    useTxt = true;
                    if (is == null) {
                        is = LanguagePacks.open(context, jsonFilename);
                        useTxt = false;
                    }
                    if (is == null)
                        throw e;
                }
            }
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 16384);

            // Частота разбирается сразу при чтении: раньше она хранилась строкой и
            // Integer.parseInt звался внутри сравнения — на словаре в 668 тысяч
            // слов это десятки миллионов разборов только ради сортировки.
            ArrayList<Entry> allEntries = new ArrayList<>(1 << 16);
            if (useTxt) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) break;
                    int tab = line.indexOf('\t');
                    if (tab <= 0) continue;
                    int freq = parseFreq(line, tab + 1);
                    if (freq < 0) continue;
                    allEntries.add(new Entry(line.substring(0, tab), freq));
                }
            } else {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int read;
                while ((read = reader.read(buf)) != -1) sb.append(buf, 0, read);
                org.json.JSONArray arr = new org.json.JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    if (Thread.currentThread().isInterrupted()) break;
                    org.json.JSONObject obj = arr.getJSONObject(i);
                    allEntries.add(new Entry(obj.getString("w"), obj.getInt("f")));
                }
            }
            reader.close();

            // Сортировка нужна лишь затем, чтобы отрезать хвост по частоте. При
            // полном словаре резать нечего, и на 668 тысячах слов это заметное
            // время впустую.
            if (maxWords > 0 && allEntries.size() > maxWords) {
                Collections.sort(allEntries, new Comparator<Entry>() {
                    public int compare(Entry a, Entry b) {
                        return b.freq - a.freq;
                    }
                });
                allEntries = new ArrayList<>(allEntries.subList(0, maxWords));
            }

            // Add to native SymSpell with both normalized and original forms
            for (Entry entry : allEntries) {
                if (Thread.currentThread().isInterrupted()) break;
                ns.addWord(WordDictionary.normalize(entry.word), entry.word, entry.freq);
            }

            // Load bigrams
            loadBigrams(ns, context, locale);

            return allEntries.size();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load dictionary: " + e);
            return 0;
        } finally {
            LanguagePacks.Close(reader);
        }
    }

    private void loadUserWords(NativeSymSpell ns, Context context) {
        List<UserDictionaryBridge.UserWord> userWords = UserDictionaryBridge.readAll(context);
        if (userWords.isEmpty()) return;
        int added = 0;
        for (UserDictionaryBridge.UserWord uw : userWords) {
            int freq = Math.max(uw.frequency, 200);
            String normalized = WordDictionary.normalize(uw.word);
            ns.addWord(normalized, uw.word, freq);
            if (uw.shortcut != null && !uw.shortcut.isEmpty()) {
                String normShort = WordDictionary.normalize(uw.shortcut);
                ns.addWord(normShort, uw.shortcut, freq);
            }
            added++;
        }
        if (added > 0) {
            Log.d(TAG, "Added " + added + " user dictionary words (native)");
        }
    }

    private void saveNativeCache(NativeSymSpell ns, Context context, String locale) {
        if (Thread.currentThread().isInterrupted()) return;
        try {
            File dir = new File(context.getFilesDir(), CACHE_DIR);
            dir.mkdirs();
            String path = new File(dir, cacheName(locale)).getAbsolutePath();
            if (ns.save(path)) {
                Log.d(TAG, "Saved native cache: " + path);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save native cache: " + e);
        }
    }

    private double computeScore(String normalizedInput, String normalizedCandidate,
                                String word, int frequency, int distance,
                                boolean isPrefix, int inputLen) {
        int effFreq = WordDictionary.effectiveFrequency(frequency);
        double distanceScore = 1.0 / (1 + distance);
        double frequencyScore = effFreq / 1600.0;
        double prefixBonus = 0;
        if (isPrefix) {
            if (inputLen <= 2) prefixBonus = 2.0;
            else prefixBonus = 5.0;
        } else if (normalizedCandidate.startsWith(normalizedInput)) {
            if (inputLen <= 2) prefixBonus = 1.5;
            else prefixBonus = 3.0;
        }

        int lenDiff = Math.abs(word.length() - inputLen);
        double lengthBonus;
        if (lenDiff == 0) lengthBonus = 0.35;
        else if (lenDiff == 1) lengthBonus = 0.2;
        else if (lenDiff == 2) lengthBonus = 0.05;
        else lengthBonus = -0.15 * Math.min(lenDiff, 4);

        return distanceScore + frequencyScore + prefixBonus + lengthBonus;
    }

    private void insertSorted(List<WordPredictor.Suggestion> list, WordPredictor.Suggestion item, int limit) {
        list.add(item);
        Collections.sort(list, new Comparator<WordPredictor.Suggestion>() {
            public int compare(WordPredictor.Suggestion a, WordPredictor.Suggestion b) {
                int s = Double.compare(b.score, a.score);
                if (s != 0) return s;
                return Integer.compare(a.distance, b.distance);
            }
        });
        while (list.size() > limit) {
            list.remove(list.size() - 1);
        }
    }
}
