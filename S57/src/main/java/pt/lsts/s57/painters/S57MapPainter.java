/*
 * Copyright (c) 2004-2016 Laboratório de Sistemas e Tecnologia Subaquática and Authors
 * All rights reserved.
 * Faculdade de Engenharia da Universidade do Porto
 * Departamento de Engenharia Electrotécnica e de Computadores
 * Rua Dr. Roberto Frias s/n, 4200-465 Porto, Portugal
 *
 * For more information please see <http://whale.fe.up.pt/neptus>.
 *
 * Created by Hugo Dias
 * 
 */
package pt.lsts.s57.painters;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.kitfox.svg.SVGException;

import pt.lsts.neptus.core.LocationType;
import pt.lsts.s57.S57;
import pt.lsts.s57.S57Factory;
import pt.lsts.s57.entities.S57Map;
import pt.lsts.s57.entities.S57Object;
import pt.lsts.s57.entities.S57ObjectPainter;
import pt.lsts.s57.mc.MarinerControls;
import pt.lsts.s57.resources.Resources;
import pt.lsts.s57.resources.entities.s52.Instruction;
import pt.lsts.s57.resources.entities.s52.InstructionType;
import pt.lsts.s57.s52.ConditionalSymbologyProcess;
import pt.omst.mapview.Renderer2DPainter;
import pt.omst.mapview.StateRenderer2D;

/**
 * Renderer2D Painter for S57 Maps
 * 
 * @author Hugo Dias
 * @author Paulo Dias
 */
public class S57MapPainter implements Renderer2DPainter, S57Painter {

    public static int offScreenBufferPixel = 400;
    
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMdd");

    private final Resources resources;
    private Map<String, S57Map> maps;
    private S57 s57;
    private Map<String, List<S57ObjectPainter>> mapsPainters = new HashMap<String, List<S57ObjectPainter>>();
    private List<String> mapOrder = new ArrayList<String>();

    private MarinerControls mc;
    private MarinerControls mcLastState;
    private Map<String, Object> rendererLastState = new HashMap<String, Object>();

    // Cache
    private BufferedImage cache;
    private boolean cacheValid;
    private Graphics2D graphics;

    private int numObjs = 0;
    private Comparator<S57ObjectPainter> painterComparator;
    private Comparator<String> mapComparator;
    private Comparator<S57Map> mapObjectComparator;
    private List<S57Map> currentMaps = Collections.synchronizedList(new ArrayList<S57Map>());

    // Added to control repaint when map is finished loading
    private StateRenderer2D rend2D = null;

    /**
     * Static factory method
     * 
     * @param config
     * @param resources
     * @param maps
     * @param mc
     * @return NeptusS57Painter
     */
    public static S57MapPainter forge(S57 s57, MarinerControls mc) {
        return new S57MapPainter(s57, mc);
    }

    /**
     * Constructor
     * 
     * @param config
     * @param resources
     * @param maps
     * @param mc
     */
    private S57MapPainter(S57 s57, MarinerControls mc) {
        this.resources = s57.getResources();
        this.maps = s57.getMaps();
        this.s57 = s57;
        this.mc = mc;
        this.mcLastState = null;
        this.cacheValid = false;
        this.painterComparator = new Comparator<S57ObjectPainter>() {

            @Override
            public int compare(S57ObjectPainter o1, S57ObjectPainter o2) {
                if (o1.getPriority() < o2.getPriority())
                    return -1;
                else if (o1.getPriority() > o2.getPriority()) {
                    return 1;
                }
                return 0;
            }
        };
        this.mapComparator = new Comparator<String>() {

            @Override
            public int compare(String o1, String o2) {
                int o1Int = Integer.valueOf(o1.charAt(2));
                int o2Int = Integer.valueOf(o2.charAt(2));
                if (o1Int < o2Int)
                    return -1;
                else if (o1Int > o2Int) {
                    return 1;
                }
                return 0;
            }
        };
        this.mapObjectComparator = new Comparator<S57Map>() {

            @Override
            public int compare(S57Map o1, S57Map o2) {
                int o1Int = o1.getMapInfo().getPurpose();
                int o2Int = o2.getMapInfo().getPurpose();
                if (o1Int > o2Int)
                    return -1;
                else if (o1Int < o2Int) {
                    return 1;
                }
                return 0;
            }
        };
    }

