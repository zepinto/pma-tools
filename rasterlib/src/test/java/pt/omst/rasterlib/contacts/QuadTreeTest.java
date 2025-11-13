//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.contacts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the QuadTree class.
 */
class QuadTreeTest {

    /**
     * Simple test object that implements Locatable.
     */
    static class TestPoint implements QuadTree.Locatable {
        private final double lat;
        private final double lon;
        private final String name;

        TestPoint(double lat, double lon, String name) {
            this.lat = lat;
            this.lon = lon;
            this.name = name;
        }

        @Override
        public double getLatitude() {
            return lat;
        }

        @Override
        public double getLongitude() {
            return lon;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name + " (" + lat + ", " + lon + ")";
        }
    }

    private QuadTree<String, TestPoint> quadTree;
    private QuadTree.Region worldBounds;

    @BeforeEach
    void setUp() {
        // Create a QuadTree covering the entire world
        worldBounds = new QuadTree.Region(-90, 90, -180, 180);
        quadTree = new QuadTree<>(worldBounds);
    }

    @Test
    @DisplayName("Test adding and retrieving a single point")
    void testAddAndGet() {
        TestPoint point = new TestPoint(40.0, -8.0, "Point1");
        
        assertTrue(quadTree.add("p1", point));
        assertEquals(1, quadTree.size());
        assertEquals(point, quadTree.get("p1"));
    }

    @Test
    @DisplayName("Test adding duplicate keys")
    void testAddDuplicateKey() {
        TestPoint point1 = new TestPoint(40.0, -8.0, "Point1");
        TestPoint point2 = new TestPoint(41.0, -7.0, "Point2");
        
        assertTrue(quadTree.add("p1", point1));
        assertFalse(quadTree.add("p1", point2)); // Should fail - duplicate key
        assertEquals(1, quadTree.size());
        assertEquals(point1, quadTree.get("p1"));
    }

    @Test
    @DisplayName("Test removing points")
    void testRemove() {
        TestPoint point = new TestPoint(40.0, -8.0, "Point1");
        
        quadTree.add("p1", point);
        assertEquals(1, quadTree.size());
        
        TestPoint removed = quadTree.remove("p1");
        assertEquals(point, removed);
        assertEquals(0, quadTree.size());
        assertNull(quadTree.get("p1"));
    }

    @Test
    @DisplayName("Test removing non-existent key")
    void testRemoveNonExistent() {
        assertNull(quadTree.remove("nonexistent"));
        assertEquals(0, quadTree.size());
    }

    @Test
    @DisplayName("Test containsKey method")
    void testContainsKey() {
        TestPoint point = new TestPoint(40.0, -8.0, "Point1");
        
        assertFalse(quadTree.containsKey("p1"));
        quadTree.add("p1", point);
        assertTrue(quadTree.containsKey("p1"));
        quadTree.remove("p1");
        assertFalse(quadTree.containsKey("p1"));
    }

