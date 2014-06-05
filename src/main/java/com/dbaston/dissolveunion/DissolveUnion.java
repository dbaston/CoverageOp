package com.dbaston.dissolveunion;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineSegment;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.index.strtree.STRtree;
import com.vividsolutions.jts.operation.linemerge.LineMerger;
import com.vividsolutions.jts.operation.polygonize.Polygonizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 *
 * @author dbaston
 */
public class DissolveUnion {
    /** getUniqueSegments returns a set of unique LineSegment objects that form
     *  the boundaries of the supplied collection of geometries.
     * @param geoms A collection of Polygons or MultiPolygons.
     * @return A set of unique LineSegment objects
     */
    protected static HashSet<LineSegment> getUniqueSegments(Collection<Geometry> geoms) {
        HashSet<Polygon> geomSet = new HashSet<>(geoms.size());
        HashSet<LineSegment> lines = new HashSet(100*geoms.size(), 0.5F);

        for (Geometry g : geoms) {
            if (!(g instanceof Polygon || g instanceof MultiPolygon)) {
                throw new IllegalArgumentException("Geometries must be Polygons or MultiPolygons");
            }
            
            for (int i = 0; i < g.getNumGeometries(); i++) {
                geomSet.add((Polygon) g.getGeometryN(i));
            }
        }

        for (Polygon p : geomSet) {
            Coordinate[][] rings = new Coordinate[1 + p.getNumInteriorRing()][];
            
            // Populate the array of ring coordinates
            rings[0] = p.getExteriorRing().getCoordinates();
            for (int i = 0; i < p.getNumInteriorRing(); i++) {
                rings[i+1] = p.getInteriorRingN(i).getCoordinates();
            }
            
            // Loop over the aray of ring coordinates, constructing LineSegments
            // and checking them for uniqueness
            for (int i = 0; i < rings.length; i++) {
                for (int j = 0; j < rings[i].length - 1; j++) {
                    LineSegment ls = new LineSegment (rings[i][j], rings[i][j+1]);
                    ls.normalize();
                    
                    if (!lines.remove(ls)) {
                        lines.add(ls);
                    }
                }
            }
        }        
        
        return lines;
    }
    
    /** getMergedLineSegments converts the supplied collection of LineSegment
     *  objects into LineStrings using the supplied GeometryFactory, and 
     *  merges the resultant LineStrings using a LineMerger.  The returned
     *  LineStrings may or may not form closed rings.
     * @param segments
     * @param gfact
     * @return Collection of LineString objects.
     */
    protected static Collection<LineString> getMergedLineSegments(Collection<LineSegment> segments, GeometryFactory gfact) {
        LineMerger lm = new LineMerger();
        for (LineSegment l : segments) {
            lm.add(l.toGeometry(gfact));
        }
        return lm.getMergedLineStrings();    
    }
    
    /** allRingsClosed determines if every one of the supplied LineStrings
     *  forms a closed ring.
     * @param rings
     * @return 
     */
    protected static boolean allRingsClosed(Collection<LineString> rings) {
        for (LineString l : rings) {
            if (!l.isClosed()) {
                return false;
            }
        }
        return true;
    }
    
