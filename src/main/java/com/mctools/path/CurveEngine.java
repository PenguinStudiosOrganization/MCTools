package com.mctools.path;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Curve interpolation engine for the MCT Path Tool system.
 *
 * <p>Supports Catmull-Rom and Bézier interpolation of control points
 * into a smooth sampled path at configurable resolution.</p>
 *
 * <p>The sampled path is a list of {@link Vector} points that Road and Bridge
 * generators consume to place blocks.</p>
 */
public class CurveEngine {

    /**
     * Samples a smooth curve through the given control points.
     *
     * @param controlPoints Ordered list of control point locations (minimum 2)
     * @param resolution    Distance in blocks between sampled points (e.g. 0.5)
     * @param algorithm     "catmullrom" or "bezier"
     * @return List of sampled points along the curve
     */
    public List<Vector> sampleCurve(List<Location> controlPoints, double resolution, String algorithm) {
        if (controlPoints.size() < 2) {
            return new ArrayList<>();
        }

        // For exactly 2 points, just do linear interpolation regardless of algorithm
        if (controlPoints.size() == 2) {
            return sampleLinear(controlPoints.get(0).toVector(), controlPoints.get(1).toVector(), resolution);
        }

        if ("bezier".equalsIgnoreCase(algorithm)) {
            return sampleBezier(controlPoints, resolution);
        } else {
            return sampleCatmullRom(controlPoints, resolution);
        }
    }

    /**
     * Samples a curve and returns Locations in the given world.
     */
    public List<Location> sampleCurveLocations(List<Location> controlPoints, double resolution,
                                                String algorithm, World world) {
        List<Vector> vectors = sampleCurve(controlPoints, resolution, algorithm);
        List<Location> locations = new ArrayList<>(vectors.size());
        for (Vector v : vectors) {
            locations.add(new Location(world, v.getX(), v.getY(), v.getZ()));
        }
        return locations;
    }

    // ── Linear interpolation (2 points) ──