    @Test
    @DisplayName("Test querying by region")
    void testQueryRegion() {
        // Add points in different locations
        quadTree.add("lisbon", new TestPoint(38.7223, -9.1393, "Lisbon"));
        quadTree.add("porto", new TestPoint(41.1579, -8.6291, "Porto"));
        quadTree.add("paris", new TestPoint(48.8566, 2.3522, "Paris"));
        quadTree.add("london", new TestPoint(51.5074, -0.1278, "London"));
        
        // Query for points in Portugal (approximate)
        QuadTree.Region portugalRegion = new QuadTree.Region(37.0, 42.0, -10.0, -6.0);
        List<TestPoint> results = quadTree.query(portugalRegion);
        
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(p -> p.getName().equals("Lisbon")));
        assertTrue(results.stream().anyMatch(p -> p.getName().equals("Porto")));
    }

    @Test
    @DisplayName("Test querying empty region")
    void testQueryEmptyRegion() {
        quadTree.add("lisbon", new TestPoint(38.7223, -9.1393, "Lisbon"));
        
        // Query a region in the Pacific Ocean (no points)
        QuadTree.Region pacificRegion = new QuadTree.Region(0.0, 10.0, 170.0, 180.0);
        List<TestPoint> results = quadTree.query(pacificRegion);
        
        assertEquals(0, results.size());
    }

    @Test
    @DisplayName("Test querying with all points in region")
    void testQueryAllPoints() {
        quadTree.add("lisbon", new TestPoint(38.7223, -9.1393, "Lisbon"));
        quadTree.add("porto", new TestPoint(41.1579, -8.6291, "Porto"));
        
        // Query the entire world
        List<TestPoint> results = quadTree.query(worldBounds);
        
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("Test getAll method")
    void testGetAll() {
        TestPoint p1 = new TestPoint(40.0, -8.0, "Point1");
        TestPoint p2 = new TestPoint(41.0, -7.0, "Point2");
        TestPoint p3 = new TestPoint(42.0, -6.0, "Point3");
        
        quadTree.add("p1", p1);
        quadTree.add("p2", p2);
        quadTree.add("p3", p3);
        
        List<TestPoint> all = quadTree.getAll();
        assertEquals(3, all.size());
        assertTrue(all.contains(p1));
        assertTrue(all.contains(p2));
        assertTrue(all.contains(p3));
    }

    @Test
    @DisplayName("Test isEmpty method")
    void testIsEmpty() {
        assertTrue(quadTree.isEmpty());
        
        quadTree.add("p1", new TestPoint(40.0, -8.0, "Point1"));
        assertFalse(quadTree.isEmpty());
        
        quadTree.remove("p1");
        assertTrue(quadTree.isEmpty());
    }

    @Test
    @DisplayName("Test clear method")
    void testClear() {
        quadTree.add("p1", new TestPoint(40.0, -8.0, "Point1"));
        quadTree.add("p2", new TestPoint(41.0, -7.0, "Point2"));
        assertEquals(2, quadTree.size());
        
        quadTree.clear();
        assertEquals(0, quadTree.size());
        assertTrue(quadTree.isEmpty());
        assertNull(quadTree.get("p1"));
    }

    @Test
    @DisplayName("Test adding many points to trigger subdivision")
    void testSubdivision() {
        // Add many points in a small region to trigger subdivision
        for (int i = 0; i < 20; i++) {
            double lat = 40.0 + (i * 0.01);
            double lon = -8.0 + (i * 0.01);
            quadTree.add("point" + i, new TestPoint(lat, lon, "Point" + i));
        }
        
        assertEquals(20, quadTree.size());
        
        // Query a region containing all points
        QuadTree.Region region = new QuadTree.Region(39.5, 41.0, -9.0, -7.0);
        List<TestPoint> results = quadTree.query(region);
        
        assertEquals(20, results.size());
    }

    @Test
    @DisplayName("Test adding points outside bounds")
    void testAddOutsideBounds() {
        // Create a small region
        QuadTree.Region smallRegion = new QuadTree.Region(40.0, 41.0, -9.0, -8.0);
        QuadTree<String, TestPoint> smallTree = new QuadTree<>(smallRegion);
        
        TestPoint inside = new TestPoint(40.5, -8.5, "Inside");
        TestPoint outside = new TestPoint(45.0, -7.0, "Outside");
        
        assertTrue(smallTree.add("inside", inside));
        assertFalse(smallTree.add("outside", outside)); // Should fail - outside bounds
        
        assertEquals(1, smallTree.size());
    }

    @Test
    @DisplayName("Test Region.contains method")
    void testRegionContains() {
        QuadTree.Region region = new QuadTree.Region(40.0, 41.0, -9.0, -8.0);
        
        assertTrue(region.contains(40.5, -8.5));
        assertTrue(region.contains(40.0, -8.0)); // Boundaries inclusive
        assertTrue(region.contains(41.0, -9.0)); // Boundaries inclusive
        assertFalse(region.contains(39.9, -8.5));
        assertFalse(region.contains(40.5, -9.1));
    }

    @Test
    @DisplayName("Test Region.intersects method")
    void testRegionIntersects() {
        QuadTree.Region region1 = new QuadTree.Region(40.0, 42.0, -9.0, -7.0);
        QuadTree.Region region2 = new QuadTree.Region(41.0, 43.0, -8.0, -6.0); // Overlaps
        QuadTree.Region region3 = new QuadTree.Region(43.0, 44.0, -10.0, -9.5); // No overlap
        
        assertTrue(region1.intersects(region2));
        assertTrue(region2.intersects(region1));
        assertFalse(region1.intersects(region3));
        assertFalse(region3.intersects(region1));
    }

    @Test
    @DisplayName("Test Region center calculations")
    void testRegionCenter() {
        QuadTree.Region region = new QuadTree.Region(40.0, 42.0, -10.0, -8.0);
        
        assertEquals(41.0, region.getCenterLat(), 0.0001);
        assertEquals(-9.0, region.getCenterLon(), 0.0001);
    }

    @Test
    @DisplayName("Test with custom capacity and depth")
    void testCustomCapacityAndDepth() {
        QuadTree<String, TestPoint> customTree = new QuadTree<>(worldBounds, 2, 4);
        
        for (int i = 0; i < 10; i++) {
            customTree.add("point" + i, new TestPoint(40.0 + i * 0.1, -8.0 + i * 0.1, "Point" + i));
        }
        
        assertEquals(10, customTree.size());
        List<TestPoint> all = customTree.getAll();
        assertEquals(10, all.size());
    }

    @Test
    @DisplayName("Test removing and re-adding same key")
    void testRemoveAndReAdd() {
        TestPoint point1 = new TestPoint(40.0, -8.0, "Point1");
        TestPoint point2 = new TestPoint(41.0, -7.0, "Point2");
        
        quadTree.add("p1", point1);
        assertEquals(point1, quadTree.get("p1"));
        
        quadTree.remove("p1");
        assertNull(quadTree.get("p1"));
        
        quadTree.add("p1", point2);
        assertEquals(point2, quadTree.get("p1"));
    }

    @Test
    @DisplayName("Test with points at exact boundaries")
    void testBoundaryPoints() {
        quadTree.add("nw", new TestPoint(90.0, -180.0, "NorthWest"));
        quadTree.add("ne", new TestPoint(90.0, 180.0, "NorthEast"));
        quadTree.add("sw", new TestPoint(-90.0, -180.0, "SouthWest"));
        quadTree.add("se", new TestPoint(-90.0, 180.0, "SouthEast"));
        
        assertEquals(4, quadTree.size());
        
        // All should be retrievable
        assertNotNull(quadTree.get("nw"));
        assertNotNull(quadTree.get("ne"));
        assertNotNull(quadTree.get("sw"));
        assertNotNull(quadTree.get("se"));
    }

    @Test
    @DisplayName("Test stress test with many points")
    void testManyPoints() {
        int numPoints = 1000;
        
        // Add many random points
        for (int i = 0; i < numPoints; i++) {
            double lat = (Math.random() * 180) - 90;  // -90 to 90
            double lon = (Math.random() * 360) - 180; // -180 to 180
            quadTree.add("point" + i, new TestPoint(lat, lon, "Point" + i));
        }
        
        assertEquals(numPoints, quadTree.size());
        
        // Verify all points are retrievable
        for (int i = 0; i < numPoints; i++) {
            assertNotNull(quadTree.get("point" + i));
        }
        
        // Test a query
        QuadTree.Region region = new QuadTree.Region(0.0, 45.0, 0.0, 45.0);
        List<TestPoint> results = quadTree.query(region);
        assertTrue(results.size() > 0);
        assertTrue(results.size() < numPoints);
    }
}