    @Override
    public void paint(Graphics2D gO, StateRenderer2D srend) {
        rend2D = srend;
        if (S57Factory.DEBUG && !cacheValid) {
            System.out.println(">>>>>>>>>> S57 Paint cache=" + cacheValid);
            System.out.flush();
        }
        long start = System.nanoTime();
        // Rendering options
        RenderingHints renderHints = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
        renderHints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        gO.setRenderingHints(renderHints);

        // Save Renderer State
        Map<String, Object> rendererState = saveRendererState(srend);
        gO.translate(1, -1);

        // if (S57.isLoadingFolder())
        // {
        // gO.setColor(Color.white);
        // gO.drawString("Loading maps ...", srend.getWidth() - 100, srend.getHeight() - 20);
        // }
        // Paint cache
        if (!hasRendererChanged(rendererState) && mc.equals(mcLastState) && cacheValid) {
            LocationType last = (LocationType) rendererLastState.get("center");
            double[] offset = srend.getCenter().getDistanceInPixelTo(last, srend.getLevelOfDetail());
            offset = rotate(srend.getRotation(), offset[0], offset[1], true);
            gO.drawImage(cache, null, (int) offset[0] - offScreenBufferPixel, (int) offset[1] - offScreenBufferPixel);
            // if(S57Factory.DEBUG) System.out.println("System used the cached image " + ((System.nanoTime() - start) /
            // 1E9) + "s");
            return;
        }

        // Create or rebuild cache
        cache = new BufferedImage(srend.getWidth() + offScreenBufferPixel * 2,
                srend.getHeight() + offScreenBufferPixel * 2, BufferedImage.TYPE_INT_ARGB);
        graphics = cache.createGraphics();
        graphics.translate(offScreenBufferPixel, offScreenBufferPixel);
        graphics.setRenderingHints(gO.getRenderingHints());
        double processTime = 0;
        if (!cacheValid || (maps.size() > 0
                && (!mc.equals(mcLastState) || !rendererLastState.get("scale").equals(rendererState.get("scale"))))) {
            long p_start = System.nanoTime();

            mapsPainters.clear();
            mapOrder.clear();
            process(srend);
            processTime = ((System.nanoTime() - p_start) / 1E9);

        }

        // Paint world map
        if (srend.getLevelOfDetail() < 11)
            paintSVG(graphics, srend);

        // PAINT THE OBJECTS IN THE LIST
        for (String mapName : mapOrder) {
            for (S57ObjectPainter test : mapsPainters.get(mapName)) {
                test.paint(graphics, srend, mc);
//                if ("SLCONS".equals(test.getObject().getAcronym())) {
//                    System.out.println(test.getObject());
//                    System.out.println(test.getObject().toStringLup(mc));
//                }
            }
        }

        // Paint image to the renderer
        gO.drawImage(cache, null, -offScreenBufferPixel, -offScreenBufferPixel);

        // persist renderer and mc state
        rendererLastState = rendererState;
        mcLastState = (MarinerControls) mc.clone();

        // change cache to valid because we made just made a new one
        cacheValid = true;
        if (S57Factory.DEBUG) {
            int loaded = 0;
            int loading = 0;
            StringBuilder mapsListStrBld = new StringBuilder();
            mapsListStrBld.append(s57.getMaps().size()).append(" :: ");
            for (Entry<String, S57Map> entry : s57.getMaps().entrySet()) {
                S57Map map = entry.getValue();
                if (map.isLoaded() || map.isLoading()) {
                    mapsListStrBld.append(map.getName() + ":" + map.getMapInfo().getPurpose())
//                    .append("[")
//                    .append("minScale=").append(map.getScaleMin())
//                    .append(", ")
                    .append(map.isLoaded() ? "*" : "" )
                    .append(map.isLoading() ? "^" : "" )
//                    .append("]");
                   .append(" ");
                }
                if (map.isLoaded())
                    loaded++;

                if (map.isLoading())
                    loading++;
            }
            System.out.println("loadded: " + loaded + " loading: " + loading);
            System.out.println("Painted in " + ((System.nanoTime() - start) / 1E9) + "s process took " + processTime
                    + " with " + numObjs + " objs ( LOD " + srend.getLevelOfDetail() + ", Scale 1:" 
                    + Math.round(srend.getMapScale()) + " ) current maps "
//                    + this.getCurrentMaps().size() + " :: " + this.getCurrentMaps().toString().replaceAll("S57Map \\[name=", "")
//                    .replaceAll("\\[|\\]", "").replaceAll(", ","\t"));
                    + mapsListStrBld.toString());
        }
    }

