// **********************************************************************
// 
// <copyright>
// 
//  BBN Technologies, a Verizon Company
//  10 Moulton Street
//  Cambridge, MA 02138
//  (617) 873-8000
// 
//  Copyright (C) BBNT Solutions LLC. All rights reserved.
// 
// </copyright>
// **********************************************************************
// 
// $Source: /cvs/distapps/openmap/src/openmap/com/bbn/openmap/plugin/UTMGridPlugIn.java,v $
// $RCSfile: UTMGridPlugIn.java,v $
// $Revision: 1.2 $
// $Date: 2003/02/26 23:24:15 $
// $Author: dietrick $
// 
// **********************************************************************


package com.bbn.openmap.plugin;

import com.bbn.openmap.*;
import com.bbn.openmap.layer.util.LayerUtils;
import com.bbn.openmap.omGraphics.*;
import com.bbn.openmap.omGraphics.geom.*;
import com.bbn.openmap.proj.Ellipsoid;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.util.Debug;
import com.bbn.openmap.util.PaletteHelper;
import com.bbn.openmap.util.PropUtils;
import com.bbn.openmap.util.quadtree.QuadTree;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Paint;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;

import javax.swing.*;

/**
 * The UTMGridPlugIn renders UTM Zone areas, and renders a grid
 * marking equal-distance areas around the center of the current
 * projection.  This distance grid only extends east-west for 500km in
 * both directions from the center of the current zone because that is
 * the extent of accuracy for those measurements - after that, you get
 * too far away from the central meridian for the current UTM zone. <p>
 *
 * Currently, this plugin only draws 100km distance squares.  Updates
 * on the way.  The plugin has the following properties that may be
 * set:<p>
 * <pre>
 *
 * # Turn zone area labels on when zoomed in closer than 1:33M (true
 * # is default)
 * showZones=true
 * showLabels=true
 * # Color for UTM Zone area boundaries
 * utmGridColor=hex AARRGGBB value
 * # Color for the distance area grid lines
 * distanceGridColor= hex AARRGGBB value
 * </pre>
 */
public class UTMGridPlugIn extends OMGraphicHandlerPlugIn {

    protected boolean UTM_DEBUG = false;
    protected boolean UTM_DEBUG_VERBOSE = false;

    public final static int INTERVAL_100K = 100000;
    public final static float DEFAULT_UTM_LABEL_CUTOFF_SCALE = 33000000;

    protected boolean showZones = true;
    protected boolean showLabels = true;
    protected float labelCutoffScale = DEFAULT_UTM_LABEL_CUTOFF_SCALE;
    protected boolean show100kGrid = false;
    /**
     * Resolution should be MRGS accuracy, 0 for none, 1-5 otherwise,
     * where 1 = 10000 meter grid, 5 is 1 meter grid.
     */
    protected int distanceGridResolution = 0;
    protected Paint utmGridPaint = Color.black;
    protected Paint distanceGridPaint = Color.black;

    public final static String ShowLabelsProperty = "showLabels";
    public final static String ShowZonesProperty = "showZones";
    public final static String LabelCutoffScaleProperty = "labelCutoffScale";
    public final static String Show100kGridProperty = "show100KmGrid";
    public final static String UTMGridColorProperty = "utmGridColor";
    public final static String DistanceGridColorProperty = "distanceGridColor";
    public final static String DistanceGridResolutionProperty = "distanceGridResolution";

    public UTMGridPlugIn() {
	UTM_DEBUG = Debug.debugging("utmgrid");
	UTM_DEBUG_VERBOSE = Debug.debugging("utmgrid_verbose");
    }

