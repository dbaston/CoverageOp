package com.dbaston.dissolveunion;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.index.strtree.STRtree;
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
public class PolygonAssembler {    
    public static Polygon[] getAssembled (Collection<LineString> rings) {
        if (rings.isEmpty()) {
            return new Polygon[0];
        }
        
        GeometryFactory gfact = rings.iterator().next().getFactory();
        
        Polygon[] polys = new Polygon[rings.size()];
        int i = 0;
        for (LineString l : rings) {
            polys[i] = gfact.createPolygon(l.getCoordinates());
            i++;
        }
        
        return getAssembled(polys);
    }
    
    public static Polygon[] getAssembled (Polygon[] polys) {
        // Sort the array so that, when iterating positively over the array,
        // polygon shells are encountered before their potential holes.
        Arrays.sort(polys, new Comparator<Geometry>() {
            @Override
            public int compare(Geometry a, Geometry b) {
                // If Geometry a could be contained within Geometry b
                // (ie, a is a hole within the shell of b), this function
                // must return 1 or 0.  The function may return 1 or 0
                // even if it is impossible that b is a hole within a.
                return Double.compare(a.getEnvelopeInternal().getMinX(),
                                      b.getEnvelopeInternal().getMinY());
                
                //return ((Double) b.getEnvelopeInternal().getArea()).compareTo(
                //        a.getEnvelopeInternal().getArea());
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
                            // ring's envelope cannot have a minimum X coordinate greater than
                            // its containing shell
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
        
        return polyArray;
    }
}