    /**
     * Paints World SVG to the cache image
     * 
     * @param gO
     * @param srend
     */
    private void paintSVG(Graphics2D gO, StateRenderer2D srend) {
        Graphics2D g = (Graphics2D) gO.create();
        LocationType gps = new LocationType(90, -180);

        Point2D start = srend.getScreenPosition(gps);
        double res = Math.pow(2.0, srend.getLevelOfDetail());

        g.translate(start.getX(), start.getY() - 15 * res);
        g.scale(res, res);

        try {
            resources.renderSVG(g);
        }
        catch (SVGException e) {
            System.out.println("exception rendering svg!");
        }
        g.dispose();
    }

    /**
     * Processes all the available maps and filters objects that aren't needed
     * 
     * @param srend
     * @param srendGeometry
     */
    private void process(StateRenderer2D srend) {
        Path2D srendBig = getRendererBoundsGPS(srend, false);
        Path2D srendSmall = getRendererBoundsGPS(srend, true);
        List<S57Map> maps2Load = new ArrayList<S57Map>();
        List<S57Map> maps2Unload = new ArrayList<S57Map>();
        numObjs = 0;

        synchronized (maps) {
            for (Entry<String, S57Map> entry : maps.entrySet()) {
                S57Map map = entry.getValue();
                String mapName = entry.getKey();
                List<S57Object> covs = map.getCoverage();
                // intersect Main filter
                if (!intersectCovsWithRenderer(covs, srendSmall)) {
                    if (map.isLoaded())
                        maps2Unload.add(map);
                    continue;
                }

                List<S57ObjectPainter> objectsPainters = new ArrayList<S57ObjectPainter>();
                int threshold = (map.getMapInfo().getPurpose()) * 2 + 3;

                // create cov painters
                objectsPainters.addAll(createCoveragePainter(covs));

//                System.out.printf("Map scale %f | LOD %d | %s with purpose %d:%d\n", srend.getMapScale(),
//                        srend.getLevelOfDetail(),
//                        map.getMapInfo().getName(),
//                        map.getMapInfo().getPurpose(),
//                        map.getMapInfo().getPurpose() * 2 + 3);
                
                // Dynamic map loading
                // load
                boolean scale = getScale(map.getMapInfo().getPurpose(), srend.getMapScale());
                if (scale) {
                    maps2Load.add(map);
                }

                // unload
                if (!scale && map.isLoaded() && srend.getLevelOfDetail() < threshold) {
                    maps2Unload.add(map);
                    if (S57Factory.DEBUG && scale)
                        System.out.println("...................." + map.getMapInfo().getName());
                }
                else if (scale && map.isLoaded()) {
                    List<S57Object> objects = map.getObjects();
                    for (S57Object obj : objects) {
                        // MAIN filter
                        if (obj.getLup(mc).isEmpty()) {
                            continue;
                        }
                        
                        // DATE filter
                        if (mc.isDateFilter() && !isDateFilterOk(obj))
                            continue;
                        
                        // Scale Filter
                        if (mc.isScaleFilter() && obj.getScamin() <= srend.getMapScale()
                                && !obj.getIdField("GRUP").equals("1")) {
                            continue;
                        }
                        // Intersect with Renderer Filter
                        if (!obj.intersects(srendBig)) {
                            continue;
                        }
                        // Soundings filter
                        if (!mc.isSoundings() && obj.getAcronym().equals("SOUNDG"))
                            continue;

                        S57ObjectPainter painter = S57ObjectPainter.forge(obj, mc, this.resources);
                        if (obj.hasConditional(mc)) {
                            // process instructions
                            ConditionalSymbologyProcess.process(painter, resources, mc, entry.getValue(),
                                    objectsPainters);
                        }

                        // Display Category Filter
                        if (mc.getDisplayCategory().getCode() < painter.getDisplayCategory().getCode()) {
                            continue;
                        }

                        objectsPainters.add(painter);
                        numObjs++;
                    }
                }

                if (!objectsPainters.isEmpty()) {
                    mapsPainters.put(mapName, objectsPainters);
                    mapOrder.add(mapName);
                    // SORT object painters
                    Collections.sort(objectsPainters, painterComparator);
                }
            }

        } // end maps cycle

        // sort maps bigger purpose first
        Collections.sort(maps2Load, mapObjectComparator);
        for (Iterator<S57Map> iterator = maps2Load.iterator(); iterator.hasNext();) {
            S57Map map = iterator.next();
            if (mapContainsRenderer(map.getCoverage(), srendSmall)) {
                S57Map mainMap = map;
                maps2Load.remove(map);
                maps2Unload.addAll(maps2Load);
                maps2Load.clear();
                maps2Load.add(mainMap);
                break;
            }
        }
        // dynamic loading maps
        currentMaps.clear();
        currentMaps.addAll(maps2Load);
        s57.loadMapObjects(maps2Load);
        s57.unloadMapObjects(maps2Unload);
        Collections.sort(mapOrder, mapComparator);
    }

