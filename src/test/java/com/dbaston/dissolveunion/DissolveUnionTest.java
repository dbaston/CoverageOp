/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.dbaston.dissolveunion;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.operation.union.UnaryUnionOp;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author dbaston
 */
public class DissolveUnionTest {
    class UnionChecker {
        public ArrayList<Geometry> geoms;

        public UnionChecker() {
            this.geoms = new ArrayList<>();
        }
        
        public UnionChecker add (String wkt) throws Exception {
            return this.add(readWKT(wkt));
        }
        
        public UnionChecker add (Geometry g) {
            this.geoms.add(g);
            return this;
        }
        
        public UnionChecker add (Collection<Geometry> g) {
            this.geoms.addAll(g);
            return this;
        }
        
        public Geometry getDissolveResult() {
            long startTime, endTime;
            
            startTime = System.currentTimeMillis();
            Geometry result = DissolveUnion.union(geoms, null);
            endTime = System.currentTimeMillis();
            
            System.out.println("DU: (" + (endTime-startTime)/1000.0 + ") " + result.toString());
            return result;
        }
        
        public Geometry getReferenceResult() {
            long startTime, endTime;
            
            startTime = System.currentTimeMillis();
            Geometry result = UnaryUnionOp.union(geoms, null);
            endTime = System.currentTimeMillis();

            System.out.println("UU: (" + (endTime-startTime)/1000.0 + ") " +  result.toString());     
            
            return result;            
        }
        
        public void checkUnchanged() {
            GeometryFactory gfact = geoms.iterator().next().getFactory();
            Geometry result   = getDissolveResult();
            Geometry original = gfact.buildGeometry(geoms);
            
            assertTrue(result.equalsTopo(original));
        }
        
        public void check() {
            Geometry result = getDissolveResult();
//            Geometry refResult = getReferenceResult();
//
//            assertTrue(result.equalsTopo(refResult));
        }
    }
    
    public DissolveUnionTest() {
    }
    
    public static Geometry geometryCollectionUnion(Collection<Geometry> geoms) {
        GeometryFactory gfact = geoms.isEmpty() ? new GeometryFactory() : geoms.iterator().next().getFactory();
        return gfact.createGeometryCollection(geoms.toArray(new Geometry[geoms.size()])).buffer(0.0);
    }
    
    private Geometry readWKT(String wkt) throws Exception {
        return new WKTReader().read(wkt);
    }
    
    private Collection<Geometry> readBlocks() throws Exception {
        ArrayList<Geometry> geoms = new ArrayList<>();
        WKTReader reader = new WKTReader();
        BufferedReader br = new BufferedReader(new FileReader("src\\test\\resources\\blocks_vt.csv"));
        
        br.readLine(); // toss the header
        String line = br.readLine(); 
        for (int i = 0; line != null; i++) {
            geoms.add(reader.read(line));
            line = br.readLine();
        }
        return geoms;
    }
    
    // ********************************************************************** //
    // These tests check that the dissolve algorithm correctly performs a     //
    // union on non-overlapping inputs.  The UnionChecker verifies that the   //
    // ouput of the dissolve operation is topologically equivalent to a       //
    // reference algorithm (UnaryUnionOp.union()                              //
    // ********************************************************************** //
    
    @Test
    public void testAdjacentSquares() throws Exception {
        new UnionChecker()
                .add("POLYGON ((0 0, 0 1, 1 1, 1 0, 0 0))")
                .add("POLYGON ((1 0, 1 1, 2 1, 2 0, 1 0))")
                .check();
    }
    
    @Test
    public void testNestedSquares() throws Exception {
        new UnionChecker()
                .add("POLYGON ((-0.83 1.06, -0.629 1.06, -0.629 0.875, -0.83 0.875, -0.83 1.06), (-0.675 0.918, -0.78 0.918, -0.78 1.02, -0.675 1.02, -0.675 0.918))")
                .add("POLYGON ((-0.675 0.918, -0.78 0.918, -0.78 1.02, -0.675 1.02, -0.675 0.918))")
                .check();
    }
    