    protected OMGeometryList createUTMZoneVerticalLines() {

	OMGeometryList verticalList = new OMGeometryList();
	float[] points = null;

	for (int lon = -180; lon < 180; lon += 6) {
	    if (lon == 6) {
		points = new float[] {56f, lon, -80f, lon};
	    } else if (lon > 6 && lon < 42) {
		points = new float[] {72f, lon, -80f, lon};
	    } else {
		points = new float[] {84f, lon, -80f, lon};
	    }
	    verticalList.add(new PolylineGeometry.LL(points, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_GREATCIRCLE));
	}

	points = new float[] {72f, 6f, 64f, 6f};
	verticalList.add(new PolylineGeometry.LL(points, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_GREATCIRCLE));

	points = new float[] {64f, 3f, 56f, 3f};
	verticalList.add(new PolylineGeometry.LL(points, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_GREATCIRCLE));

	points = new float[] {84f, 9f, 72f, 9f};
	verticalList.add(new PolylineGeometry.LL(points, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_GREATCIRCLE));

	points = new float[] {84f, 21f, 72f, 21f};
	verticalList.add(new PolylineGeometry.LL(points, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_GREATCIRCLE));

	points = new float[] {84f, 33f, 72f, 33f};
	verticalList.add(new PolylineGeometry.LL(points, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_GREATCIRCLE));

	verticalList.setLinePaint(utmGridPaint);
	
	return verticalList;
    }

    protected OMGeometryList createUTMZoneHorizontalLines() {
	OMGeometryList horizontalList = new OMGeometryList();
	float[] points = null;

	for (int lat = -80; lat <= 72; lat += 8) {
	    points = new float[] {lat, -180f, lat, -90f, lat, 0f, lat, 90f, lat, 180f};
	    horizontalList.add(new PolylineGeometry.LL(points, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_RHUMB));
	}

	points = new float[] {84f, -180f, 84f, -90f, 84f, 0f, 84f, 90f, 84f, 180f};
	horizontalList.add(new PolylineGeometry.LL(points, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_RHUMB));

	horizontalList.setLinePaint(utmGridPaint);

	return horizontalList;
    }

    protected QuadTree createUTMZoneLabels() {

	QuadTree labelTree = new QuadTree();

	UTMPoint utm = new UTMPoint();
	LatLonPoint llp = new LatLonPoint();
	float latitude;
	float longitude;

	for (int lat = -80; lat <= 72; lat += 8) {
	    for (int lon = -180; lon < 180; lon += 6) {

		latitude = (float) lat;
		longitude = (float) lon;

		if (lat == 56 && lon == 6) {
		    longitude = 3f;
		} else if (lat == 72 && (lon > 0 && lon < 42)) {
		    continue;
		}
		llp.setLatLon(latitude, longitude);
		addLabel(llp, UTMPoint.LLtoUTM(llp,utm), labelTree);
	    }
	}
    
	latitude = 72f;
	llp.setLatLon(latitude, 9f);
	addLabel(llp, UTMPoint.LLtoUTM(llp,utm), labelTree);
	llp.setLongitude(21f);
	addLabel(llp, UTMPoint.LLtoUTM(llp,utm), labelTree);
	llp.setLongitude(33f);
	addLabel(llp, UTMPoint.LLtoUTM(llp,utm), labelTree);

	return labelTree;
    }

    protected void addLabel(LatLonPoint llp, UTMPoint utm, QuadTree labelTree) {
	float latitude = llp.getLatitude();
	float longitude = llp.getLongitude();
	labelTree.put(latitude, longitude, new OMText(latitude, longitude, 2, -2, new String(utm.zone_number + "" + utm.zone_letter), OMText.JUSTIFY_LEFT));
    }