    /**
     * @param obj
     * @return
     */
    private boolean isDateFilterOk(S57Object obj) {
        boolean filterOk = true;

        String datstaStr = obj.isAttribute("DATSTA") ? obj.getAttribute("DATSTA").getValue().get(0) : "";
        String datendStr = obj.isAttribute("DATEND") ? obj.getAttribute("DATEND").getValue().get(0) : "";
        String perstaStr = obj.isAttribute("PERSTA") ? obj.getAttribute("PERSTA").getValue().get(0) : "";
        String perendStr = obj.isAttribute("PEREND") ? obj.getAttribute("PEREND").getValue().get(0) : "";
        if (!datstaStr.isEmpty() || !datendStr.isEmpty() || !perstaStr.isEmpty() || !perendStr.isEmpty()) {
//            if (S57Factory.DEBUG)
//                System.out.println("DATES L   :" + datstaStr + ":" + datendStr + ":" + perstaStr + ":" + perendStr + ":");
        
            long datsta = Long.MIN_VALUE;
            long datend = Long.MAX_VALUE;
            long persta = Long.MIN_VALUE;
            long perend = Long.MAX_VALUE;
            try {
                if (!datstaStr.isEmpty())
                    datsta = dateFormatter.parse(datstaStr).getTime();
            }
            catch (ParseException e) {
                e.printStackTrace();
            }
            try {
                if (!datendStr.isEmpty())
                    datend = dateFormatter.parse(datendStr).getTime();
            }
            catch (ParseException e) {
                e.printStackTrace();
            }
            try {
                if (!perstaStr.isEmpty()) {
                    persta = dateFormatter.parse(Calendar.getInstance().get(Calendar.YEAR)
                            + perstaStr.replaceAll("[^0-9]", "")).getTime();
                }
            }
            catch (ParseException e) {
                e.printStackTrace();
            }
            try {
                if (!perendStr.isEmpty()) {
                    perend = dateFormatter.parse(Calendar.getInstance().get(Calendar.YEAR)
                            + perendStr.replaceAll("[^0-9]", "")).getTime();
                }
            }
            catch (ParseException e) {
                e.printStackTrace();
            }
            
            long currentTimeMillis = System.currentTimeMillis();
            if (!datstaStr.isEmpty() && currentTimeMillis < datsta)
                filterOk = false;
            if (filterOk && !datendStr.isEmpty() && currentTimeMillis > datend)
                filterOk = false;
            if (filterOk && !perstaStr.isEmpty() && currentTimeMillis < persta)
                filterOk = false;
            if (filterOk && !perendStr.isEmpty() && currentTimeMillis > perend)
                filterOk = false;
            
            if (S57Factory.DEBUG && !filterOk)
                System.out.println("Filtered " + (filterOk ? "in" : "out") + " DATES   :" + dateFormatter.format(new Date(datsta)) 
                    + ":" + dateFormatter.format(new Date(datend)) + ":" + dateFormatter.format(new Date(persta)) 
                            + ":" + dateFormatter.format(new Date(perend)) + ":");
        }
        return filterOk;
    }

    private Map<String, Object> saveRendererState(StateRenderer2D srend) {
        Map<String, Object> rendererState = new HashMap<String, Object>();
        rendererState.put("zoom", srend.getLevelOfDetail());
        rendererState.put("center", srend.getCenter().getNewAbsoluteLatLonDepth());
        rendererState.put("width", srend.getWidth());
        rendererState.put("height", srend.getHeight());
        rendererState.put("scale", srend.getMapScale());
        rendererState.put("rotation", srend.getRotation());
        return rendererState;
    }

    private boolean hasRendererChanged(Map<String, Object> properties) {
        if (!properties.get("zoom").equals(rendererLastState.get("zoom"))) {
            return true;
        }
        LocationType current = ((LocationType) properties.get("center")).getNewAbsoluteLatLonDepth();
        LocationType last = (LocationType) rendererLastState.get("center") == null ? new LocationType(0, 0)
                : (LocationType) rendererLastState.get("center");
        double[] offset = current.getDistanceInPixelTo(last, (Integer) properties.get("zoom"));
        if (Math.abs(offset[0]) > offScreenBufferPixel || Math.abs(offset[1]) > offScreenBufferPixel) {
            return true;
        }
        if (!properties.get("width").equals(rendererLastState.get("width"))) {
            return true;
        }
        if (!properties.get("height").equals(rendererLastState.get("height"))) {
            return true;
        }
        if (!properties.get("rotation").equals(rendererLastState.get("rotation"))) {
            return true;
        }
        return false;
    }

