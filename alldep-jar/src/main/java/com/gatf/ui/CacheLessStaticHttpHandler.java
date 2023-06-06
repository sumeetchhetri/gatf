/*
    Copyright 2013-2019, Sumeet Chhetri
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
    http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
package com.gatf.ui;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
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
	
	private boolean isAuthMode = false;
	
	public CacheLessStaticHttpHandler(boolean isAuthMode, String docRoot) {
		this.isAuthMode = isAuthMode;
    	if (docRoot == null) {
            throw new NullPointerException("docRoot can't be null");
        }

        final File file = new File(docRoot);
        this.docRoots.add(file);
	}
	
	public CacheLessStaticHttpHandler(boolean isAuthMode, String... docRoots) {
		this.isAuthMode = isAuthMode;
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
        
        if(isAuthMode && resource.getAbsolutePath().endsWith("index.html")) {
        	String filecontents = FileUtils.readFileToString(resource, "UTF-8");
        	filecontents = filecontents.replace("id=\"loginform\" class=\"log-form hidden\"", "id=\"loginform\" class=\"log-form\"");
        	filecontents = filecontents.replace("id=\"toptoolbar\" class=\"", "id=\"toptoolbar\" class=\"hidden ");
        	filecontents = filecontents.replace("id=\"subnav\" class=\"", " id=\"subnav\" class=\"hidden ");
        	filecontents = filecontents.replace("id=\"myModal\" class=\"", "id=\"myModal\" class=\"hidden ");
        	filecontents = filecontents.replace(" id=\"main\" class=\"\"", " id=\"main\" class=\"hidden\"");
        	File tmpIndxRes = File.createTempFile("indx__", ".html");
        	tmpIndxRes.delete();
        	FileUtils.writeStringToFile(tmpIndxRes, filecontents, "UTF-8");
        	resource = tmpIndxRes;
        }
        
        sendFile(response, resource);

        return true;
    }
}
