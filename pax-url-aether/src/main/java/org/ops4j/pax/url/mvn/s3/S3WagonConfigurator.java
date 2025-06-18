package org.ops4j.pax.url.mvn.s3;

import java.util.Map;

import com.gkatzioura.maven.cloud.s3.S3StorageWagon;

import org.apache.maven.wagon.Wagon;
import org.eclipse.aether.transport.wagon.WagonConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class S3WagonConfigurator implements WagonConfigurator {

    private static final Logger LOG = LoggerFactory.getLogger(S3WagonConfigurator.class);

    @Override
    public void configure(Wagon wagon, Object configuration) throws Exception {
        if (wagon instanceof S3StorageWagon) {
            if (null == configuration) {
                LOG.warn("no server configuration for S3 Wagon");
                return;
            }
            if (!(configuration instanceof Map<?, ?>)) {
                LOG.warn("bad server configuration for S3 Wagon: " + configuration.getClass().getName());
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, String> config = (Map<String, String>) configuration;
            if (config.isEmpty()) {
                LOG.warn("empty server configuration for S3 Wagon");
                return;
            }
            LOG.debug("using S3 Wagon configuration " + config);

            S3StorageWagon s3Wagon = (S3StorageWagon) wagon;
            s3Wagon.setRegion(config.get(S3Constants.PROPERTY_REGION));
            s3Wagon.setEndpoint(config.get(S3Constants.PROPERTY_ENDPOINT));
        }
    }

}
