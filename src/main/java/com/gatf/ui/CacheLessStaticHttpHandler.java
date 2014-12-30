package com.gatf.ui;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.StaticHttpHandler;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.utils.ArraySet;

/**
 * Static HTTP Handler which does not cache files.
 * 
 * @author Sumeet Chhetri
 *
 */
public class CacheLessStaticHttpHandler extends StaticHttpHandler {
	
	private static final Logger LOGGER = Grizzly.logger(CacheLessStaticHttpHandler.class);

	protected final ArraySet<File> docRoots = new ArraySet<File>(File.class);
	
	public CacheLessStaticHttpHandler(String... docRoots) {
		if (docRoots != null) {
            for (String docRoot : docRoots) {
            	if (docRoot == null) {
                    throw new NullPointerException("docRoot can't be null");
                }

                final File file = new File(docRoot);
                this.docRoots.add(file);
            }
        }
	}

	/**
     * {@inheritDoc}
     */
    @Override
    protected boolean handle(final String uri,
            final Request request,
            final Response response) throws Exception {

        boolean found = false;

        final File[] fileFolders = docRoots.getArray();
        if (fileFolders == null) {
            return false;
        }

        File resource = null;

        for (int i = 0; i < fileFolders.length; i++) {
            final File webDir = fileFolders[i];
            // local file
            resource = new File(webDir, uri);
            final boolean exists = resource.exists();
            final boolean isDirectory = resource.isDirectory();

            if (exists && isDirectory) {
                final File f = new File(resource, "/index.html");
                if (f.exists()) {
                    resource = f;
                    found = true;
                    break;
                }
            }

            if (isDirectory || !exists) {
                found = false;
            } else {
                found = true;
                break;
            }
        }

        if (!found) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "File not found {0}", resource);
            }
            return false;
        }

        assert resource != null;
        
        // If it's not HTTP GET - return method is not supported status
        if (!Method.GET.equals(request.getMethod())) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "File found {0}, but HTTP method {1} is not allowed",
                        new Object[] {resource, request.getMethod()});
            }
            response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
            response.setHeader(Header.Allow, "GET");
            return true;
        }
        
        pickupContentType(response, resource.getPath());
        
        sendFile(response, resource);

        return true;
    }
}
