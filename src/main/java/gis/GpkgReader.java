package gis;

import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.geopkg.GeoPackage;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Tools for reading edges and nodes files produced by the JIBE WP2 team

public class GpkgReader {

    public static Map<Integer, SimpleFeature> readFeatures(File file, String id) {

        Map<Integer,SimpleFeature> features = new HashMap<>();

        try{
            GeoPackage geopkg = new GeoPackage(file);
            SimpleFeatureReader r = geopkg.reader(geopkg.features().get(0), null,null);
            while(r.hasNext()) {
                SimpleFeature feature = r.next();
                features.put((int) feature.getAttribute(id),feature);
            }
            r.close();
            geopkg.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return features;
    }

    public static Set<SimpleFeature> readFeatures(File file) {
        Set<SimpleFeature> features = new HashSet<>();

        try{
            GeoPackage geopkg = new GeoPackage(file);
            SimpleFeatureReader r = geopkg.reader(geopkg.features().get(0), null,null);
            while(r.hasNext()) {
                features.add(r.next());
            }
            r.close();
            geopkg.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return features;
    }

    private static File openFile(String filePath) {
        File file = new File(filePath);
        if(!file.exists()) {
            throw new RuntimeException("File " + filePath + " not found!");
        }
        return file;
    }

}
