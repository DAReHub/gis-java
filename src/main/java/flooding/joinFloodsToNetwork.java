package flooding;

import gis.GpkgReader;
import org.apache.log4j.Logger;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;

import java.io.*;
import java.util.*;

public class joinFloodsToNetwork {

    private final static Logger log = Logger.getLogger(joinFloodsToNetwork.class);

    public static void main(String[] args) throws FactoryException, IOException {

        Map<Integer, SimpleFeature> network = GpkgReader.readFeatures(new File("network_v3.13.gpkg"),"edgeID");

        // Buffered links feature type (for writing geopackage / debugging)
        final SimpleFeatureType TYPE = createBufferedLinkFeatureType();
        final SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);
        final DefaultFeatureCollection bufferedLinks = new DefaultFeatureCollection("bufferedLinks",TYPE);

        // Depth assumptions
        Map<String,Double> depths = new HashMap<>();
        depths.put("0.00 - 0.15",0.15);
        depths.put("0.15 - 0.30",0.3);
        depths.put("0.30 - 0.60",0.6);
        depths.put("0.60 - 0.90",0.9);
        depths.put("0.90 - 1.20",1.2);
        depths.put("> 1.20",1.5);

        // Read in floods
        Set<SimpleFeature> floods = GpkgReader.readFeatures(new File("depth30.gpkg"));
        SpatialIndex qt = createQuadtree(floods);

        HashMap<Integer, Double> linkDepths = new HashMap<>(network.size());

        for(Map.Entry<Integer, SimpleFeature> e : network.entrySet()) {
            Geometry geom = (Geometry) e.getValue().getDefaultGeometry();
            Geometry buf = geom.buffer((double) e.getValue().getAttribute("avg_wdt_mp") / 2.);

            // Add to buffered features set
            featureBuilder.add(buf);
            featureBuilder.add(e.getKey());
            bufferedLinks.add(featureBuilder.buildFeature(null));

            // Intersect with floods
            List<SimpleFeature> elements = qt.query(buf.getEnvelopeInternal());
            double maxDepth = 0.;
            for (SimpleFeature flood : elements) {
                if(((Geometry) flood.getDefaultGeometry()).intersects(buf)) {
                    double thisDepth = depths.get((String) flood.getAttribute("depth"));
                    if (thisDepth > maxDepth) {
                        maxDepth = thisDepth;
                    }
                }
                linkDepths.put(e.getKey(),maxDepth);
            }
        }

        // Write as CSV
        PrintWriter pw = openFileForSequentialWriting(new File("floods.csv"),false);
        assert pw != null;

        pw.println("edgeID,depth");
        for(Map.Entry<Integer,Double> e : linkDepths.entrySet()) {
            pw.println(e.getKey() + "," + e.getValue());
        }
        pw.close();

        // Write buffered network geopackage
        File outputEdgesFile = new File("network_buf.gpkg");
        if(outputEdgesFile.delete()) {
            log.warn("File " + outputEdgesFile.getAbsolutePath() + " already exists. Overwriting.");
        }
        GeoPackage out = new GeoPackage(outputEdgesFile);
        out.init();
        FeatureEntry entry = new FeatureEntry();
        entry.setDescription("bufferedLinks");
        out.add(entry,bufferedLinks);
        out.createSpatialIndex(entry);
        out.close();

    }

    private static SimpleFeatureType createBufferedLinkFeatureType() throws FactoryException {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("links");
        builder.setCRS(CRS.decode("EPSG:27700"));

        // add attributes in order
        builder.add("path", Polygon.class);
        builder.add("edgeID",Integer.class);

        return builder.buildFeatureType();
    }

    private static SpatialIndex createQuadtree(Collection<SimpleFeature> features) {
        log.info("Creating spatial index");
        SpatialIndex zonesQt = new Quadtree();
        for (SimpleFeature feature : features) {
            Geometry geom = (Geometry) (feature.getDefaultGeometry());
            if(!geom.isEmpty()) {
                Envelope envelope = ((Geometry) (feature.getDefaultGeometry())).getEnvelopeInternal();
                zonesQt.insert(envelope, feature);
            } else {
                throw new RuntimeException("Null geometry for zone " + feature.getID());
            }
        }
        return zonesQt;
    }


    public static PrintWriter openFileForSequentialWriting(File outputFile, boolean append) {
        if (outputFile.getParent() != null) {
            File parent = outputFile.getParentFile();
            parent.mkdirs();
        }

        try {
            FileWriter fw = new FileWriter(outputFile, append);
            BufferedWriter bw = new BufferedWriter(fw);
            return new PrintWriter(bw);
        } catch (IOException var5) {
            log.info("Could not open file <" + outputFile.getName() + ">.");
            return null;
        }
    }

}
