package com.dbaston.dissolveunion;

import com.vividsolutions.jts.io.WKTReader;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author dbaston
 */
public class PolygonCoverageTest {
	public class PolygonCoverageTester {
		public PolygonCoverage pc;
		public PolygonCoverageTester() {
			pc = new PolygonCoverage();
		}
		
		public PolygonCoverageTester add(String wkt) throws Exception {
			pc.add(new WKTReader().read(wkt));
			return this;
		}
		
		public void assertInvalid() {
			assertFalse(pc.isValid());
		}
		
		public void assertValid() {
			assertTrue(pc.isValid());
		}
	}
	
	
	
	public PolygonCoverageTest() {
	}

	@Before
	public void setUp() {
	}

	
	@Test
    public void checkOverlappingSquares() throws Exception {
        new PolygonCoverageTester()
                .add("POLYGON ((60 400, 170 400, 170 280, 60 280, 60 400))")
                .add("POLYGON ((200 250, 90 250, 90 360, 200 360, 200 250))")
                .assertInvalid();
    }
    
    @Test
    public void checkOverlappingSquaresWithHole() throws Exception {
        new PolygonCoverageTester()
                .add("POLYGON ((19.2 15.25, 28.35 15.25, 28.35 8.35, 19.2 8.35, 19.2 15.25), (20 14, 22.75 14, 22.75 11.9, 20 11.9, 20 14))")
                .add("POLYGON ((11.35 21.3, 23.7 21.3, 23.7 10.8, 11.35 10.8, 11.35 21.3))")
                .assertInvalid();
    }
    
        @Test
    public void testDisconnectedComponents() throws Exception {
        new PolygonCoverageTester()
                .add("POLYGON ((-0.84 1.18, -0.705 1.18, -0.705 1.121, -0.84 1.121, -0.84 1.18))")
                .add("POLYGON ((-1.016 1.184, -0.89 1.184, -0.89 1.11, -1.016 1.11, -1.016 1.184))")
                .assertValid();
    }
    
}
