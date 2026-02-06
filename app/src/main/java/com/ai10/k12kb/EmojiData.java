package com.ai10.k12kb;

public class EmojiData {

    private static final String[][] PAGES = {
        // Page 1: Smileys (Emoji 5.0 and below only for Android 8.x)
        {
            "\uD83D\uDE00", "\uD83D\uDE03", "\uD83D\uDE04", "\uD83D\uDE01", "\uD83D\uDE06",
            "\uD83D\uDE05", "\uD83E\uDD23", "\uD83D\uDE02", "\uD83D\uDE42", "\uD83D\uDE43",
            "\uD83D\uDE09", "\uD83D\uDE0A", "\uD83D\uDE07", "\uD83D\uDE0E", "\uD83D\uDE0D",
            "\uD83E\uDD29", "\uD83D\uDE18", "\uD83D\uDE17", "\uD83D\uDE1A",
            "\uD83D\uDE19", "\uD83D\uDE0B", "\uD83D\uDE1B", "\uD83D\uDE1C", "\uD83E\uDD2A",
            "\uD83D\uDE1D", "\uD83E\uDD11", "\uD83E\uDD17"
        },
        // Page 2: More faces
        {
            "\uD83E\uDD2D", "\uD83E\uDD2B", "\uD83E\uDD25", "\uD83D\uDE36", "\uD83D\uDE0F",
            "\uD83D\uDE10", "\uD83D\uDE11", "\uD83D\uDE2C", "\uD83D\uDE44", "\uD83D\uDE0C",
            "\uD83D\uDE14", "\uD83D\uDE2A", "\uD83E\uDD24", "\uD83D\uDE34", "\uD83D\uDE37",
            "\uD83E\uDD12", "\uD83E\uDD15", "\uD83E\uDD22", "\uD83E\uDD2E",
            "\uD83E\uDD27", "\uD83D\uDE33", "\uD83D\uDE28", "\uD83D\uDE16", "\uD83D\uDE35",
            "\uD83E\uDD2F", "\uD83D\uDE32", "\uD83E\uDD20"
        },
        // Page 3: Hearts & symbols
        {
            "\u2764\uFE0F", "\uD83E\uDDE1", "\uD83D\uDC9B", "\uD83D\uDC9A", "\uD83D\uDC99",
            "\uD83D\uDC9C", "\uD83D\uDDA4", "\uD83D\uDC9F", "\uD83D\uDC8C", "\uD83D\uDC94",
            "\uD83D\uDC95", "\uD83D\uDC9E", "\uD83D\uDC93", "\uD83D\uDC97", "\uD83D\uDC96",
            "\uD83D\uDC98", "\uD83D\uDC9D", "\uD83D\uDC9F", "\u2763\uFE0F",
            "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4A", "\u270A", "\uD83E\uDD1B",
            "\uD83E\uDD1C", "\uD83E\uDD1E", "\u270C\uFE0F"
        },
        // Page 4: Gestures
        {
            "\uD83E\uDD1F", "\uD83E\uDD18", "\uD83D\uDC4C", "\u270D\uFE0F", "\uD83D\uDC50",
            "\uD83D\uDC48", "\uD83D\uDC49", "\uD83D\uDC46", "\uD83D\uDC47", "\u261D\uFE0F",
            "\u270B", "\uD83E\uDD1A", "\uD83D\uDD90\uFE0F", "\uD83D\uDD96", "\uD83D\uDC4B",
            "\uD83E\uDD19", "\uD83D\uDCAA", "\uD83D\uDE4F", "\uD83E\uDD1D",
            "\uD83D\uDC4F", "\uD83D\uDE4C", "\uD83E\uDD32", "\uD83D\uDC90", "\uD83C\uDF39",
            "\uD83C\uDF37", "\uD83C\uDF3B", "\uD83C\uDF3A"
        },
        // Page 5: Animals
        {
            "\uD83D\uDC36", "\uD83D\uDC31", "\uD83D\uDC2D", "\uD83D\uDC39", "\uD83D\uDC30",
            "\uD83E\uDD8A", "\uD83D\uDC3B", "\uD83D\uDC3C", "\uD83D\uDC28", "\uD83D\uDC2F",
            "\uD83E\uDD81", "\uD83D\uDC2E", "\uD83D\uDC37", "\uD83D\uDC38", "\uD83D\uDC35",
            "\uD83D\uDC14", "\uD83D\uDC27", "\uD83D\uDC26", "\uD83D\uDC24",
            "\uD83E\uDD8B", "\uD83D\uDC1B", "\uD83D\uDC1D", "\uD83D\uDC1E", "\uD83E\uDD80",
            "\uD83D\uDC19", "\uD83D\uDC33", "\uD83D\uDC2C"
        }
    };

    public static int getPageCount() {
        return PAGES.length;
    }

    public static String[] getPage(int page) {
        if (page < 0 || page >= PAGES.length) return PAGES[0];
        return PAGES[page];
    }
}