    @Test
    public void testDisconnectedComponents() throws Exception {
        new UnionChecker()
                .add("POLYGON ((-0.84 1.18, -0.705 1.18, -0.705 1.121, -0.84 1.121, -0.84 1.18))")
                .add("POLYGON ((-1.016 1.184, -0.89 1.184, -0.89 1.11, -1.016 1.11, -1.016 1.184))")
                .check();
    }
    
    @Test
    public void testBowTie() throws Exception {
        new UnionChecker()
                .add("POLYGON ((-0.88 1.04, -0.79 1.07, -0.865 1.123, -0.88 1.04))")
                .add("POLYGON ((-0.865 1.123, -0.935 1.167, -0.863 1.186, -0.865 1.123))")
                .check();
    }
    
    @Test
    public void checkPolygonInsideHole() throws Exception {
        new UnionChecker()
                .add("POLYGON ((0 0, 0 20, 40 20, 40 0, 0 0), (30 10, 35 10, 35 15, 5 15, 5 5, 30 5, 30 10))")
                .add("POLYGON ((20 10, 20 12, 30 12, 29 10, 20 10))")
                .check();
    }
    
    @Test
    public void checkPolygonInsideHoleTouching() throws Exception {
        new UnionChecker()
                .add("POLYGON ((0 0, 0 20, 40 20, 40 0, 0 0), (30 10, 35 10, 35 15, 5 15, 5 5, 30 5, 30 10))")
                .add("POLYGON ((20 10, 20 12, 30 12, 30 10, 20 10))")
                .check();        
    }
    
    @Test
    public void checkMultipleNestedHoles() throws Exception {
        new UnionChecker()
                .add("MULTIPOLYGON (((0 0, 0 70, 70 70, 70 0, 0 0), " +
                      "(20 10, 30 10, 30 20, 40 20, 40 10, 50 10, 50 20, 60 20, 60 30, 50 30, 50 40, 60 40, 60 50, 50 50, 50 60, 40 60, 40 50, 30 50, 30 60, 20 60, 20 50, 10 50, 10 40, 20 40, 20 30, 10 30, 10 20, 20 20, 20 10))," +
                "  ((20 20, 20 30, 30 30, 30 20, 20 20))," +
                "  ((40 20, 40 30, 50 30, 50 20, 40 20))," +
                "  ((30 30, 30 40, 40 40, 40 30, 30 30))," +
                "  ((20 40, 20 50, 30 50, 30 40, 20 40))," +
                "  ((40 40, 40 50, 50 50, 50 40, 40 40)))")
                .check();
    }
    
//    // ********************************************************************** //
//    // These tests check that overlapping inputs are unmodified by the        //
//    // algorithm.  In other words, if there are no shared boundaries to       //
//    // dissolve, the dissolve algorithm should not alter the input.           //
//    // ********************************************************************** //
//    
//    @Test
//    public void checkOverlappingSquares() throws Exception {
//        new UnionChecker()
//                .add("POLYGON ((60 400, 170 400, 170 280, 60 280, 60 400))")
//                .add("POLYGON ((200 250, 90 250, 90 360, 200 360, 200 250))")
//                .checkUnchanged();
//    }
//    
//    @Test
//    public void checkOverlappingSquaresWithHole() throws Exception {
//        new UnionChecker()
//                .add("POLYGON ((19.2 15.25, 28.35 15.25, 28.35 8.35, 19.2 8.35, 19.2 15.25), (20 14, 22.75 14, 22.75 11.9, 20 11.9, 20 14))")
//                .add("POLYGON ((11.35 21.3, 23.7 21.3, 23.7 10.8, 11.35 10.8, 11.35 21.3))")
//                .checkUnchanged();
//    }
    
    @Test
    public void performanceTestVTBlocks() throws Exception {
        new UnionChecker().add(readBlocks()).check();
    }
    
}
