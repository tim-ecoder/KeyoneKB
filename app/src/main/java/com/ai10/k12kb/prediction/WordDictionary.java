package com.ai10.k12kb.prediction;

import android.content.Context;

import java.io.File;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;

/**
 * Static utilities for word prediction: normalization, frequency scaling,
 * load-stats tracking, and cache management.
 */
public class WordDictionary {

    /** Loading stats per locale — survives across instances (static). */
    public static class LoadStats {
        public final String locale;
        public final String source;  // "cache", "assets", or "loading"
        public final int wordCount;
        public final long timeMs;
        public final long timestamp;

        public LoadStats(String locale, String source, int wordCount, long timeMs) {
            this.locale = locale;
            this.source = source;
            this.wordCount = wordCount;
            this.timeMs = timeMs;
            this.timestamp = System.currentTimeMillis();
        }
    }
    private static final HashMap<String, LoadStats> loadStatsMap = new HashMap<>();

    public static LoadStats getLoadStats(String locale) {
        return loadStatsMap.get(locale);
    }

    public static HashMap<String, LoadStats> getAllLoadStats() {
        return loadStatsMap;
    }

    /** Record that loading has started for a locale (used by all engines). */
    public static void recordLoadingStart(String locale) {
        loadStatsMap.put(locale, new LoadStats(locale, "loading", 0, 0));
    }

    /** Record completed load stats for a locale (used by all engines). */
    public static void recordLoadStats(String locale, String source, int wordCount, long timeMs) {
        loadStatsMap.put(locale, new LoadStats(locale, source, wordCount, timeMs));
    }

    public static void clearLoadStats() {
        loadStatsMap.clear();
    }