    /** getRingPolygons converts a supplied collection of LineStrings into
     *  an array of Polygon objects.  The Polygons are constructed as
     *  exterior rings only.  A Polygonizer is used to construct rings
     *  from the input LineString.  If the input LineStrings already form
     *  closed rings, a more efficient approach is used.
     * 
     * @param rings
     * @param inputs
     * @return 
     */
    protected static Polygon[] getRingPolygons(Collection<LineString> rings,
                                             Collection<Geometry> inputs) {
        if (rings.isEmpty()) {
            return new Polygon[0];
        }

        GeometryFactory gfact = rings.iterator().next().getFactory();
        if (allRingsClosed(rings)) {
            Polygon[] polyRings;
            polyRings = new Polygon[rings.size()];

            int i = 0;
            for (LineString l : rings) {
                polyRings[i] =  gfact.createPolygon(l.getCoordinates());
                i++;
            }
            return polyRings;
        } else {
            // Bring in the big guns.
            Polygonizer polygen = new Polygonizer();
            polygen.add(rings);
            Collection<Polygon> polygons = polygen.getPolygons();
            
            STRtree inputIndex = new STRtree();
            for (Geometry g : inputs) {
                inputIndex.insert(g.getEnvelopeInternal(), g);
            }
            
            HashSet<LineString> ringSet = new HashSet<>();
            
            for (Polygon p : polygons) {
                LineString s = p.getExteriorRing();
                
                Point pt = gfact.createPolygon(s.getCoordinates()).getInteriorPoint();
                boolean found = false;
                for (Geometry input : (Collection<Geometry>) inputIndex.query(pt.getEnvelopeInternal())) {
                    if (input.intersects(pt)) {
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    continue;
                }
                
                s.normalize();
                ringSet.add(s);
                
                for (int i = 0; i < p.getNumInteriorRing(); i++) {
                    s = p.getInteriorRingN(i);
                    s.normalize();
                    ringSet.add(s);
                }
            }
            
            Polygon[] polyRings = new Polygon[ringSet.size()];
            {
                int i = 0;
                for (LineString l : ringSet) {
                    polyRings[i] = gfact.createPolygon(l.getCoordinates());
                    i++;
            }
            }
            
            return polyRings;
        }
    }
    
    /** Unions the supplied collection of geometries using a dissolve approach, 
     *  wherein line segments that are shared by more than one input geometry
     *  are removed from the result.  This function will not produce valid
     *  output in cases where the input geometries are invalid, overlapping,
     *  or incorrectly noded.
     * @param geoms
     * @param gfact
     * @return 
     */
    public static Geometry union(Collection<Geometry> geoms, GeometryFactory gfact) {
        // If an empty geometry collection is provided, return an empty geometry
        if (geoms.isEmpty()) {
            return new GeometryFactory().createPoint((Coordinate) null);
        }
        
        // If a geometry factory is not provided, borrow one from the inputs
        if (gfact == null) {
            gfact = geoms.iterator().next().getFactory();
        }
        
        // Get the unique segments and convert them into linesrings, then
        // merge the linestrings.
        Collection<LineString> rings = getMergedLineSegments(getUniqueSegments(geoms), gfact);

        //System.out.println(gfact.buildGeometry(rings));
        
        Polygon[] polys = getRingPolygons(rings, geoms); 
        
        // Sort polygon array in descending order by size of bbox...try to
        // pick up the most holes to start
        Arrays.sort(polys, new Comparator<Geometry>() {
            @Override
            public int compare(Geometry a, Geometry b) {
                return ((Double) b.getEnvelopeInternal().getArea()).compareTo(
                        a.getEnvelopeInternal().getArea());
            }
        });
        
        // Build a spatial index on the rings (represented as polygons)
        STRtree polyIndex = new STRtree();
        for (int i = 0; i < polys.length; i++) {
            polyIndex.insert(polys[i].getEnvelopeInternal(), i);
        }

        // Associate located interior rings with each polygon
        HashMap<Polygon, List<Polygon>> intRings = new HashMap<>();
        // Keep track of which polygons have been identified as an interior
        // ring of some other polygon.
        HashSet<Polygon> intRingSet = new HashSet<>();
                
        // Identify polygons that should be considered interior rings of other polygons
        for (int i = 0; i < polys.length; i++) {
            if (intRingSet.contains(polys[i])) {
                // This polygon has already been identified as an interior ring
                // of something else...skip it.
                continue;
            }
            
            List<Integer> potentialInteriorRings = polyIndex.query(polys[i].getEnvelopeInternal());
            Collections.sort(potentialInteriorRings);
            for (int j : potentialInteriorRings) {
                if (i < j   // only j > i could be an interior ring of i, because an interior
                            // ring cannot have a larger bounding box than the exterior ring
                        && !intRingSet.contains(polys[j])  // make sure this polygon is not already
                                                           // identified as an interior ring of
                                                           // something else.  what would appear to be
                                                           // an interior ring of an interior ring should
                                                           // be considered a separate exterior ring in a 
                                                           // separate polygon
                        && polys[j].getEnvelopeInternal().intersects(polys[i].getEnvelopeInternal()) // bbox isect
                        && polys[i].contains(polys[j])) { // ring is inside poly
                    
                    // Sorry, this is ugly.  If j is inside a hole of i, then
                    // don't consider j a hole.
                    boolean isInsideHoleOfHole = false;
                    Collection<Polygon> existingHoles = intRings.get(polys[i]);
                    if (existingHoles != null) {
                        for (Polygon hole : intRings.get(polys[i])) {
                            if (hole.getEnvelopeInternal().intersects(polys[j].getEnvelopeInternal())
                                    && hole.contains(polys[j])) {
                                isInsideHoleOfHole = true;
                                break;
                            }
                        }
                    }
                    
                    if (!isInsideHoleOfHole) {
                        // Register j as an interior ring of i
                        if (!intRings.containsKey(polys[i])) {
                            intRings.put(polys[i], new ArrayList<Polygon>());
                        }
                        intRings.get(polys[i]).add(polys[j]);
                        intRingSet.add(polys[j]);                        
                    }
                }
            }
        }
        
        // Reconstruct the polygons, incorporating the interior rings
        // identified above.
        Polygon[] polyArray = new Polygon[polys.length - intRingSet.size()];
        int i = 0;
        for (Polygon p : polys) {
            if (intRingSet.contains(p)) {
                continue;
            }
            List<Polygon> intRingList = intRings.get(p); 
            int numIntRings = intRingList == null ? 0 : intRingList.size();
            LinearRing[] intRingArray = new LinearRing[numIntRings];
            LinearRing   extRing  = p.getFactory().createLinearRing(p.getExteriorRing().getCoordinates());
            
            for (int j = 0; j < numIntRings; j++) {
                intRingArray[j] = p.getFactory().createLinearRing(intRingList.get(j).getExteriorRing().getCoordinates());
            }
        
            polyArray[i] = p.getFactory().createPolygon(extRing, intRingArray);
            i++;
        }

        Geometry result = gfact.createMultiPolygon(polyArray);
        return result;
    };
}
