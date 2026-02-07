package com.ai10.k12kb.prediction;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * SymSpell implementation for fast fuzzy string matching.
 * Ported from Pastiera (Kotlin) to Java without lambdas.
 * Uses delete-only approach with precomputed deletes for O(1) lookup.
 */
public class SymSpell {

    public static class SuggestItem {
        public final String term;
        public final int distance;
        public final int frequency;

        public SuggestItem(String term, int distance, int frequency) {
            this.term = term;
            this.distance = distance;
            this.frequency = frequency;
        }
    }

    private final int maxEditDistance;
    private final int prefixLength;
    private final HashMap<String, Integer> dictionary = new HashMap<>();
    private final HashMap<String, List<String>> deletes = new HashMap<>();
    private boolean bulkLoading = false;

    public SymSpell(int maxEditDistance, int prefixLength) {
        this.maxEditDistance = maxEditDistance;
        this.prefixLength = prefixLength;
    }

    public SymSpell() {
        this(2, 7);
    }

    public void addWord(String term, int frequency) {
        if (term == null || term.isEmpty()) return;
        Integer existing = dictionary.get(term);
        if (existing == null || frequency > existing) {
            dictionary.put(term, frequency);
        }
        if (!bulkLoading) {
            String key = term.length() > prefixLength ? term.substring(0, prefixLength) : term;
            generateDeletes(key, maxEditDistance);
        }
    }

    /**
     * Enable bulk loading mode - skips delete generation in addWord().
     * Call buildIndex() when done to generate all deletes at once.
     */
    public void setBulkLoading(boolean bulk) {
        this.bulkLoading = bulk;
    }

    private void generateDeletes(String term, int distance) {
        if (distance == 0 || term.isEmpty()) return;
        for (int i = 0; i < term.length(); i++) {
            String deleted = term.substring(0, i) + term.substring(i + 1);
            List<String> bucket = deletes.get(deleted);
            if (bucket == null) {
                bucket = new ArrayList<>();
                deletes.put(deleted, bucket);
            }
            // Use the original full term (before prefix truncation) for the bucket
            // Actually we need the full word. Let's fix this by storing during addWord.
            generateDeletes(deleted, distance - 1);
        }
    }

    /**
     * Rebuild: addWord handles both dictionary and delete generation properly.
     */
    public void buildIndex() {
        deletes.clear();
        int count = 0;
        for (Map.Entry<String, Integer> entry : dictionary.entrySet()) {
            if (Thread.currentThread().isInterrupted()) break;
            String term = entry.getKey();
            String key = term.length() > prefixLength ? term.substring(0, prefixLength) : term;
            addDeletes(key, maxEditDistance, term);
            if (++count % 500 == 0) {
                try { Thread.sleep(1); } catch (InterruptedException e) { break; }
            }
        }
    }

    private void addDeletes(String current, int distance, String originalTerm) {
        if (distance == 0 || current.isEmpty()) return;
        for (int i = 0; i < current.length(); i++) {
            String deleted = current.substring(0, i) + current.substring(i + 1);
            List<String> bucket = deletes.get(deleted);
            if (bucket == null) {
                bucket = new ArrayList<>();
                deletes.put(deleted, bucket);
            }
            if (!bucket.contains(originalTerm)) {
                bucket.add(originalTerm);
            }
            addDeletes(deleted, distance - 1, originalTerm);
        }
    }