    protected OMGraphicList createEquiDistanceLines(UTMPoint utm, int gridLineInterval) {

	OMGraphicList list = new OMGraphicList();

	// Used to calculate the endpoints of the horizontal lines.
	UTMPoint utm1 = new UTMPoint(utm);
	UTMPoint utm2 = new UTMPoint(utm);
	LatLonPoint point1 = new LatLonPoint();
	LatLonPoint point2 = new LatLonPoint();

	// Used to calculate the pieces of the vertical lines.
	UTMPoint utmp = new UTMPoint(utm);
	LatLonPoint llp = new LatLonPoint();

	int i;
	OMLine line;
	BasicGeometry poly;

	float lat2;
 	int endNorthing = (int) Math.floor(utm.northing/INTERVAL_100K) + 10;
 	int startNorthing = (int) Math.floor(utm.northing/INTERVAL_100K) - 10;

	int numVertLines = 9;
	int numHorLines = endNorthing - startNorthing;

	float[][] vertPoints = new float[numVertLines][numHorLines * 2];

	if (UTM_DEBUG_VERBOSE) {
	    Debug.output("Array is [" + vertPoints.length + "][" + vertPoints[0].length + "]");
	}

	int coordCount = 0;
	boolean doPolys = true;

	utm1.easting = INTERVAL_100K;
	utm2.easting = 9 * INTERVAL_100K;

	// Horizontal lines
	for (i = startNorthing; i < endNorthing; i++) {
	    utm1.northing = (float) i * gridLineInterval;
	    utm2.northing = utm1.northing;
	    utmp.northing = utm1.northing;

	    if (doPolys) {
		for (int j = 0; j < numVertLines; j++) {
		    utmp.easting = (float) (j+1) * gridLineInterval;
		    llp = utmp.toLatLonPoint(Ellipsoid.WGS_84, llp);

		    vertPoints[j][coordCount] = llp.getLatitude();
		    vertPoints[j][coordCount+1] = llp.getLongitude();

		    if (UTM_DEBUG_VERBOSE) {
			Debug.output("for vline " + j + ", point " + i +
				     ", easting: " + utmp.easting + 
				     ", northing: " + utmp.northing + 
				     ", lat:" + vertPoints[j][coordCount] + 
				     ", lon:" + vertPoints[j][coordCount+1] );
		    }
		}
		coordCount+=2;
	    }

	    point1 = utm1.toLatLonPoint(Ellipsoid.WGS_84, point1);
	    point2 = utm2.toLatLonPoint(Ellipsoid.WGS_84, point2);

	    lat2 = point1.getLatitude();

	    if (lat2 < 84f) {
		line = new OMLine(point1.getLatitude(), point1.getLongitude(),
				  point2.getLatitude(), point2.getLongitude(),
				  OMGraphic.LINETYPE_GREATCIRCLE);
		line.setLinePaint(distanceGridPaint);
  		list.add(line);
	    }
	}


	if (doPolys) {
	    OMGeometryList polys = new OMGeometryList();
	    for (i = 0; i < vertPoints.length; i++) {
		if (UTM_DEBUG_VERBOSE) {
		    for (int k = 0; k < vertPoints[i].length; k += 2) {
			System.out.println(" for poly " + i + ": lat = " + 
					   vertPoints[i][k] + ", lon = " +
					   vertPoints[i][k+1]);
		    }
		}
		poly = new PolylineGeometry.LL(vertPoints[i], OMGraphic.DECIMAL_DEGREES, 
					       OMGraphic.LINETYPE_GREATCIRCLE);
		polys.add(poly);
	    } 
	    polys.setLinePaint(distanceGridPaint);
	    list.add(polys);
	} else {

	    // This doesn't seem to calculate the right 
	    // lines, although it looks like it should.

	    if (UTM_DEBUG) {
		Debug.output("Doing vertical lines");
	    }

	    utm1.northing = startNorthing;
	    utm2.northing = endNorthing;

	    // Vertical lines
	    for (i = 1; i <= 9; i++) {
		utm1.easting = i * 100000f;
		utm2.easting = i * 100000f;

		point1 = utm1.toLatLonPoint(Ellipsoid.WGS_84, point1);
		point2 = utm2.toLatLonPoint(Ellipsoid.WGS_84, point2);

		line = new OMLine(point1.getLatitude(), point1.getLongitude(),
				  point2.getLatitude(), point2.getLongitude(),
				  OMGraphic.LINETYPE_GREATCIRCLE);
		line.setLinePaint(distanceGridPaint);
		list.add(line);
	    }
	}

	return list;
    }

    protected OMGeometryList createMGRSRectangles(LatLonPoint llp, int accuracy, 
						  int numRects) {
	return createMGRSRectangles(llp, accuracy, numRects, Ellipsoid.WGS_84);
    }

    protected OMGeometryList createMGRSRectangles(LatLonPoint llp, int accuracy, 
						  int numRects, Ellipsoid ellipsoid) {
	MGRSPoint mgrs = new MGRSPoint();
	mgrs.setAccuracy(accuracy);
	MGRSPoint.LLtoMGRS(llp, ellipsoid, mgrs);

 	mgrs = new MGRSPoint(mgrs.getMGRS());
	mgrs.setAccuracy(accuracy);

	float accuracyBonus = 100000f/(float)Math.pow(10, accuracy);

	OMGeometryList list = new OMGeometryList();

	for (float i = -numRects * accuracyBonus; i < numRects * accuracyBonus; i+= accuracyBonus) {
	    for (float j = -numRects * accuracyBonus; j < numRects * accuracyBonus; j += accuracyBonus) {
		if (Debug.debugging("utmdistancegrid")) {
		    System.out.print(".");
		}
		list.add(createMGRSRectangle(mgrs, i, j, accuracyBonus, ellipsoid));
	    }
	    if (Debug.debugging("utmdistancegrid")) {
		System.out.println("");
	    }
	}

	return list;
    }

