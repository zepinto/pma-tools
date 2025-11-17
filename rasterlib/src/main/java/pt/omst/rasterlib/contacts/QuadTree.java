//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.contacts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import lombok.Getter;

/**
 * A generic QuadTree implementation for spatial indexing of objects.
 * Objects are stored by key and must implement the Locatable interface to provide spatial coordinates.
 *
 * @param <K> the type of key used to identify objects
 * @param <V> the type of value stored in the tree (must be Locatable)
 */
public class QuadTree<K, V extends QuadTree.Locatable<V>> {

    /**
     * Interface for objects that can be stored in the QuadTree.
     * Objects must provide latitude and longitude coordinates.
     */
    public interface Locatable<V> extends Comparable<V> {
        /**
         * @return the latitude in degrees
         */
        double getLatitude();
        
        /**
         * @return the longitude in degrees
         */
        double getLongitude();
    }

    /**
     * Represents a rectangular region in geographic coordinates.
     */
    @Getter
    public static class Region {
        private final double minLat;
        private final double maxLat;
        private final double minLon;
        private final double maxLon;


        public Region(double minLat, double maxLat, double minLon, double maxLon) {
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.minLon = minLon;
            this.maxLon = maxLon;
        }

        public Region(double[] bbox) {
            this.minLat = bbox[0];
            this.maxLat = bbox[2];
            this.minLon = bbox[1];
            this.maxLon = bbox[3];
        }

        /**
         * Checks if a point is contained within this region.
         */
        public boolean contains(double lat, double lon) {
            return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
        }

        /**
         * Checks if this region intersects with another region.
         */
        public boolean intersects(Region other) {
            return !(other.maxLat < minLat || other.minLat > maxLat ||
                    other.maxLon < minLon || other.minLon > maxLon);
        }

        /**
         * Returns the center latitude of this region.
         */
        public double getCenterLat() {
            return (minLat + maxLat) / 2.0;
        }

        /**
         * Returns the center longitude of this region.
         */
        public double getCenterLon() {
            return (minLon + maxLon) / 2.0;
        }
    }