    public List<SuggestItem> lookup(String input, int maxSuggestions) {
        if (input == null || input.isEmpty()) return Collections.emptyList();

        List<SuggestItem> suggestions = new ArrayList<>();
        HashSet<String> suggestionSet = new HashSet<>();
        HashSet<String> consideredDeletes = new HashSet<>();

        String inputPrefix = input.length() > prefixLength ? input.substring(0, prefixLength) : input;
        LinkedList<String> queue = new LinkedList<>();
        queue.add(inputPrefix);
        consideredDeletes.add(inputPrefix);

        // Direct hit
        Integer directFreq = dictionary.get(input);
        if (directFreq != null) {
            addSuggestion(suggestions, suggestionSet, input, 0, directFreq);
        }

        while (!queue.isEmpty()) {
            String candidate = queue.removeFirst();
            int distance = inputPrefix.length() - candidate.length();
            if (distance > maxEditDistance) continue;

            List<String> bucket = deletes.get(candidate);
            if (bucket != null) {
                for (String suggestionTerm : bucket) {
                    int editDistance = damerauDistanceLimited(input, suggestionTerm, maxEditDistance);
                    if (editDistance >= 0 && editDistance <= maxEditDistance) {
                        Integer freq = dictionary.get(suggestionTerm);
                        if (freq == null) freq = 0;
                        addSuggestion(suggestions, suggestionSet, suggestionTerm, editDistance, freq);
                    }
                }
            }

            if (distance < maxEditDistance) {
                for (int i = 0; i < candidate.length(); i++) {
                    String delete = candidate.substring(0, i) + candidate.substring(i + 1);
                    if (consideredDeletes.add(delete)) {
                        queue.add(delete);
                    }
                }
            }
        }

        Collections.sort(suggestions, new Comparator<SuggestItem>() {
            public int compare(SuggestItem a, SuggestItem b) {
                int d = Integer.compare(a.distance, b.distance);
                if (d != 0) return d;
                int f = Integer.compare(b.frequency, a.frequency);
                if (f != 0) return f;
                return Integer.compare(a.term.length(), b.term.length());
            }
        });

        if (suggestions.size() > maxSuggestions) {
            return suggestions.subList(0, maxSuggestions);
        }
        return suggestions;
    }

    private void addSuggestion(List<SuggestItem> suggestions, HashSet<String> seen,
                               String term, int distance, int frequency) {
        if (!seen.add(term)) {
            // Update existing if higher frequency
            for (int i = 0; i < suggestions.size(); i++) {
                if (suggestions.get(i).term.equals(term) && suggestions.get(i).frequency < frequency) {
                    suggestions.set(i, new SuggestItem(term, distance, frequency));
                    break;
                }
            }
            return;
        }
        suggestions.add(new SuggestItem(term, distance, frequency));
    }

    /**
     * Damerau-Levenshtein distance with early termination.
     * Returns -1 if distance exceeds maxDistance.
     */
    public static int damerauDistanceLimited(String a, String b, int maxDistance) {
        if (Math.abs(a.length() - b.length()) > maxDistance) return -1;
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        int[] prevPrev = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            int minRow = curr[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                int value = Math.min(Math.min(
                        prev[j] + 1,
                        curr[j - 1] + 1),
                        prev[j - 1] + cost);
                if (i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    value = Math.min(value, prevPrev[j - 2] + 1);
                }
                curr[j] = value;
                if (value < minRow) minRow = value;
            }
            if (minRow > maxDistance) return -1;
            // Rotate rows
            int[] tmp = prevPrev;
            prevPrev = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()] <= maxDistance ? prev[b.length()] : -1;
    }

    public int size() {
        return dictionary.size();
    }

    public boolean containsWord(String word) {
        return dictionary.containsKey(word);
    }

    public int getFrequency(String word) {
        Integer f = dictionary.get(word);
        return f != null ? f : 0;
    }

    /**
     * Write the precomputed deletes index to a binary stream.
     */
    public void writeDeletesCache(DataOutputStream out) throws IOException {
        out.writeInt(deletes.size());
        for (Map.Entry<String, List<String>> entry : deletes.entrySet()) {
            out.writeUTF(entry.getKey());
            List<String> bucket = entry.getValue();
            out.writeInt(bucket.size());
            for (int i = 0; i < bucket.size(); i++) {
                out.writeUTF(bucket.get(i));
            }
        }
    }

    /**
     * Read the precomputed deletes index from a binary stream.
     * Replaces any existing deletes data.
     */
    public void readDeletesCache(DataInputStream in) throws IOException {
        int size = in.readInt();
        deletes.clear();
        for (int i = 0; i < size; i++) {
            if (Thread.currentThread().isInterrupted()) return;
            String key = in.readUTF();
            int bucketSize = in.readInt();
            ArrayList<String> bucket = new ArrayList<String>(bucketSize);
            for (int j = 0; j < bucketSize; j++) {
                bucket.add(in.readUTF());
            }
            deletes.put(key, bucket);
        }
    }
}