    private boolean getScale(int purpose, double srendScale) {
        // System.out.println(srendScale);
        switch (purpose) {
            case 1:
                if (srendScale >= 1499999)
                    return true;
                else
                    return false;
            case 2: // srendScale > 350000 && srendScale < 1499999
                if (srendScale < 1499999)
                    return true;
                else
                    return false;
            case 3: // srendScale > 90000 && srendScale < 349999
                if (srendScale < 349999)
                    return true;
                else
                    return false;
            case 4:// srendScale > 22000 && srendScale < 89999
                if (srendScale < 89999)
                    return true;
                else
                    return false;
            case 5:// srendScale > 4000 && srendScale < 21999
                if (srendScale < 21999)
                    return true;
                else
                    return false;
            case 6:
                if (srendScale < 4000)
                    return true;
                else
                    return false;
            default:
                return false;
        }
    }

    /**
     * @return the cacheValid
     */
    public boolean isCacheValid() {
        return cacheValid;
    }

    /**
     * @param cacheValid the cacheValid to set
     */
    public void setCacheValid(boolean cacheValid) {
        this.cacheValid = cacheValid;
        if (!cacheValid && rend2D != null) {
            rend2D.repaint();
            if (S57Factory.DEBUG) {
                System.out.println(">>>>>>>>>>>> Cache invalig, repainting.");
                System.out.flush();
            }
        }
    }

    public List<S57Map> getCurrentMaps() {
        return currentMaps;
    }

    // Helpers

    private boolean intersectCovsWithRenderer(List<S57Object> covs, Path2D bounds) {
        for (S57Object cov : covs) {
            if (cov.intersects(bounds)) {
                return true;
            }
        }
        return false;
    }

    private boolean mapContainsRenderer(List<S57Object> covs, Path2D srend) {
        for (S57Object cov : covs) {
            if (cov.contains(srend)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create the coverage painter for a given map
     * 
     * @param map
     * @return
     */
    private List<S57ObjectPainter> createCoveragePainter(List<S57Object> covs) {
        List<S57ObjectPainter> painters = new ArrayList<S57ObjectPainter>();
        for (S57Object cov : covs) {
            S57ObjectPainter covPainter = S57ObjectPainter.forge(cov, mc, this.resources);
            Instruction LS = Instruction.forge(InstructionType.LS, "SOLD,5,LITGN");
            List<Instruction> temp = new ArrayList<Instruction>();
            temp.add(LS);
            covPainter.setInstructions(temp);
            covPainter.setPriority(9);
            painters.add(covPainter);
        }
        return painters;
    }

    /**
     * Builds boundaries for renderer vision in GPS
     * 
     * @param srend
     * @param small if true doesnt add the offscreen buffer
     * @return
     */
    private Path2D getRendererBoundsGPS(StateRenderer2D srend, boolean small) {
        int buffer = offScreenBufferPixel;
        if (small)
            buffer = (int) (((srend.getWidth() * 0.2) + (srend.getHeight() * 0.2)) / 2);
        LocationType topLeft = srend.getRealWorldLocation(new Point2D.Double(0 - buffer, 0 - buffer));
        LocationType topRight = srend
                .getRealWorldLocation(new Point2D.Double(srend.getWidth() - 1 + buffer, 0 - buffer));
        LocationType bottomRight = srend.getRealWorldLocation(
                new Point2D.Double(srend.getWidth() - 1 + buffer, srend.getHeight() - 1 + buffer));
        LocationType bottomLeft = srend
                .getRealWorldLocation(new Point2D.Double(0 - buffer, srend.getHeight() - 1 + buffer));

        Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        path.moveTo(topLeft.getLatitudeDegs(), topLeft.getLongitudeDegs());
        path.lineTo(topRight.getLatitudeDegs(), topRight.getLongitudeDegs());
        path.lineTo(bottomRight.getLatitudeDegs(), bottomRight.getLongitudeDegs());
        path.lineTo(bottomLeft.getLatitudeDegs(), bottomLeft.getLongitudeDegs());
        path.closePath();

        return path;
    }

    public static double[] rotate(double angleRadians, double x, double y, boolean clockwiseRotation) {
        double sina = Math.sin(angleRadians);
        double cosa = Math.cos(angleRadians);
        double[] xy = new double[] { 0.0, 0.0 };
        if (clockwiseRotation) {
            xy[0] = x * cosa + y * sina;
            xy[1] = -x * sina + y * cosa;
        } else {
            xy[0] = x * cosa - y * sina;
            xy[1] = x * sina + y * cosa;
        }

        return xy;
    }
}