    private static class Entry<K, V> {
        final K key;
        final V value;

        Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private static class Node<K, V extends Locatable> {
        private final Region bounds;
        private final int capacity;
        private final int maxDepth;
        private final int depth;
        private final List<Entry<K, V>> entries;
        private Node<K, V>[] children;
        private boolean divided;

        @SuppressWarnings("unchecked")
        Node(Region bounds, int capacity, int maxDepth, int depth) {
            this.bounds = bounds;
            this.capacity = capacity;
            this.maxDepth = maxDepth;
            this.depth = depth;
            this.entries = new ArrayList<>();
            this.children = null;
            this.divided = false;
        }

        boolean insert(Entry<K, V> entry) {
            if (!bounds.contains(entry.value.getLatitude(), entry.value.getLongitude())) {
                return false;
            }

            if (entries.size() < capacity || depth >= maxDepth) {
                entries.add(entry);
                return true;
            }

            if (!divided) {
                subdivide();
            }

            for (Node<K, V> child : children) {
                if (child.insert(entry)) {
                    return true;
                }
            }

            return false;
        }

        @SuppressWarnings("unchecked")
        private void subdivide() {
            double centerLat = bounds.getCenterLat();
            double centerLon = bounds.getCenterLon();

            children = new Node[4];
            // NW quadrant
            children[0] = new Node<>(
                new Region(centerLat, bounds.maxLat, bounds.minLon, centerLon),
                capacity, maxDepth, depth + 1
            );
            // NE quadrant
            children[1] = new Node<>(
                new Region(centerLat, bounds.maxLat, centerLon, bounds.maxLon),
                capacity, maxDepth, depth + 1
            );
            // SW quadrant
            children[2] = new Node<>(
                new Region(bounds.minLat, centerLat, bounds.minLon, centerLon),
                capacity, maxDepth, depth + 1
            );
            // SE quadrant
            children[3] = new Node<>(
                new Region(bounds.minLat, centerLat, centerLon, bounds.maxLon),
                capacity, maxDepth, depth + 1
            );

            divided = true;

            // Re-insert existing entries into children
            List<Entry<K, V>> entriesToMove = new ArrayList<>(entries);
            entries.clear();
            for (Entry<K, V> entry : entriesToMove) {
                boolean inserted = false;
                for (Node<K, V> child : children) {
                    if (child.insert(entry)) {
                        inserted = true;
                        break;
                    }
                }
                if (!inserted) {
                    entries.add(entry);
                }
            }
        }

        void query(Region range, List<Entry<K, V>> found) {
            if (!bounds.intersects(range)) {
                return;
            }

            for (Entry<K, V> entry : entries) {
                if (range.contains(entry.value.getLatitude(), entry.value.getLongitude())) {
                    found.add(entry);
                }
            }

            if (divided) {
                for (Node<K, V> child : children) {
                    child.query(range, found);
                }
            }
        }

        boolean remove(K key) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).key.equals(key)) {
                    entries.remove(i);
                    return true;
                }
            }

            if (divided) {
                for (Node<K, V> child : children) {
                    if (child.remove(key)) {
                        return true;
                    }
                }
            }

            return false;
        }

        Entry<K, V> find(K key) {
            for (Entry<K, V> entry : entries) {
                if (entry.key.equals(key)) {
                    return entry;
                }
            }

            if (divided) {
                for (Node<K, V> child : children) {
                    Entry<K, V> result = child.find(key);
                    if (result != null) {
                        return result;
                    }
                }
            }

            return null;
        }

        void collectAll(List<Entry<K, V>> result) {
            result.addAll(entries);
            if (divided) {
                for (Node<K, V> child : children) {
                    child.collectAll(result);
                }
            }
        }
    }

    private final Node<K, V> root;
    private final Map<K, V> keyIndex;
    private final int capacity;
    private final int maxDepth;

    /**
     * Creates a new QuadTree with default capacity (4) and max depth (8).
     *
     * @param bounds the geographic bounds of the tree
     */
    public QuadTree(Region bounds) {
        this(bounds, 4, 8);
    }

    public QuadTree() {
        this(new Region(-90, 90, -180, 180), 4, 8);
    }

    /**
     * Creates a new QuadTree with specified capacity and max depth.
     *
     * @param bounds the geographic bounds of the tree
     * @param capacity the maximum number of items per node before subdivision
     * @param maxDepth the maximum depth of the tree
     */
    public QuadTree(Region bounds, int capacity, int maxDepth) {
        this.capacity = capacity;
        this.maxDepth = maxDepth;
        this.root = new Node<>(bounds, capacity, maxDepth, 0);
        this.keyIndex = new HashMap<>();
    }

    /**
     * Adds an object to the QuadTree.
     *
     * @param key the unique key for this object
     * @param value the object to add
     * @return true if the object was added successfully, false otherwise
     */
    public boolean add(K key, V value) {
        if (keyIndex.containsKey(key)) {
            return false; // Key already exists
        }

        Entry<K, V> entry = new Entry<>(key, value);
        if (root.insert(entry)) {
            keyIndex.put(key, value);
            return true;
        }
        return false;
    }

    public boolean update(K key, V value) {
        // Remove the old entry
        root.remove(key);
        keyIndex.remove(key);

        // Add the new entry
        Entry<K, V> entry = new Entry<>(key, value);
        if (root.insert(entry)) {
            keyIndex.put(key, value);
            return true;
        }
        return false;
    }

    /**
     * Removes an object from the QuadTree by its key.
     *
     * @param key the key of the object to remove
     * @return the removed object, or null if not found
     */
    public V remove(K key) {
        V value = keyIndex.remove(key);
        if (value != null) {
            root.remove(key);
        }
        return value;
    }

    /**
     * Gets an object from the QuadTree by its key.
     *
     * @param key the key of the object to retrieve
     * @return the object, or null if not found
     */
    public V get(K key) {
        return keyIndex.get(key);
    }

    /**
     * Checks if the QuadTree contains an object with the given key.
     *
     * @param key the key to check
     * @return true if the key exists, false otherwise
     */
    public boolean containsKey(K key) {
        return keyIndex.containsKey(key);
    }

    public List<V> query(Region region) {
        List<Entry<K, V>> entries = new ArrayList<>();
        root.query(region, entries);
        List<V> results = new ArrayList<>();
        for (Entry<K, V> entry : entries) {
            results.add(entry.value);
        }
        Collections.sort(results);
        return results;
    }
    /**
     * Queries the QuadTree for all objects within a given region.
     *
     * @param region the region to query
     * @return a list of all values within the region
     */
    public List<V> query(Region region, Predicate<V>... filters) {
        List<Entry<K, V>> entries = new ArrayList<>();
        root.query(region, entries);
        List<V> results = new ArrayList<>();
        for (Entry<K, V> entry : entries) {
            boolean matches = true;
            for (Predicate<V> filter : filters) {
                if (!filter.test(entry.value)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                results.add(entry.value);
            }
        }
        Collections.sort(results);
        return results;
    }    

    /**
     * Returns all objects in the QuadTree.
     *
     * @return a list of all values in the tree
     */
    public List<V> getAll() {
        List<Entry<K, V>> entries = new ArrayList<>();
        root.collectAll(entries);
        List<V> results = new ArrayList<>();
        for (Entry<K, V> entry : entries) {
            results.add(entry.value);
        }
        return results;
    }

    /**
     * Returns the number of objects in the QuadTree.
     *
     * @return the size of the tree
     */
    public int size() {
        return keyIndex.size();
    }

    /**
     * Checks if the QuadTree is empty.
     *
     * @return true if the tree is empty, false otherwise
     */
    public boolean isEmpty() {
        return keyIndex.isEmpty();
    }

    /**
     * Clears all objects from the QuadTree.
     */
    public void clear() {
        keyIndex.clear();
        // Recreate the root node
        Region bounds = root.bounds;
        root.entries.clear();
        root.divided = false;
        root.children = null;
    }
}