    /**
     * Create a polygon representing an equidistant area, at a meters
     * offset with a meters interval.
     * @param mgrsBasePoint the center point of interest that has been
     * normalized for the units of the rectangle (meters, km, etc).
     * @param voffset vertical offset in meters, normalized for units,
     * for entire polygon.
     * @param hoffset horizontal offset in meters, normalized for units,
     * for entire polygon.
     * @param interval edge length of rectangle polygon in meters,
     * normalized for units.
     * @param ellipsoid Ellipsoid for coordinate translation.
     */
    protected OMGeometry createMGRSRectangle(MGRSPoint mgrsBasePoint, 
					     float voffset, float hoffset, 
					     float interval, Ellipsoid ellipsoid) {

	float[] llpoints = new float[10];

	float easting = mgrsBasePoint.easting + hoffset;
	float northing = mgrsBasePoint.northing + voffset;
	int zone_number = mgrsBasePoint.zone_number;
	char zone_letter = mgrsBasePoint.zone_letter;

	LatLonPoint llp1 = new LatLonPoint();
	MGRSPoint.UTMtoLL(ellipsoid, northing, easting,
			  zone_number, zone_letter, llp1);
	llpoints[0] = llp1.getLatitude();
	llpoints[1] = llp1.getLongitude();
	llpoints[8] = llp1.getLatitude();
	llpoints[9] = llp1.getLongitude();

	MGRSPoint.UTMtoLL(ellipsoid, northing, easting + interval,
			  zone_number, zone_letter, llp1);
	llpoints[2] = llp1.getLatitude();
	llpoints[3] = llp1.getLongitude();

	MGRSPoint.UTMtoLL(ellipsoid, northing + interval, easting + interval,
			  zone_number, zone_letter, llp1);
	llpoints[4] = llp1.getLatitude();
	llpoints[5] = llp1.getLongitude();

	MGRSPoint.UTMtoLL(ellipsoid, northing + interval, easting,
			  zone_number, zone_letter, llp1);
	llpoints[6] = llp1.getLatitude();
	llpoints[7] = llp1.getLongitude();

	MGRSPoint mgrs = new MGRSPoint(northing, easting, zone_number, zone_letter);
	mgrs.setAccuracy(mgrsBasePoint.getAccuracy());
	MGRSPoint.MGRStoLL(mgrs, ellipsoid, llp1);
	String mgrsString = new String(mgrs.getMGRS());

	if (Debug.debugging("utmgriddetail")) 
	    Debug.output(" - assigning " + mgrsString + " to poly with " +
			 mgrs.getAccuracy());

	PolygonGeometry poly = new PolygonGeometry.LL(llpoints, OMGraphic.DECIMAL_DEGREES, (interval <= 1000?OMGraphic.LINETYPE_STRAIGHT:OMGraphic.LINETYPE_GREATCIRCLE));
	poly.setAppObject(mgrsString);
	return poly;
    }

    protected QuadTree labelTree;
    protected OMGraphicList labelList;
    protected OMGraphicList verticalList;
    protected OMGraphicList horizontalList;

