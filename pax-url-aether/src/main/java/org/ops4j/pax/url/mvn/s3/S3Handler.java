package org.ops4j.pax.url.mvn.s3;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import org.osgi.service.url.AbstractURLStreamHandlerService;

public class S3Handler extends AbstractURLStreamHandlerService {

    @Override
    public URLConnection openConnection(URL u) throws IOException {
        return null;
    }

}
