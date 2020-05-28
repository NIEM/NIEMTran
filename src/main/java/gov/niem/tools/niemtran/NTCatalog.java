/* 
 * NOTICE
 *
 * This software was produced for the U. S. Government
 * under Basic Contract No. W56KGU-18-D-0004, and is
 * subject to the Rights in Noncommercial Computer Software
 * and Noncommercial Computer Software Documentation
 * Clause 252.227-7014 (FEB 2012)
 * 
 * Copyright 2019 The MITRE Corporation.
 */

package gov.niem.tools.niemtran;

import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.xml.resolver.Catalog;
import org.apache.xml.resolver.CatalogException;
import org.apache.xml.resolver.CatalogManager;
import org.apache.xml.resolver.helpers.FileURL;
import org.apache.xml.resolver.readers.CatalogReader;

/**
 * A class derived from Apache's XML Commons Resolver. This kind of catalog
 * object can report all the catalog files it has attempted to load. It 
 * logs a warning if called to parse a catalog from an InputStream or
 * from a non-local URL.
 * 
 * <p>The purpose of this class is to provide visibility into the workings
 * of XML Catalog resolution to developers.  Not much use for this
 * class in production code.
 * 
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class NTCatalog extends Catalog {
     
    private static List<String> allCatalogs = new ArrayList<>();
    
    public NTCatalog () {
        super();
    }
                      
    public NTCatalog(CatalogManager manager) {
        super(manager);
    }
    
    /**
     * Clears the static class list of catalog files that have been
     * requested. Called by the catalog manager when it creates a new NTCatalog
     * object to parse the initial list of catalog files.
     */
    public void clearCatalogList() {
        allCatalogs = new ArrayList<>();
    }

    /**
     * Returns a list of parse results for every requested catalog file. One
     * string per requested file, formatted as:
     * <p><i><code>    message : fileURI</code></i>
     * <p>The possible message substrings are:
     * <ul><li>OK
     * <li>catalog file does not exist
     * <li>catalog file cannot be read
     * <li>catalog parsing failed</ul>
     * @return list of catalog file parse results
     */
    public List<String> parsingResults () {
        return allCatalogs;
    }
    
    /**
     * Parse a catalog provided as an InputStream (not supported). 
     * These catalogs are not included in the parse results.
     * Logs a warning message.
     * @param mimeType
     * @param is
     * @throws IOException
     * @throws CatalogException 
     */
    @Override
    public synchronized void parseCatalog(String mimeType, InputStream is)
            throws IOException, CatalogException {
        Logger.getLogger("NTCatalog").log(Level.WARNING, "NTCatalog.parseCatalog(String,InputStream) can't report parsing result");
        super.parseCatalog(mimeType,is);
    }
    
    /**
     * Parses a catalog provided as a URL for a (possibly) non-local resource 
     * (not supported).
     * These catalogs are not included in the parse results.
     * Logs a warning message.
     * @param aUrl
     * @throws IOException 
     */
    @Override
    public synchronized void parseCatalog(URL aUrl) throws IOException {
        String p = aUrl.getProtocol();
        if (!"file".equals(p)) {
            Logger.getLogger("NTCatalog").log(Level.WARNING, "NTCatalog.parseCatalog(URL) called for non-local resource {0}", aUrl);
            super.parseCatalog(aUrl);
        }
        else {
            String fp = aUrl.getFile();
            parseCatalogFile(fp);
            parsePendingCatalogs();
        }
        
    }

    /**
     * Parse a named catalog file. Same code as the method in the base class,
     * modified only to record the parsing result at the end.
     * @param fileName 
     */
    @Override
    protected synchronized void parseCatalogFile(String fileName) {     // MODIFIED: doesn't throw exceptions

        // The base-base is the cwd. If the catalog file is specified
        // with a relative path, this assures that it gets resolved
        // properly...
        try {
            // tack on a basename because URLs point to files not dirs
            catalogCwd = FileURL.makeURL("basename");
        } catch (MalformedURLException e) {
            String userdir = System.getProperty("user.dir");
            userdir = userdir.replace('\\', '/');
            catalogManager.debug.message(1, "Malformed URL on cwd", userdir);
            catalogCwd = null;
        }

        // The initial base URI is the location of the catalog file
        try {
            base = new URL(catalogCwd, fixSlashes(fileName));
        } catch (MalformedURLException e) {
            try {
                base = new URL("file:" + fixSlashes(fileName));
            } catch (MalformedURLException e2) {
                catalogManager.debug.message(1, "Malformed URL on catalog filename",
                        fixSlashes(fileName));
                base = null;
            }
        }

        catalogManager.debug.message(2, "Loading catalog", fileName);
        catalogManager.debug.message(4, "Default BASE", base.toString());

        fileName = base.toString();

        DataInputStream inStream = null;
        boolean parsed = false;
        boolean notFound = false;
        boolean notRead = false;        // MODIFIED; separate handling for IOException

        for (int count = 0; !parsed && count < readerArr.size(); count++) {
            CatalogReader reader = (CatalogReader) readerArr.get(count);

            try {
                notFound = false;
                inStream = new DataInputStream(base.openStream());
            } catch (FileNotFoundException fnfe) {
                // No catalog; give up!
                notFound = true;
                break;
            } catch (IOException ex) {  // MODIFIED: handle IOException
                notRead = true;
                break;
            }

            try {
                reader.readCatalog(this, inStream);
                parsed = true;
            } catch (CatalogException ce) {
                if (ce.getExceptionType() == CatalogException.PARSE_FAILED) {
                    // give up!
                    break;
                } else {
                    // try again!
                }
            } catch (IOException ex) {  // MODIFIED: handle IOException
                notRead = true;
                break;
            }

            try {
                inStream.close();
            } catch (IOException e) {
                //nop
            }
        }
        
        // MODIFIED: Remember the load result
        if (!parsed) {
            if (notFound) {
                catalogManager.debug.message(3, "Catalog does not exist", fileName);
                allCatalogs.add(String.format("catalog file does not exist: %s", fileName));
            } else if (notRead) {
                catalogManager.debug.message(3, "Catalog file could not be read", fileName);
                allCatalogs.add(String.format("catalog file cannot be read: %s", fileName));
            }
            else {
                catalogManager.debug.message(1, "Failed to parse catalog", fileName);
                allCatalogs.add(String.format("catalog parsing failed: %s", fileName));                
            }
        }
        else {
            allCatalogs.add(String.format("OK: %s", fileName));
        } 
    }
}

