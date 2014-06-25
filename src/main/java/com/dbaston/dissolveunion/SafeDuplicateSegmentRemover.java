/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.dbaston.dissolveunion;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineSegment;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author dbaston
 */
public class SafeDuplicateSegmentRemover extends DuplicateSegmentRemover {
	private HashSet<LineSegment> duplicates = new HashSet<>();
	
	@Override
	protected void processSegment(LineSegment ls) {
		ls.normalize();
		if (!lines.remove(ls) || duplicates.contains(ls)) {
			lines.add(ls);
		}
	}
}
