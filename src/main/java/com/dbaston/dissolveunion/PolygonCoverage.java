/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.dbaston.dissolveunion;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import java.util.ArrayList;
import java.util.Collection;

/**
 *
 * @author dbaston
 */
public class PolygonCoverage {
	private Collection<Polygon> polygons;
	
	public Collection<Polygon> getPolygons() {
		return this.polygons;
	}
	
	public boolean isEmpty() {
		return (this.polygons.isEmpty());
	}
	
	public boolean isValid() {
		if (this.isEmpty()) {
			return true;
		}
		
		GeometryFactory gfact = polygons.iterator().next().getFactory();
		DuplicateSegmentRemover dsr = new DuplicateSegmentRemover();
		for (Polygon p : polygons) {
			dsr.add((Geometry) p);
		}
		
		MultiLineString mls = dsr.getMultiLineString(gfact);
		return mls.isSimple();
	}
	
	public void add (Geometry g) {
		if (g instanceof MultiPolygon) {
            for (int i = 0; i < g.getNumGeometries(); i++) {
                add((Polygon) g.getGeometryN(i));
            }
            return;
        }
        
        if (!(g instanceof Polygon)) {
            throw new IllegalArgumentException("Geometries must be Polygons or MultiPolygons");
        }
	
		this.polygons.add((Polygon) g);
	}
	
	public PolygonCoverage() {
		this.polygons = new ArrayList<>();
	}
	
	public PolygonCoverage(Collection<Geometry> geoms) {
		this.polygons = new ArrayList<>(geoms.size());
		for (Geometry g : geoms) {
			this.add(g);
		}
	}
}
