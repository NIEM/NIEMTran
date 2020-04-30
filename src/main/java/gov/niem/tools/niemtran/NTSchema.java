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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import javax.xml.parsers.ParserConfigurationException;
import static org.apache.commons.lang3.StringUtils.getCommonPrefix;
import org.apache.xerces.impl.xs.util.StringListImpl;
import org.apache.xerces.util.URI;
import org.apache.xerces.xs.StringList;
import org.apache.xerces.xs.XSLoader;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSNamespaceItem;
import org.apache.xerces.xs.XSNamespaceItemList;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMError;
import org.w3c.dom.DOMErrorHandler;
import org.w3c.dom.DOMLocator;

/**
 * A class to construct a Xerces XSModel object for an XML schema defined
 * by local resources that are specified by two lists: <ul>
 * <li>a list of initial XML Schema documents (or namespace URIs)
 * <li>a list of XML Catalog documents</ul>
 * 
 * <p>
 * The schema thus specified is the schema constructed by:
 * <ul><li> Loading each initial schema document, and
 * <li>Resolving each initial namespace URI and loading the resulting local file, and
 * <li>Recursively loading the resource described by every <code>import</code>, 
 * <code>include</code>, and <code>redefine</code> element encountered, while
 * <li>Using the XML Commons Resolver constructed from the list of XML Catalog 
 * documents to resolve each namespace and schemaLocation URI.</ul>
 *
 * <p>
 * The class offers insight into the actions of Xerces as it assembles a
 * schema from schema documents. It reports the following initialization errors:<ul>
 * <li>Initial schema document files that cannot be read
 * <li>Initial namespace URIs that cannot be resolved to a readable file
 * <li>Catalog documents that are not valid XML Catalog documents
 * (including subordinate catalogs)</ul>
 *
 * <p>
 * The class also reports catalog resolutions and schema documents processed
 * during schema assembly.
 * <p>
 * Example usage: <pre><code>
 try {
     NTSchema s = new NTSchema();
 }
 catch (ParserConfigurationException ex) {
     Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "NTSchema bootstrap error", ex);
     System.exit(1);
 }
 s.addCatalogFile("catalog.xml");
 s.addSchemaFile("myschema.xsd");
 if (!s.initializationErrorMessages().isEmpty()) {
     System.out.println("Schema initialization errors");
     System.out.print(s.initializationErrorMessages();
 }
 XSModel xs = s.xmodel();
 System.out.println(xs == null ? "Schema construction FAILED" : "Schema construction SUCCEDED");
 if (!s.xsWarningMessages().isEmpty() || !s.xsConstructionMessages().isEmpty()) {
     System.out.println("Schema construction errors and warnings:"
     System.out.print(s.xsConstructionMessages());
     System.out.print(s.xsWarningMessages());
 }
 System.out.println("Catalog resolutions:");
 System.out.print(s.xsResolutionMessages());
 * </code></pre>
 *
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class NTSchema {

    protected ParserBootstrap parsers;
    private List<String> catalogFiles = new ArrayList<>();  // list of initial catalog files for resolver (as provided)
    private List<String> schemaFiles = new ArrayList<>();   // list of initial schema documents to load (as provided)
    private List<String> initialNSs = new ArrayList<>();    // list of namespaces to resolve for initial schema documents (as provided)
    private List<String> allSchemaFileURIs = null;          // list of initial schema documents & initial namespace URI resolutions
    private String schemaRootDir = null;                    // common path prefix of schema and catalog documents
    private NTCatalogResolver resolver = null;              // catalog resolver used for load checking & schema generation
    private XSModel xsmodel = null;                         // constructed XML schema object from Xerces
    private String initErrors = null;                       // initialization errors; eg. schema document not found
    private String xsConstructionMsgs = null;               // error and warning messages from Xerces
    private String xsNamespaceDocs = null;                  // namespaces constructed from schema documents
    
    /**
     * Constructs an empty Schema object. Add schema documents, namespace URIs,
     * and XML Catalog documents later, then check for import errors or generate
     * the schema XSModel, etc. All of the parser bootstrapping happens here, 
     * throwing an exception if any of it fails.
     * @throws ParserConfigurationException
     */
    public NTSchema() throws ParserConfigurationException {
        // Bootstrap the parsers when the first object is created
        parsers = new ParserBootstrap();
    }

    /**
     * Constructs a Schema object for the specified schema documents and catalog
     * files.
     * @param catalogFiles list of initial XML Catalog files
     * @param schemaOrNamespace list of initial schema documents or namespace URIs
     * @throws ParserConfigurationException
     */
    public NTSchema(List<String> catalogFiles, List<String> schemaOrNamespace)
            throws ParserConfigurationException {

        this();

        // Check and save the catalog files as canonical file URIs
        for (String cn : catalogFiles) {
            this.catalogFiles.add(canonicalFileURI(cn));
        }
        // Check and save the schema file names as canonical file URIs
        // Save schema namespace URIs separately
        for (String sn : schemaOrNamespace) {
            URI u = null;
            try {
                u = new URI(sn);
                if ("file".equals(u.getScheme())) {
                    sn = u.getPath();
                    u = null;
                } else {
                    initialNSs.add(u.toString());
                }
            } catch (URI.MalformedURIException ex) {
                // IGNORE
            }
            if (u == null) {
                schemaFiles.add(sn);
            }
        }
    }
    
    // ----- SCHEMA INITIALIZATION SETUP --------------------------------------

    /**
     * Add one catalog file to the list of catalogs used to resolve URIs. This
     * may change the schema, so initialization and schema construction must be reset.
     * @param cfn catalog file path
     */
    public void addCatalogFile(String cfn) {
        catalogFiles.add(canonicalFileURI(cfn));
        reset();
    }

    /**
     * Add one file to the list of initial schema documents. This changes the
     * schema, so initialization and schema construction must be reset.
     * @param sfn schema file path
     */
    public void addSchemaFile(String sfn) {
        schemaFiles.add(sfn);
        reset();
    }

    /**
     * Add one URI to the list of initial namespace URIs specifying initial schema
     * documents. This changes the schema, so initialization and schema
     * assembly must be reset.
     * @param sns namespace URI string
     */
    public void addSchemaNamespaceURI(String sns) {
        initialNSs.add(sns);
        reset();
    }
    
    // Clear all the cached results
    protected void reset() {
        initErrors = null;
        xsmodel = null;
        xsConstructionMsgs = null;
    }
    
    // ----- CATALOG RESOLVER RESULTS ----------------------------

    /**
     * Returns a list of file URIs for the initial list of catalog files
     * used to specify the schema. 
     * @return list of provided catalog file paths
     */
    public List<String> getInitialCatalogFiles() {
        return catalogFiles;
    }

    /**
     * Returns a list of file URIs for all the initial and subordinate
     * catalog files requested during catalog file parsing
     * @return list of all catalog files requested
     */
    public List<String> getAllCatalogFiles() {
        initialize();
        return resolver.allCatalogFiles();
    }

    /**
     * Returns a list of file URIs for the valid catalog files loaded
     * during catalog file parsing.
     * @return list of all valid catalog files
     */
    public List<String> getAllValidCatalogFiles() {
        initialize();
        return resolver.allValidCatalogFiles();
    }
    
    /**
     * Returns the result of parsing the catalog files used to specify the
     * schema. Includes result of parsing subordinate catalogs.
     * @return list of catalog parsing results
     */
    public List<String> catalogParsingResults() {
        initialize();
        return resolver().validationResults();
    }

    /**
     * Returns any errors encountered in parsing the catalog files used to
     * specify the schema. Includes errors found while parsing subordinate
     * catalogs.
     * @return list of catalog parsing errors; empty list if none
     */
    public List<String> catalogParsingErrors() {
        initialize();
        return resolver().validationErrors();
    }    

    /**
     * Returns the catalog resolver used to construct the schema.
     * @return catalog resolver object
     */
    public NTCatalogResolver resolver() {
        initialize();
        return resolver;
    }
    
    // ----- SCHEMA INITIALIZATION RESULTS ---------------------------
    
    /**
     * Returns a list of initial schema documents provided to specify the schema.
     * @return list of initial schema documents
     */
    public List<String> getInitialSchemaFiles() {
         return schemaFiles;
    }
       
    /**
     * Returns a list of file: URIs, one for each initial schema document 
     * that was provided as a file name or namespace URI.
     * @return list of initial schema document file URIs
     */
    public List<String> getAllInitialSchemas() {
        initialize();
        return allSchemaFileURIs;
    }
    
    /**
     * Returns any initialization errors for the schema. Includes any errors in
     * the catalog files.
     * @return schema initialization errors; zero-length if none
     */
    public String initializationErrorMessages() {
        initialize();
        return initErrors;
    }

    private void initialize() {
        if (initErrors != null) {
            return;
        }
        StringBuilder msg = new StringBuilder();
        resolver = new NTCatalogResolver();
        if (catalogFiles.size() > 0) {
            resolver.setCatalogList(catalogFiles.toArray(new String[0]));
        }
        resolver.validationErrors().forEach((em) -> { msg.append(em).append("\n"); } );
        
        // Convert initial schema files to URIs, ensure they are readable
        allSchemaFileURIs = new ArrayList<>();
        for (String s : schemaFiles) {
            String suri = null;
            File f = new File(s);
            if (f.canRead()) {
                suri = canonicalFileURI(f);
            }
            if (suri != null) {
                allSchemaFileURIs.add(suri);
            } else {
                msg.append(String.format("can't read schema file %s\n", s));
            }
        }
        // Resolve initial namespaces, ensure they map to readable files
        for (String s : initialNSs) {
            String rv;
            try {
                rv = resolver.resolveURI(s);
                if (rv == null) {
                    msg.append(String.format("can't resolve initial namespace %s\n", s));
                } else if (!rv.startsWith("file:")) {
                    msg.append(String.format("initial namespace %s resolves to non-local resource %s\n", s, rv));
                } else {
                    allSchemaFileURIs.add(rv);
                }
            } catch (IOException ex) {
                msg.append(String.format("invalid URI syntax for initial namespace %s\n", s));
            }
        }
        if (allSchemaFileURIs.size() < 1) {
            msg.append("no readable schema documents provided\n");
        }
        initErrors = msg.toString();
    }
    
    
    // ------- SCHEMA XSMODEL CONSTRUCTION AND RESULTS ----------------------
    
    /**
     * Returns the longest common path of all schema and catalog documents
     * used to construct the schema.
     * @return schema root directory
     */
    public String schemaRoot () {
        if (schemaRootDir != null) {
            return schemaRootDir;
        }
        xsmodel();
        XSNamespaceItemList nsil = xsmodel.getNamespaceItems();        
        List<String> sdocs = new ArrayList<>(getAllCatalogFiles());
        for (int i = 0; i < nsil.getLength(); i++) {
            XSNamespaceItem nsi = nsil.item(i);
            String ns = nsi.getSchemaNamespace();  
            if (!W3C_XML_SCHEMA_NS_URI.equals(ns)) {
                StringList docs = nsi.getDocumentLocations();
                for (int di = 0; di < docs.getLength(); di++) {
                    sdocs.add(docs.item(di));
                }
            }
        }
        schemaRootDir = getCommonPrefix(sdocs.toArray(new String[0]));
        return schemaRootDir;
    }
   
    /**
     * Returns the namespaces in the constructed schema, together
     * with the schema documents used to construct content for each namespace.
     * Convenience function for people who don't want to extract this from
     * the XSModel object.
     * @return namespaces and schema documents
     */
    public String xsNamespaceDocuments () {
        if (xsNamespaceDocs !=  null) {
            return xsNamespaceDocs;
        }
        int schemaRootLen = schemaRoot().length();
        XSNamespaceItemList nsil = xsmodel.getNamespaceItems();
        StringBuilder msgs = new StringBuilder();
        msgs.append(String.format("Schema root directory: %s\n", schemaRootDir));
        for (int i = 0; i < nsil.getLength(); i++) {
            XSNamespaceItem nsi = nsil.item(i);
            String ns = nsi.getSchemaNamespace();  
            if (!W3C_XML_SCHEMA_NS_URI.equals(ns)) {
                StringList docs = nsi.getDocumentLocations();
                if (docs.getLength() > 1) {
                    StringBuilder msg = new StringBuilder();
                    msg.append(String.format("%s <- MULTIPLE DOCUMENTS\n", ns));
                    for (int di = 0; di < docs.getLength(); di++) {
                        msg.append(String.format("  %s\n", docs.item(di).substring(schemaRootLen)));
                    }
                } else if (docs.getLength() == 1) {
                    msgs.append(String.format("%s <- %s\n", ns, docs.item(0).substring(schemaRootLen)));
                } else {
                    msgs.append(String.format("%s <- NOTHING???\n", ns));
                }
            
            }
        }
        xsNamespaceDocs = msgs.toString();
        return xsNamespaceDocs;
    }
     
    /**
     * Return results of all XML Catalog resolution operations performed during 
     * schema construction
     * @return resolution messages
     */
    public String xsResolutionMessages () {
        xsmodel();
        StringBuilder msgs = new StringBuilder();
        resolver().resolutionMessages().forEach((rm) -> { msgs.append(rm).append("\n"); } );
        return msgs.toString();
    }
    
    /**
     * Return schema construction messages produced by Xerces
     * @return schema construction messages
     */
    public String xsConstructionMessages () {
        xsmodel();
        return xsConstructionMsgs;
    }

   
    /**
     * Returns the XSModel constructed by Xerces for this schema.
     * @return schema XSModel object
     */
    public XSModel xsmodel () {
        if (xsConstructionMsgs != null) { 
            return xsmodel;
        }
        initialize();
        StringBuilder msgs = new StringBuilder();
        SchemaErrorHandler ehandler = new SchemaErrorHandler(msgs);
        XSLoader loader = parsers.xsLoader();   // don't reuse these, they keep state    
        DOMConfiguration config = loader.getConfig();
        config.setParameter("validate", true);
        config.setParameter("resource-resolver", resolver());
        config.setParameter("error-handler",ehandler);
        resolver().resetResolutions();
        StringList slist = new StringListImpl(
                allSchemaFileURIs.toArray(new String[0]),
                allSchemaFileURIs.size());
        xsmodel = loader.loadURIList(slist);
        if (xsmodel == null) {
            msgs.append("Xerces xsloader returned null\n");
        }
        xsConstructionMsgs = msgs.toString();
        return xsmodel;
    }
   
    private class SchemaErrorHandler implements DOMErrorHandler {
        private final StringBuilder msgs;
        SchemaErrorHandler(StringBuilder msgs) {
            super();
            this.msgs = msgs;
        }
        @Override
        public boolean handleError(DOMError error) {
            short sevCode = error.getSeverity();
            String sevstr;
            if (sevCode == DOMError.SEVERITY_FATAL_ERROR) { sevstr = "[fatal]"; } 
            else if (sevCode == DOMError.SEVERITY_ERROR)  { sevstr = "[error]"; } 
            else { sevstr = "[warn] "; }

            DOMLocator loc = error.getLocation();
            String uri = loc.getUri();
            String fn  = "";
            if (uri != null) {
                int index = uri.lastIndexOf('/');
                if (index != -1) {
                    fn = uri.substring(index + 1)+":";
                }
            }
            msgs.append(String.format("xerces%s %s %d:%d %s\n", 
                    sevstr, 
                    fn, 
                    loc.getLineNumber(),
                    loc.getColumnNumber(),
                    error.getMessage()));
            return true;
        }
        @Override
        public String toString() {
            return msgs.toString();
        }     
    }

    
    // ------------ STATIC HELPER METHODS  ----------------------------------    

    static String canonicalFileURI(File f) {
        String suri = null;
        try {
            File cf = f.getCanonicalFile();
            java.net.URI u = cf.toURI();
            suri = u.toString();
        } catch (IOException ex) {
            // IGNORE
        }
        return suri;
    }

    static String canonicalFileURI(String fn) {
        File f = new File(fn);
        return canonicalFileURI(f);
    }

    static String canonicalFileURI(String ancestor, String path) {
        File fp = uriToFile(path);
        if (fp == null) {
            fp = new File(path);
        }
        if (!fp.isAbsolute()) {
            File pf = uriToFile(ancestor);
            pf = pf.getParentFile();
            path = fp.getPath();
            fp = new File(pf, path);
        }
        return canonicalFileURI(fp);
    }

    static File uriToFile(String uri) {
        File f = null;
        if (uri != null && uri.startsWith("file:")) {
            uri = uri.substring(5);
            f = new File(uri);
        }
        return f;
    }
    
    // Extract exception reason in parenthesis, if it's there
    static String exceptionReason (Exception ex) {
        String rmsg = ex.getMessage();
        int px = rmsg.indexOf("(");
        if (px >= 0) {
            rmsg = rmsg.substring(px);
        }
        return rmsg;
    }

}