    /**
     * The getRectangle call is the main call into the PlugIn module.
     * The module is expected to fill the graphics list with objects
     * that are within the screen parameters passed.
     *
     * @param p projection of the screen, holding scale, center
     * coords, height, width.
     */
    public OMGraphicList getRectangle(Projection p) {

	OMGraphicList list = getList();

	if (verticalList == null) {
	    verticalList = createUTMZoneVerticalLines();
	    horizontalList = createUTMZoneHorizontalLines();
	    labelTree = createUTMZoneLabels();
	}

	list.clear();

	if (showZones) {
	    list.add(verticalList);
	    list.add(horizontalList);
	}

	LatLonPoint center = p.getCenter();
	UTMPoint utm = new UTMPoint(center);

	if (show100kGrid) {
	    Debug.message("utmgrid", "Creating 100k distance lines...");

	    OMGraphicList hunKLines = createEquiDistanceLines(utm, 100000);
	    list.add(hunKLines);
	}

	if (distanceGridResolution > 0) {
	    Debug.message("utmgrid", "Creating distance lines...");

	    float decisionAid = 100000f/(float)Math.pow(10, distanceGridResolution); 

	    float dglc = 30f * decisionAid; // distance grid label cutoff
// 	    Debug.output("Basing decision to display labels on " + dglc);

	    int numberBasedForScale = (int)(p.getScale() / (2*decisionAid));
	    if (numberBasedForScale > 10) {
		numberBasedForScale = 10;
	    }
// 	    Debug.output(numberBasedForScale + "");

	    OMGeometryList geoList = createMGRSRectangles(center, distanceGridResolution, numberBasedForScale);


	    if (showLabels && p.getScale() <= dglc) {
		Debug.message("utmgrid", "Creating labels for distance lines ...");

		OMGraphicList textList = new OMGraphicList();
		LatLonPoint llp = new LatLonPoint();
		Point point = new Point();
		Iterator it = geoList.iterator();
		while (it.hasNext()) {
		    PolygonGeometry.LL pll = (PolygonGeometry.LL)it.next();
		    String labelString = (String)(pll).getAppObject();
		    float[] ll = pll.getLatLonArray();
		    llp.setLatLon(ll[0], ll[1], true);

		    p.forward(llp, point);

		    double x = point.getX();
		    double y = point.getY();
		    int buffer = 20;

		    // Lame attempt of testing whether the label is on-screen
		    if ((x > -buffer || x < p.getWidth() + buffer) &&
			(y > -buffer || y < p.getHeight() + buffer)) {

			OMText label = new OMText(llp.getLatitude(),
						  llp.getLongitude(), 4, -4,
						  labelString, OMText.JUSTIFY_LEFT);
			label.setLinePaint(distanceGridPaint);
			textList.add(label);
		    }
		}
		list.add(textList);
	    }

	    geoList.setLinePaint(distanceGridPaint);
	    list.add(geoList);
	}

	if (labelList != null) {
	    labelList.clear();
	} else {
	    labelList = new OMGraphicList();
	}

	if (showLabels && p.getScale() <= labelCutoffScale) {
	    Debug.message("utmgrid", "Creating labels for map...");
	    LatLonPoint ul = p.getUpperLeft();
	    LatLonPoint lr = p.getLowerRight();

	    Vector labels = labelTree.get(ul.getLatitude(), ul.getLongitude(),
					  lr.getLatitude(), lr.getLongitude());

	    labelList.setTargets(labels);
	    labelList.setLinePaint(getUTMGridPaint());
	    list.add(labelList);
	}

	Debug.message("utmgrid", "Generating OMGraphics...");
	list.generate(p);
	Debug.message("utmgrid", "Done.");
        return list;
    } //end getRectangle

