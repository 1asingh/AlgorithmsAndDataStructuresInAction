package org.mlarocca.containers.trie;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class Trie implements StringTree {

    TrieNode root;

    /** Creates an empty trie.     *
     */
    public Trie() {
        root = new TrieNode();
    }

    @Override
    public boolean add(String element) {
        return root.add(element) != null;
    }

    @Override
    public boolean remove(String element) {
        return root.remove(element);
    }

    @Override
    public void clear() {
        // Let the garbage collector do the heavy lifting
        this.root = new TrieNode();
    }

    @Override
    public Optional<String> min() {
        return root.min("");
    }

    @Override
    public Optional<String> max() {
        return root.max("");
    }

    @Override
    public Optional<String> search(String element) {
        return Optional.ofNullable(root.getNodeFor(element)).map(n -> n.storesKey ? element : null);
    }

    @Override
    public boolean isEmpty() {
        return this.size() == 0;
    }

    @Override
    public int size() {
        return root.size();
    }

    @Override
    public int height() {
        return root.height();
    }

    @Override
    public Optional<String> longestPrefixOf(String prefix) {
        return Optional.ofNullable(root.longestPrefixOf(prefix));
    }

    @Override
    public Iterable<String> keysWithPrefix(String prefix) {
        return root.keysWithPrefix(prefix);
    }

    @Override
    public Iterable<String> keys() {
        return root.keys();
    }

    /**
     * Internal class TrieNode models a single node in the trie.
     */
    private class TrieNode {

        Map<Character, TrieNode> children;
        boolean storesKey;

        /**
         * Constructs a new node, and all its descendants needed o store the key passed.
         *
         * @param key A string to be stored.
         * @param charIndex The index of the next character in the string to be stored in the trie. We use this
         *                  parameters to index string key, so that we don't have to create new strings at each call
         *                  (which would be quite expensive). Instead of splitting the argument into head and tails,
         *                  we pass a reference to the original string, and only increase the index.
         */
        private TrieNode(String key, int charIndex) {
            children = new HashMap<>();
            if (charIndex >= key.length()) {
                storesKey = true;
            } else {
                storesKey = false;
                Character character = key.charAt(charIndex);
                children.put(character, new TrieNode(key, charIndex + 1));
            }
        }

        public TrieNode(String key) {
            this(key, 0);
        }

        public TrieNode() {
            children = new HashMap<>();
            storesKey = false;
        }

        public TrieNode add(String key) {
            return this.add(key, 0);
        }

        private TrieNode add(String key, int charIndex) {
            if (charIndex < key.length()) {
                Character character = key.charAt(charIndex);
                if (children.containsKey(character)) {
                    return children.get(character).add(key, charIndex + 1);
                } else {
                    children.put(character, new TrieNode(key, charIndex + 1));
                    return this;
                }
            } else if (charIndex == key.length()) {
                if (this.storesKey) {
                    return null;
                } else {
                    this.storesKey = true;
                    return this;
                }
            } else {
                throw new IllegalArgumentException("CharIndex out of bound " + charIndex + ", " + key);
            }
        }

        public TrieNode getNodeFor(String key) {
            return getNodeFor(key, 0);
        }

        private TrieNode getNodeFor(String key, int charIndex) {
            if (charIndex > key.length()) {
                return null;
            } else if (charIndex == key.length()) {
                return this;
            } else {
                Character character = key.charAt(charIndex);
                if (children.containsKey(character)) {
                    return children.get(character).getNodeFor(key, charIndex + 1);
                } else {
                    return null;
                }
            }
        }

        public boolean remove(String key) {
            return this.remove(key, 0, new AtomicBoolean(false));
        }

        /**
         *
         * @param key The key to be removed from the trie.
         * @param charIndex The index of he next character to check in the input string.
         * @param purge This is set to true if we need to purge the path traversed while deleting a key.
         *              It may happen, in fact, that a node has only one child, containing a single key: if we delete
         *              that key, there is no point in keeping the branch (which can be arbitrarily long) now that
         *              it's empty.
         * @return true iff the argument was successfully deleted from the trie.
         */
        private boolean remove(String key, int charIndex, AtomicBoolean purge) {
            if (charIndex > key.length()) {
                return false;
            } else if (charIndex == key.length()) {
                if (storesKey) {
                    storesKey = false;
                    if (children.isEmpty()) {
                        purge.set(true);
                    }
                    return true;
                } else {
                    return false;
                }
            } else {
                Character character = key.charAt(charIndex);
                if (children.containsKey(character)) {
                    boolean deleted = children.get(character).remove(key, charIndex + 1, purge);
                    if (deleted && purge.get()) {
                        // If the node was deleted and its subtree has no other key, we can remove it from this node's
                        // branches.
                        children.remove(character);
                        if (!children.isEmpty() || this.storesKey) {
                            // If there are other branches in the tree, we can't purge the trie anymore
                            purge.set(false);
                        }
                    }
                    return deleted;
                } else {
                    return false;
                }
            }
        }

        public int size() {
            // Finds out the number of keys in each sub-tree
            int keysInSubTree = children.values()
                    .parallelStream()
                    .map(node -> node.size())
                    .reduce(0, (a, b) -> a + b);
            return (storesKey ? 1 : 0) + keysInSubTree;
        }

        public int height() {
            // Retrieves the maximum height among all children, or returns 0 for a leaf
            return children.values()
                    .parallelStream()
                    .map(node -> node.height() + 1)
                    .reduce(0, (a, b) -> Math.max(a, b));
        }

        public List<String> keys() {
            List<String> keys = Collections.synchronizedList(new ArrayList<>());
            this.keys("", keys);
            return keys;
        }

        private void keys(String currentPath, List<String> keys) {
            if (this.storesKey) {
                keys.add(currentPath);
            }
            // For each children, we need to start the search, keeping track of the path
            children.entrySet()
                    .parallelStream()
                    .forEach(entry -> entry.getValue().keys(currentPath + entry.getKey(), keys));
        }

        private Optional<String> min(String path) {
            if (storesKey) {
                // shorter strings are always lexicographically smaller
                return Optional.of(path);
            } else {
                // Assumes that we do purge the trie when we remove a key
                return children.keySet().stream().min(Character::compareTo).flatMap(c -> children.get(c).min(path + c));
            }
        }

        private Optional<String> max(String path) {
            // Assumes that we do purge the trie when we remove a key
            Optional<String> maxInSubtree = children.keySet().stream().max(Character::compareTo)
                    .flatMap(c -> children.get(c).max(path + c));

            // longer strings are always lexicographically larger (so if we found something in the sub-tree...)
            return maxInSubtree.or(() -> storesKey ? Optional.of(path) : Optional.empty());
        }

        private Iterable<String> keysWithPrefix(String prefix) {
            TrieNode node = this.getNodeFor(prefix);

            return node == null
                    ? new HashSet<>()
                    : node.keys().stream().map(s -> prefix + s).collect(Collectors.toSet());
        }

        private String longestPrefixOf(String prefix) {
            return this.longestPrefixOf(prefix, 0);
        }

        private String longestPrefixOf(String prefix, int charIndex) {
            if (charIndex == prefix.length()) {
                return storesKey ? prefix : null;
            } else {
                char next = prefix.charAt(charIndex);
                String longestFound = null;
                // Depth first search: we are looking for the longest common prefix
                if (children.containsKey(next)) {
                    longestFound = children.get(next).longestPrefixOf(prefix, charIndex + 1);
                }

                if (storesKey && longestFound == null) {
                    return prefix.substring(0, charIndex);
                } else {
                    return longestFound;
                }
            }
        }
    }
}