    private List<Vector> sampleLinear(Vector a, Vector b, double resolution) {
        List<Vector> result = new ArrayList<>();
        double dist = a.distance(b);
        int steps = Math.max(1, (int) Math.ceil(dist / resolution));

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            result.add(lerp(a, b, t));
        }
        return result;
    }

    // ── Catmull-Rom spline ──

    private List<Vector> sampleCatmullRom(List<Location> points, double resolution) {
        List<Vector> result = new ArrayList<>();
        int n = points.size();

        for (int i = 0; i < n - 1; i++) {
            Vector p0 = getControlPoint(points, i - 1);
            Vector p1 = points.get(i).toVector();
            Vector p2 = points.get(i + 1).toVector();
            Vector p3 = getControlPoint(points, i + 2);

            double segmentDist = p1.distance(p2);
            int steps = Math.max(1, (int) Math.ceil(segmentDist / resolution));

            for (int s = 0; s < steps; s++) {
                double t = (double) s / steps;
                result.add(catmullRomPoint(p0, p1, p2, p3, t));
            }
        }

        // Add the last point
        result.add(points.get(n - 1).toVector());
        return result;
    }

    /**
     * Gets a control point, extrapolating for out-of-bounds indices.
     */
    private Vector getControlPoint(List<Location> points, int index) {
        if (index < 0) {
            // Extrapolate before first point
            Vector first = points.get(0).toVector();
            Vector second = points.get(1).toVector();
            return first.clone().subtract(second.clone().subtract(first));
        }
        if (index >= points.size()) {
            // Extrapolate after last point
            int last = points.size() - 1;
            Vector lastVec = points.get(last).toVector();
            Vector prevVec = points.get(last - 1).toVector();
            return lastVec.clone().add(lastVec.clone().subtract(prevVec));
        }
        return points.get(index).toVector();
    }

    /**
     * Evaluates a Catmull-Rom spline at parameter t between p1 and p2.
     */
    private Vector catmullRomPoint(Vector p0, Vector p1, Vector p2, Vector p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;

        double x = 0.5 * ((2 * p1.getX()) +
                (-p0.getX() + p2.getX()) * t +
                (2 * p0.getX() - 5 * p1.getX() + 4 * p2.getX() - p3.getX()) * t2 +
                (-p0.getX() + 3 * p1.getX() - 3 * p2.getX() + p3.getX()) * t3);

        double y = 0.5 * ((2 * p1.getY()) +
                (-p0.getY() + p2.getY()) * t +
                (2 * p0.getY() - 5 * p1.getY() + 4 * p2.getY() - p3.getY()) * t2 +
                (-p0.getY() + 3 * p1.getY() - 3 * p2.getY() + p3.getY()) * t3);

        double z = 0.5 * ((2 * p1.getZ()) +
                (-p0.getZ() + p2.getZ()) * t +
                (2 * p0.getZ() - 5 * p1.getZ() + 4 * p2.getZ() - p3.getZ()) * t2 +
                (-p0.getZ() + 3 * p1.getZ() - 3 * p2.getZ() + p3.getZ()) * t3);

        return new Vector(x, y, z);
    }

    // ── Bézier curve (composite quadratic) ──

    private List<Vector> sampleBezier(List<Location> points, double resolution) {
        List<Vector> result = new ArrayList<>();
        int n = points.size();

        if (n == 3) {
            // Single quadratic Bézier
            Vector p0 = points.get(0).toVector();
            Vector p1 = points.get(1).toVector();
            Vector p2 = points.get(2).toVector();

            double totalDist = p0.distance(p1) + p1.distance(p2);
            int steps = Math.max(1, (int) Math.ceil(totalDist / resolution));

            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps;
                result.add(quadraticBezier(p0, p1, p2, t));
            }
        } else {
            // Composite: chain quadratic Bézier segments
            // Use midpoints between consecutive control points as on-curve points
            for (int i = 0; i < n - 2; i++) {
                Vector p0, p1, p2;

                if (i == 0) {
                    p0 = points.get(0).toVector();
                } else {
                    p0 = midpoint(points.get(i).toVector(), points.get(i + 1).toVector());
                }

                p1 = points.get(i + 1).toVector();

                if (i == n - 3) {
                    p2 = points.get(n - 1).toVector();
                } else {
                    p2 = midpoint(points.get(i + 1).toVector(), points.get(i + 2).toVector());
                }

                double segDist = p0.distance(p1) + p1.distance(p2);
                int steps = Math.max(1, (int) Math.ceil(segDist / resolution));

                int startS = (i == 0) ? 0 : 1; // avoid duplicate points at segment joins
                for (int s = startS; s <= steps; s++) {
                    double t = (double) s / steps;
                    result.add(quadraticBezier(p0, p1, p2, t));
                }
            }
        }

        return result;
    }

    private Vector quadraticBezier(Vector p0, Vector p1, Vector p2, double t) {
        double u = 1 - t;
        double x = u * u * p0.getX() + 2 * u * t * p1.getX() + t * t * p2.getX();
        double y = u * u * p0.getY() + 2 * u * t * p1.getY() + t * t * p2.getY();
        double z = u * u * p0.getZ() + 2 * u * t * p1.getZ() + t * t * p2.getZ();
        return new Vector(x, y, z);
    }

    // ── Utility ──

    private Vector lerp(Vector a, Vector b, double t) {
        return new Vector(
                a.getX() + (b.getX() - a.getX()) * t,
                a.getY() + (b.getY() - a.getY()) * t,
                a.getZ() + (b.getZ() - a.getZ()) * t
        );
    }

    private Vector midpoint(Vector a, Vector b) {
        return lerp(a, b, 0.5);
    }

    /**
     * Computes the tangent (forward direction) at a given index in the sampled path.
     * Returns a normalized vector.
     */
    public static Vector getTangent(List<Vector> sampledPath, int index) {
        if (sampledPath.size() < 2) return new Vector(1, 0, 0);

        Vector before, after;
        if (index <= 0) {
            before = sampledPath.get(0);
            after = sampledPath.get(1);
        } else if (index >= sampledPath.size() - 1) {
            before = sampledPath.get(sampledPath.size() - 2);
            after = sampledPath.get(sampledPath.size() - 1);
        } else {
            before = sampledPath.get(index - 1);
            after = sampledPath.get(index + 1);
        }

        Vector tangent = after.clone().subtract(before);
        if (tangent.lengthSquared() < 0.0001) {
            return new Vector(1, 0, 0);
        }
        return tangent.normalize();
    }

    /**
     * Computes the perpendicular (right) vector on the XZ plane for a given tangent.
     * This is used to expand the path laterally for road/bridge width.
     */
    public static Vector getPerpendicular(Vector tangent) {
        // Rotate tangent 90 degrees on XZ plane: (x, y, z) -> (z, 0, -x)
        return new Vector(tangent.getZ(), 0, -tangent.getX()).normalize();
    }
}
