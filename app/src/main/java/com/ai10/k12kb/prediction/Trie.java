package com.ai10.k12kb.prediction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Memory-efficient Trie using sorted-array children.
 * Stores words with frequencies for prefix lookup.
 */
public class Trie {

    public static class TrieResult {
        public final String word;
        public final int frequency;

        public TrieResult(String word, int frequency) {
            this.word = word;
            this.frequency = frequency;
        }
    }

    private static class Node {
        char[] childChars = new char[0];
        Node[] childNodes = new Node[0];
        int frequency = -1; // -1 means not a word end
        String word;        // stored only at word-end nodes

        int findChild(char c) {
            return Arrays.binarySearch(childChars, c);
        }

        Node getOrCreateChild(char c) {
            int idx = Arrays.binarySearch(childChars, c);
            if (idx >= 0) return childNodes[idx];

            // Insert in sorted order
            int insertPos = -(idx + 1);
            char[] newChars = new char[childChars.length + 1];
            Node[] newNodes = new Node[childNodes.length + 1];

            System.arraycopy(childChars, 0, newChars, 0, insertPos);
            System.arraycopy(childNodes, 0, newNodes, 0, insertPos);

            newChars[insertPos] = c;
            Node child = new Node();
            newNodes[insertPos] = child;

            System.arraycopy(childChars, insertPos, newChars, insertPos + 1, childChars.length - insertPos);
            System.arraycopy(childNodes, insertPos, newNodes, insertPos + 1, childNodes.length - insertPos);

            childChars = newChars;
            childNodes = newNodes;
            return child;
        }
    }

    private final Node root = new Node();
    private int size = 0;

    public void insert(String word, int frequency) {
        if (word == null || word.isEmpty()) return;
        Node node = root;
        for (int i = 0; i < word.length(); i++) {
            node = node.getOrCreateChild(word.charAt(i));
        }
        if (node.frequency < 0) size++;
        node.frequency = frequency;
        node.word = word;
    }

    public boolean contains(String word) {
        Node node = findNode(word);
        return node != null && node.frequency >= 0;
    }

    public int getFrequency(String word) {
        Node node = findNode(word);
        return (node != null && node.frequency >= 0) ? node.frequency : -1;
    }

    /**
     * Find all words with given prefix, sorted by frequency descending.
     */
    public List<TrieResult> findByPrefix(String prefix, int maxResults) {
        if (prefix == null || prefix.isEmpty()) return Collections.emptyList();
        Node node = findNode(prefix);
        if (node == null) return Collections.emptyList();

        List<TrieResult> results = new ArrayList<>();
        collectWords(node, results, maxResults * 4); // collect more, then sort+trim

        Collections.sort(results, new Comparator<TrieResult>() {
            public int compare(TrieResult a, TrieResult b) {
                return Integer.compare(b.frequency, a.frequency);
            }
        });

        if (results.size() > maxResults) {
            return new ArrayList<>(results.subList(0, maxResults));
        }
        return results;
    }

    public int size() {
        return size;
    }

    private Node findNode(String key) {
        if (key == null || key.isEmpty()) return null;
        Node node = root;
        for (int i = 0; i < key.length(); i++) {
            int idx = node.findChild(key.charAt(i));
            if (idx < 0) return null;
            node = node.childNodes[idx];
        }
        return node;
    }

    private void collectWords(Node node, List<TrieResult> results, int limit) {
        if (results.size() >= limit) return;
        if (node.frequency >= 0 && node.word != null) {
            results.add(new TrieResult(node.word, node.frequency));
        }
        for (int i = 0; i < node.childChars.length; i++) {
            if (results.size() >= limit) return;
            collectWords(node.childNodes[i], results, limit);
        }
    }
}