    public Component getGUI() {
	JPanel panel = new JPanel();

	GridBagLayout gridbag = new GridBagLayout();
	GridBagConstraints c = new GridBagConstraints();
	panel.setLayout(gridbag);

	JCheckBox setZonesButton = new JCheckBox("Show UTM Zone Grid", showZones);
	setZonesButton.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent ae) {
		    JCheckBox button = (JCheckBox)ae.getSource();
		    showZones = button.isSelected();
		    doPrepare();
		}
	    });
	c.gridy = 0;
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(setZonesButton, c);
	panel.add(setZonesButton);

	JCheckBox set100kGridButton = new JCheckBox("Show 100Km Distance Grid", show100kGrid);	
	set100kGridButton.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent ae) {
		    JCheckBox button = (JCheckBox)ae.getSource();
		    show100kGrid = button.isSelected();
		    doPrepare();
		}
	    });

	c.gridy = 1;
	gridbag.setConstraints(set100kGridButton, c);
	panel.add(set100kGridButton);

	JCheckBox setLabelsButton = new JCheckBox("Show Zone Labels", showLabels);
	setLabelsButton.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent ae) {
		    JCheckBox button = (JCheckBox)ae.getSource();
		    showLabels = button.isSelected();
		    doPrepare();
		}
	    });	
	c.gridy = 2;
	gridbag.setConstraints(setLabelsButton, c);
	panel.add(setLabelsButton);

	JPanel resPanel = PaletteHelper.createPaletteJPanel("Distance Grid Units");
	String[] resStrings = {" No Grid ", " 10,000 meter   ", " 1000 meter ", " 100 meter ", " 10 meter ", " 1 meter "};

	JComboBox resList = new JComboBox(resStrings);
	resList.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent e) {
		    JComboBox jcb = (JComboBox) e.getSource();
		    setDistanceGridResolution(jcb.getSelectedIndex());
		    doPrepare();
		}
	    });

	resPanel.add(resList);

	c.gridy = 3;
	c.anchor = GridBagConstraints.CENTER;
	gridbag.setConstraints(resPanel, c);
	panel.add(resPanel);

	JButton utmGridColorButton = new JButton("Set UTM Grid Color");
	utmGridColorButton.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent ae) {
		    Color tmpPaint = getNewPaint((Component)ae.getSource(), 
						 "Choose UTM Grid Color", 
						 (Color)getUTMGridPaint());
		    if (tmpPaint != null) {
			setUTMGridPaint(tmpPaint);
			doPrepare();
		    }
		}
	    });

	c.gridy = 4;
	gridbag.setConstraints(utmGridColorButton, c);
	panel.add(utmGridColorButton);

	JButton distGridColorButton = new JButton("Set Distance Grid Color");
	distGridColorButton.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent ae) {
		    Color tmpPaint = getNewPaint((Component)ae.getSource(), 
						 "Choose Distance Grid Color", 
						 (Color)getDistanceGridPaint());
		    if (tmpPaint != null) {
			setDistanceGridPaint(tmpPaint);
			doPrepare();
		    }
		}
	    });
	
	c.gridy = 5;
	gridbag.setConstraints(distGridColorButton, c);
	panel.add(distGridColorButton);

	return panel;
    }

    /**
     * A convenience method to get a color from a JColorChooser.  Null
     * will be returned if the JColorChooser lock is in place, or if
     * something else is done where the JColorChooser would normally
     * return null.
     *
     * @param source the source component for the JColorChooser.
     * @param title the String to label the JColorChooser window.
     * @param startingColor the color to give to the JColorChooser to
     * start with.  Returned if the cancel button is pressed.  
     * @return Color chosen from the JColorChooser, null if lock for
     * chooser can't be sequired.
     */
    protected Color getNewPaint(Component source, String title, 
				Color startingColor) {
	Color newPaint = null;
	if (getLock()) {
  	    newPaint = OMColorChooser.showDialog(source, title, startingColor);
	    releaseLock();
	}
	return newPaint;
    }

    /**
     * A lock to use to limit the number of JColorChoosers that can
     * pop up for a given DrawingAttributes GUI.  
     */
    private boolean colorChooserLock = false;
    
    /**
     * Get the lock to use a JColorChooser.  Returns true if you got
     * the lock, false if you didn't.
     */
    protected synchronized boolean getLock() {
	if (colorChooserLock == false) {
	    colorChooserLock = true;
	    return colorChooserLock;
	} else {
	    return false;
	}
    }

    /**
     * Release the lock on the JColorChooser.
     */
    protected synchronized void releaseLock() {
	colorChooserLock = false;
    }

    public void setProperties(String prefix, Properties props) {
	super.setProperties(prefix, props);
	prefix = PropUtils.getScopedPropertyPrefix(prefix);

	showLabels = LayerUtils.booleanFromProperties(props, prefix + ShowLabelsProperty, showLabels);
	showZones = LayerUtils.booleanFromProperties(props, prefix + ShowZonesProperty, showZones);
	show100kGrid = LayerUtils.booleanFromProperties(props, prefix + Show100kGridProperty, show100kGrid);
	labelCutoffScale = LayerUtils.floatFromProperties(props, prefix + LabelCutoffScaleProperty, labelCutoffScale);
	utmGridPaint = LayerUtils.parseColorFromProperties(props, prefix + UTMGridColorProperty, utmGridPaint);
	distanceGridPaint = LayerUtils.parseColorFromProperties(props, prefix + DistanceGridColorProperty, distanceGridPaint);
	setDistanceGridResolution(LayerUtils.intFromProperties(props, prefix + DistanceGridResolutionProperty, distanceGridResolution));
    }

    public Properties getProperties(Properties props) {
	props = super.getProperties(props);

	String prefix = PropUtils.getScopedPropertyPrefix(this);
	props.put(prefix + ShowLabelsProperty, new Boolean(showLabels).toString());
	props.put(prefix + ShowZonesProperty, new Boolean(showZones).toString());
	props.put(prefix + LabelCutoffScaleProperty, Float.toString(labelCutoffScale));
	props.put(prefix + Show100kGridProperty, new Boolean(show100kGrid).toString());
	props.put(prefix + UTMGridColorProperty, Integer.toHexString(((Color)utmGridPaint).getRGB()));
	props.put(prefix + DistanceGridColorProperty, Integer.toHexString(((Color)distanceGridPaint).getRGB()));
	props.put(prefix + DistanceGridResolutionProperty, Integer.toString(distanceGridResolution));
	return props;
    }

    public Properties getPropertyInfo(Properties props) {
	props = super.getPropertyInfo(props);

	props.put(ShowZonesProperty, "Show UTM Zone Grid Lines");
	props.put(ShowZonesProperty + ScopedEditorProperty, 
		  "com.bbn.openmap.util.propertyEditor.YesNoPropertyEditor");

	props.put(UTMGridColorProperty, "Color for UTM Zone Grid lines.");
	props.put(UTMGridColorProperty + ScopedEditorProperty, 
		 "com.bbn.openmap.util.propertyEditor.ColorPropertyEditor");

	props.put(ShowLabelsProperty, "Show Labels for Grid Lines");
	props.put(ShowLabelsProperty + ScopedEditorProperty, 
		  "com.bbn.openmap.util.propertyEditor.YesNoPropertyEditor");

	props.put(Show100kGridProperty, "Show 100Km Distance Grid Lines");
	props.put(Show100kGridProperty + ScopedEditorProperty, 
		  "com.bbn.openmap.util.propertyEditor.YesNoPropertyEditor");

	props.put(DistanceGridColorProperty, "Color for Equal-Distance Grid Lines.");
	props.put(DistanceGridColorProperty + ScopedEditorProperty, 
		  "com.bbn.openmap.util.propertyEditor.ColorPropertyEditor");

	props.put(DistanceGridResolutionProperty, "Meter Resolution for Distance Grid Lines (0-5)");

	props.put(initPropertiesProperty, ShowZonesProperty + " " + 
		  UTMGridColorProperty + " " + 
		  Show100kGridProperty + " " + 
		  ShowLabelsProperty + " " + 
		  DistanceGridResolutionProperty + " " + 
		  DistanceGridColorProperty);
	return props;
    }

    public void setShowZones(boolean value) {
	showZones = value;
    }

    public boolean isShowZones() {
	return showZones;
    }

    public void setShowLabels(boolean value) {
	showLabels = value;
    }

    public boolean isShowLabels() {
	return showLabels;
    }

    public void setLabelCutoffScale(float value) {
	labelCutoffScale = value;
    }

    public float getLabelCutoffScale() {
	return labelCutoffScale;
    }

    public void setShow100kGrid(boolean value) {
	show100kGrid = value;
    }

    public boolean isShow100kGrid() {
	return show100kGrid;
    }

    /**
     * Resolution should be MRGS accuracy, 0 for none, 1-5 otherwise,
     * where 1 = 10000 meter grid, 5 is 1 meter grid.
     */
    public void setDistanceGridResolution(int value) {
	distanceGridResolution = value;
	if (distanceGridResolution < 0 || 
	    distanceGridResolution > MGRSPoint.ACCURACY_1_METER) {
	    distanceGridResolution = 0;
	}
    }

    public int getDistanceGridResolution() {
	return distanceGridResolution;
    }

    public void setUTMGridPaint(Paint value) {
	utmGridPaint = value;

	if (verticalList != null) {
	    verticalList.setLinePaint(getUTMGridPaint());
	    horizontalList.setLinePaint(getUTMGridPaint());
	}
    }

    public Paint getUTMGridPaint() {
	return utmGridPaint;
    }

    public void setDistanceGridPaint(Paint value) {
	distanceGridPaint = value;
    }

    public Paint getDistanceGridPaint() {
	return distanceGridPaint;
    }
}