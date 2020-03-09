package org.ops4j.pax.url.mvn.s3;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.plexus.util.xml.Xpp3Dom;

public final class S3ServerConfigHelper {

    private S3ServerConfigHelper() {
    }

    public static Xpp3Dom buildS3ServerConfiguration(String region, String endpoint) {
        if (null == region && null == endpoint) {
            return null;
        }

        Xpp3Dom configDom = new Xpp3Dom("configuration");

        if (null != region) {
            Xpp3Dom regionDom = new Xpp3Dom(S3Constants.PROPERTY_REGION);
            regionDom.setValue(region);
            configDom.addChild(regionDom);
        }

        if (null != endpoint) {
            Xpp3Dom endpointDom = new Xpp3Dom(S3Constants.PROPERTY_ENDPOINT);
            endpointDom.setValue(endpoint);
            configDom.addChild(endpointDom);
        }

        return configDom;
    }

    public static Map<String, String> getS3ServerConfiguration(Xpp3Dom configuration) {
        Map<String, String> config = new HashMap<String, String>();

        Xpp3Dom regionNode = configuration.getChild(S3Constants.PROPERTY_REGION);
        if (null != regionNode) {
            config.put(S3Constants.PROPERTY_REGION, regionNode.getValue());
        }

        Xpp3Dom endpointNode = configuration.getChild(S3Constants.PROPERTY_ENDPOINT);
        if (null != endpointNode) {
            config.put(S3Constants.PROPERTY_ENDPOINT, endpointNode.getValue());
        }

        return config;
    }
}
