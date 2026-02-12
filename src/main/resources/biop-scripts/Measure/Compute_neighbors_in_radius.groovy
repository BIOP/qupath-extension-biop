
/**
 * This script counts the number of the neighbors for each cell 
 * in a certain distance from its centroid and gives the distancer to
 * its closest neighbor.
 *  
 * author: Rémy Dornier - PTBIOP & Claude.ai
 * date: 2026-02-11
 * version: 1.0.0
 * 
 * Last tested on QuPath 0.6.0
 * 
 */


/**********************
 * VARIABLES TO MODIFY
 *********************/


double radius = 20; // in um


/***********************
 * BEGINNING OF THE SCRIPT
 ***********************/


// get only detections
def detections = getDetectionObjects()

if(detections.isEmpty()) {
   Logger.warn("No detections on the current image ; nothing to do")
   return
}

def px = getCurrentServer().getPixelCalibration().getAveragedPixelSize()

// extract centroid from detections and store them in a map
println("Extracting centroids from " + detections.size() + " cells...");
long startTime = System.currentTimeMillis();
def pointObjectMap = detections.parallelStream().map(pathObject -> {
    def tempMap = new HashMap<>()
    var roi = PathObjectTools.getROI(pathObject, true);
    double x = roi.getCentroidX() * px
    double y = roi.getCentroidY() * px
    var coord = new Point(x, y);
    tempMap.put(coord, pathObject)
    return tempMap
}).map(Map::entrySet).flatMap(Set::stream).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
long buildTime = System.currentTimeMillis() - startTime;
println("Extraction done in " + buildTime + " ms");

// get all centroids
List<Point> points = new ArrayList<>(pointObjectMap.keySet()) 

// Build KD-Tree
println("Building KD-Tree with " + points.size() + " points...");
startTime = System.currentTimeMillis();
KDTree kdTree = new KDTree();
kdTree.build(points);
buildTime = System.currentTimeMillis() - startTime;
println("KD-Tree built in " + buildTime + " ms");

// compute the number of neighbors for each detection
println("Computing the number of neighbors and distance...");
startTime = System.currentTimeMillis();
pointObjectMap.each{ point, detection ->
    List<Point> neighborsOpt = kdTree.findNeighborsInRadius(point, radius);
    detection.measurements.put("NN in radius "+radius+" "+GeneralTools.micrometerSymbol(), neighborsOpt.size())   
    
}
long kdTreeTime = System.currentTimeMillis() - startTime;
println("Found neighbors within radius " + radius +"um in " + kdTreeTime + " ms");


// Find k nearest neighbors
println("nearest neighbors:");
startTime = System.currentTimeMillis();
pointObjectMap.each{ point, detection ->
    List<Point> nearest = kdTree.findKNearestNeighbors(point, 1);
    for (int i = 0; i < nearest.size(); i++) {
        Point p = nearest.get(i);
        double dist = KDTree.calculateDistance(point, p);
        detection.measurements.put("Closest Neighbor "+GeneralTools.micrometerSymbol(), Math.sqrt(dist))
    }
}
kdTreeTime = System.currentTimeMillis() - startTime;
println("Found closest neighbors in " + kdTreeTime + " ms");

return


/**********************
 * HELPERS
 *********************/


class Point {
    double x, y;

    Point(double x, double y) {
        this.x = x;
        this.y = y;
    }
        
    double get(int dimension) {
        return dimension == 0 ? x : y;
    }
}
  
  
class Node {
    Point point;
    Node left, right;
    
    Node(Point point) {
        this.point = point;
    }
}

class NeighborDistance {
    Point point;
    double distance;
    
    NeighborDistance(Point point, double distance) {
        this.point = point;
        this.distance = distance;
    }
    
    @Override
    public String toString() {
        return point + " at distance " + String.format("%.2f", distance);
    }
}

// Graph class to store point dependencies
class KDTree {
    private Node root;
    private int size;
    private static final int K = 2; // 2D points
    
    public KDTree() {
        this.root = null;
        this.size = 0;
    }
    
    /**
     * Build KD-Tree from list of points
     * Time Complexity: O(n log n)
     */
    public void build(List<Point> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        
        List<Point> pointsCopy = new ArrayList<>(points);
        root = buildRecursive(pointsCopy, 0);
        size = points.size();
    }
    
