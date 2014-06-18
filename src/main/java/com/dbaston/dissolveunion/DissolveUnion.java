package com.dbaston.dissolveunion;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.index.strtree.STRtree;
import com.vividsolutions.jts.operation.polygonize.Polygonizer;
import java.util.Collection;
import java.util.HashSet;

/**
 *
 * @author dbaston
 */
public class DissolveUnion {
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
        DuplicateSegmentRemover dsr = new DuplicateSegmentRemover(geoms);
        Collection<LineString> rings = dsr.getMergedLineSegments(gfact);

        //System.out.println(gfact.buildGeometry(rings));
        
        Polygon[] polys = PolygonAssembler.getAssembled(getRingPolygons(rings, geoms)); 
        
        Geometry result = gfact.createMultiPolygon(polys);
        return result;
    };
}