    public static void clearCacheFiles(Context context) {
        String[] dirs = {"dict_cache", "native_dict_cache"};
        for (String dirName : dirs) {
            File dir = new File(context.getFilesDir(), dirName);
            if (dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        f.delete();
                    }
                }
            }
        }
    }

    public static class DictEntry {
        public final String word;
        public final int frequency;

        public DictEntry(String word, int frequency) {
            this.word = word;
            this.frequency = frequency;
        }
    }

    /**
     * Check if native binary cache file exists for a locale.
     */
    /**
     * Есть ли собранный словарь для языка под выбранный размер.
     *
     * Предел размера — часть имени файла. Отвечать «да» на кеш другого размера
     * значило бы обещать мгновенную загрузку там, где словарь будет собираться
     * заново десятки секунд.
     */
    public static boolean hasCacheFile(Context context, String locale, int maxWords) {
        File dir = new File(context.getFilesDir(), "native_dict_cache");
        File[] files = dir.listFiles();
        if (files == null)
            return false;
        String suffix = "-" + (maxWords > 0 ? String.valueOf(maxWords) : "full") + ".ssnd";
        for (File f : files) {
            String name = f.getName();
            if (name.endsWith(suffix)
                    && (name.startsWith(locale + "-") || name.startsWith("pack-" + locale + "-")))
                return true;
        }
        return false;
    }

    /**
     * Effective frequency: scale 0-255 to 0-1600 using power curve.
     */
    public static int effectiveFrequency(int rawFrequency) {
        double normalized = rawFrequency / 255.0;
        return Math.max(1, (int) (Math.pow(normalized, 0.75) * 1600.0));
    }

    /**
     * Check if a character is a "word character" for prediction purposes.
     * Includes letters, digits, apostrophe, and email-like chars (@, ., -, _).
     */
    public static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '\'' || c == '@' || c == '.' || c == '-' || c == '_';
    }

    /**
     * Normalize a word: lowercase, strip accents, keep letters/digits/apostrophe
     * and email-like characters (@, ., -, _).
     */
    /**
     * Версия правила {@link #normalize(String)}. Кеш, собранный по прежнему
     * правилу, ищет по другим ключам, поэтому при изменении правила его нужно
     * один раз выбросить — см. проверку в K12KbIME.
     *
     * 1 — снятие диакритики со всех букв, включая кириллицу (ё и й склеивались
     *     с е и и);
     * 2 — кириллица через NFD не проходит;
     * 3 — список для поиска по префиксу отсортирован по свёрнутым буквам, а
     *     веса ё-написаний пересчитаны: кеш прежнего порядка даёт неверные
     *     дополнения.
     */
    public static final int NORMALIZATION_REVISION = 3;

    /** Кириллица, включая расширения: диакритику у этих букв снимать нельзя. */
    private static boolean IsCyrillic(char c) {
        return c >= '\u0400' && c <= '\u052F';
    }

    /**
     * Нижний регистр, единый апостроф, снятие диакритики, только буквы, цифры
     * и ' @ . - _ .
     *
     * Кириллица через NFD не проходит вовсе. Разложение считает ё за «е с
     * диерезисом», а й за «и с бреве», и снятие диакритики склеивало разные
     * слова: «всё» с «все», «свой» со «свои», «отношений» с «отношении».
     * SymSpell хранит один оригинал на ключ, поэтому в индекс попадало только
     * частотное написание, а второе слово пропадало из подсказок совсем — 9934
     * слова русского словаря, из них 6917 из-за ё и 2987 из-за й. Нечёткому
     * поиску это не мешает: буквы остаются на расстоянии одной правки.
     *
     * Правило обязано совпадать с tools/prepare_dict.py: индекс собирается там,
     * а ключ для поиска считается здесь, и разойтись им нельзя.
     */
    /**
     * Буква без диакритики: ё -> е, й -> и, ї -> і, ў -> у.
     *
     * Написания одного слова должны совпадать при сравнении префиксов, иначе
     * набранное «зелены» не считает «зелёный» своим дополнением. То же правило
     * действует в поиске (keyboard_distance.c, kb_letter_base).
     */
    public static char letterBase(char c) {
        switch (c) {
            case '\u0451': return '\u0435';  // ё -> е
            case '\u0450': return '\u0435';  // ѐ -> е
            case '\u0439': return '\u0438';  // й -> и
            case '\u045D': return '\u0438';  // ѝ -> и
            case '\u0457': return '\u0456';  // ї -> і
            case '\u045E': return '\u0443';  // ў -> у
            case '\u0453': return '\u0433';  // ѓ -> г
            case '\u045C': return '\u043A';  // ќ -> к
            default: return c;
        }
    }

    /**
     * Ключ, по которому подсказки считаются одним и тем же словом.
     *
     * Схлопывается только ё: «ещё» и «ёще» — написания одного слова, показывать
     * оба незачем, остаётся то, что выше по оценке. Й не схлопываем: «мои» и
     * «мой» — разные слова, и оба должны доходить до панели.
     */
    public static String suggestionKey(String word) {
        return word.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
    }

    /** Начинается ли слово с префикса, если написания одной буквы считать равными. */
    public static boolean foldedStartsWith(String word, String prefix) {
        if (word.length() < prefix.length()) return false;
        for (int i = 0; i < prefix.length(); i++) {
            if (letterBase(word.charAt(i)) != letterBase(prefix.charAt(i)))
                return false;
        }
        return true;
    }

    public static String normalize(String word) {
        if (word == null || word.isEmpty()) return "";
        String lower = word.toLowerCase(Locale.ROOT);
        // Normalize apostrophes
        lower = lower.replace('\u2018', '\'').replace('\u2019', '\'').replace('\u02BC', '\'');

        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK)
                continue;
            if (IsCyrillic(c)) {
                if (Character.isLetterOrDigit(c))
                    sb.append(c);
                continue;
            }
            String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            for (int j = 0; j < decomposed.length(); j++) {
                char d = decomposed.charAt(j);
                if (Character.getType(d) != Character.NON_SPACING_MARK && isWordChar(d))
                    sb.append(d);
            }
        }
        return sb.toString();
    }
}