    private Node buildRecursive(List<Point> points, int depth) {
        if (points.isEmpty()) {
            return null;
        }
        
        int axis = depth % K;
        
        // Sort points by current dimension and find median
        points.sort(Comparator.comparingDouble(p -> p.get(axis)));
        int medianIndex = points.size() / 2;
        
        // For even number of points, use the lower median for better balance
        if (points.size() % 2 == 0 && medianIndex > 0) {
            medianIndex--;
        }
        
        Node node = new Node(points.get(medianIndex));
        
        // Recursively build left and right subtrees
        node.left = buildRecursive(
            new ArrayList<>(points.subList(0, medianIndex)), 
            depth + 1
        );
        node.right = buildRecursive(
            new ArrayList<>(points.subList(medianIndex + 1, points.size())), 
            depth + 1
        );
        
        return node;
    }
    
    /**
     * Find all neighbors within radius
     * Average Time Complexity: O(√n + m) for 2D, where m is number of points found
     * Worst case: O(n) when all points are within radius
     */
    public List<Point> findNeighborsInRadius(Point center, double radius) {
        List<Point> neighbors = new ArrayList<>();
        double radiusSq = radius * radius
        if (root == null) {
            return neighbors;
        }
        
        findNeighborsInRadiusRecursive(root, center, radiusSq, 0, neighbors);
        return neighbors;
    }
    
    private void findNeighborsInRadiusRecursive(Node node, Point center, 
                                                double radiusSq, int depth, 
                                                List<Point> neighbors) {
        if (node == null) {
            return;
        }
        
        // Check if current node's point is within radius
        double distance = calculateDistance(node.point, center);
        if (distance <= radiusSq && !node.point.equals(center)) {
            neighbors.add(node.point);
        }
        
        // Determine which dimension we're splitting on
        int axis = depth % K;
        double diff = center.get(axis) - node.point.get(axis);
        
        // Determine which side of the splitting plane to search first
        Node first = diff < 0 ? node.left : node.right;
        Node second = diff < 0 ? node.right : node.left;
        
        // Search the side of the splitting plane that contains the center point
        findNeighborsInRadiusRecursive(first, center, radiusSq, depth + 1, neighbors);
        
        // Only search the other side if the splitting plane is within radius
        // This is the key optimization that makes KD-tree efficient
        if ((diff * diff) <= radiusSq) {
            findNeighborsInRadiusRecursive(second, center, radiusSq, depth + 1, neighbors);
        }
    }

    
    /**
     * Find k nearest neighbors
     * Time Complexity: O(log n) average case, O(n) worst case
     */
    public List<Point> findKNearestNeighbors(Point center, int k) {
        if (k <= 0 || root == null) {
            return new ArrayList<>();
        }
        
        PriorityQueue<NeighborDistance> maxHeap = new PriorityQueue<>(
            (a, b) -> Double.compare(b.distance, a.distance)
        );
        
        findKNearestRecursive(root, center, k, 0, maxHeap);
        
        List<Point> result = new ArrayList<>();
        while (!maxHeap.isEmpty()) {
            result.add(0, maxHeap.poll().point);
        }
        return result;
    }
    
    private void findKNearestRecursive(Node node, Point center, int k, 
                                      int depth, PriorityQueue<NeighborDistance> heap) {
        if (node == null) {
            return;
        }
        
        double distance = calculateDistance(node.point, center);
        
        if (!node.point.equals(center)) {
            if (heap.size() < k) {
                heap.offer(new NeighborDistance(node.point, distance));
            } else if (distance < heap.peek().distance) {
                heap.poll();
                heap.offer(new NeighborDistance(node.point, distance));
            }
        }
        
        int axis = depth % K;
        double diff = center.get(axis) - node.point.get(axis);
        
        Node first = diff < 0 ? node.left : node.right;
        Node second = diff < 0 ? node.right : node.left;
        
        findKNearestRecursive(first, center, k, depth + 1, heap);
        
        // Only explore the other branch if necessary
        if (heap.size() < k || (diff * diff) < heap.peek().distance) {
            findKNearestRecursive(second, center, k, depth + 1, heap);
        }
    }
    
        
    /**
     * Insert a single point (for dynamic updates)
     * Time Complexity: O(log n) average case
     */
    public void insert(Point point) {
        root = insertRecursive(root, point, 0);
        size++;
    }
    
    private Node insertRecursive(Node node, Point point, int depth) {
        if (node == null) {
            return new Node(point);
        }
        
        int axis = depth % K;
        
        if (point.get(axis) < node.point.get(axis)) {
            node.left = insertRecursive(node.left, point, depth + 1);
        } else {
            node.right = insertRecursive(node.right, point, depth + 1);
        }
        
        return node;
    }
    
    public static double calculateDistance(Point p1, Point p2) {
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        return dx * dx + dy * dy;
    }
}


/**********************
 * IMPORTS
 *********************/
 
 
import java.util.*;
import java.util.stream.*