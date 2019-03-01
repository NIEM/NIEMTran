/*
 * Copyright 2019 The MITRE Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package niemtool;

import java.io.File;
import java.io.IOException;
import static java.lang.Math.min;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.apache.xerces.impl.xs.util.StringListImpl;
import org.apache.xerces.util.URI;
import org.apache.xerces.xs.StringList;
import org.apache.xerces.xs.XSImplementation;
import org.apache.xerces.xs.XSLoader;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSNamespaceItem;
import org.apache.xerces.xs.XSNamespaceItemList;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMError;
import org.w3c.dom.DOMErrorHandler;
import org.w3c.dom.DOMLocator;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A class to represent an XML schema defined by local resources that are
 * specified by two lists: <ul>
 * <li>a list of initial XML Schema documents (or namespace URIs)
 * <li> a list of XML Catalog documents</ul>
 * The schema thus specified is the schema constructed by:
 * <ul><li> Loading each initial schema document, and
 * <li>Resolving each initial namespace URI and loading the resulting local file, and
 * <li>Recursively loading the local resource (non-local resources are flagged
 * as errors) described by every <code>import</code>, <code>include</code>, and 
 * <code>redefine</code> element encountered, while
 * <li>Using the list of XML Catalog documents to resolve each namespace and
 * schemaLocation URI.</ul>
 *
 * <p>
 * The class is intended for development, not production. It supplies details about 
 * the operation of the Xerces parser and the Apache Commons XML resolver during 
 * schema construction.  It also reports inconsistencies and ambiguities in the 
 * <code>import</code>, <code>include</code>, and <code>redefine</code> elements,
 * under the assumption that <ul>
 * <li>the schema should be entirely constructed from local schema documents 
 * <li>each schema component should have a namespace
 * <li>each namespace should be constructed from a single schema document
 * <li>in <code>import</code> elements, the resolved <code>namespace</code> and
 * <code>schemaLocation</code> attributes should point to the same document</ul>
 * 
 * <p>
 * This class can be used for schema checking in three ways: <ol>
 *
 * <li> Initialization checking:
 *
 * <ul><li>Can all of the initial schema document files be read?
 * <li>Can all of the initial namespaces be resolved to a readable file?
 * <li>Are all of the catalog files readable and valid XML Catalog documents?</ul>
 *
 * <p>
 * <li> Schema assembly. Parses each schema document (using vanilla
 * DOM, not the Xerces XML Scheme API), following every <code>import</code>,
 * <code>include</code>, and <code>redefine</code> element, checking each for 
 * completeness and consistency. Reports the following findings:<ul>
 *
 * <lI>namespace URI resolves to non-local resource
 * <li>schemaLocation resolves to non-local resource
 * <li>resolved namespace != resolved schemaLocation
 * <li>can't determine schema document to be loaded
 * <li>can't parse schema document
 * <li>can't read file
 * <li>target namespace != expected namespace
 * <li>namespace already loaded from a different file
 * <li>namespace URI does not resolve
 * <li>no namespace attribute
 * <li>no schemaLocation attribute</ul>
 *
 * <p>
 * <li> Schema construction. Uses the Xerces XML Schema API to construct
 * the XSModel object, reports any errors returned by Xerces. Also reports
 * the result of all XML Catalog resolutions performed during construction.</ol>
 *
 * <p>
 * Example usage:
 * <pre><code>
 try {
     NTSchema s = new NTSchema();
 }
 catch (ParserConfigurationException ex) {
     Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "NTSchema bootstrap error", ex);
     System.exit(1);
 }
 s.addCatalogFile("catalog.xml");
 s.addSchemaFile("myschema.xsd");
 if (!s.initializationErrorMessages().equals("")) {
     System.out.println("Schema initialization errors");
     System.out.print(s.initializationErrorMessages();
     System.exit(1);
 }
 System.out.println("Schema root directory: " + s.schemaRootDirectory();
 if (!s.assemblyMessages().equals("")) {
     System.out.println("Schema assembly findings:");
     System.out.print(s.assemblyWarningMessages());
 }
 XSModel xs = s.xmodel();
 System.out.println(xs == null ? "Schema construction FAILED" : "Schema construction SUCCEDED");
 if (!s.xsWarningMessages().equals("") {
     System.out.println("Schema construction errors and warnings:"
     System.out.print(s.xsWarningMessages());
 }</code></pre>
 *
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */
public class NTSchema {
    
    protected static SAXParserFactory sfact = null;          // bootstrap for SAX parser
    protected static SAXParser saxp = null;                  // reusable SAX parser, for schema assembly checking
    protected static DOMImplementationRegistry direg = null; // bootstrap for XML Schema loader
    protected static XSImplementation xsimpl = null;         // more bootstrap
    protected static XSLoader xsloader = null;               // reusable XML Schema loader, produces XSModel
       
    private List<String> catalogFiles = new ArrayList<>();  // list of initial catalog files for resolver (as provided)
    private List<String> schemaFiles = new ArrayList<>();   // list of initial schema documents to load (as provided)
    private List<String> initialNSs = new ArrayList<>();    // list of namespaces to resolve for initial schema documents (as provided)
    private List<String> initialSchemaFileURIs = null;      // list of initial schema documents to load (converted to canonical file:// URIs)    
    private List<String> initialNSURIs = null;              // list of namespaces to resolve for initial achema documents (as URIs)
    private List<String> allSchemaFileURIs = null;          // list of initial schema documents & initial namespace URI resolutions
    private String initErrors = null;                       // initialization errors; eg. schema document not found
    private NTCatalogResolver resolver = null;              // catalog resolver used for load checking & schema generation

    private String schemaRootDirectory = "";                // file URI for root directory of all schema documents 
    private int schemaRootDirectoryLength = 0;              // length of root directory URI
    private List<LoadRec> loadDocs = null;                  // results of each document load attempt
    private HashSet<String> attemptedFiles = null;          // schema document load attempts
    private HashSet<String> loadedFiles = null;             // schema documents successfully loaded
    private HashMap<String, String> namespaceFile = null;   // map of namespace URI to schema document

    private XSModel xsmodel = null;                         // assembled XML schema object
    private String xsConstructionMessages = null;           // XML Schema errors

    protected String xsNamespaces = null;                   // namespace & contributing documents from XSModel
    protected String xsWarnings = null;                     // warnings derived from XSModel
    protected HashMap<String,List<MapRec>> nsPrefix = null; // nsPrefix.get(P) -> list of URIs mapped to prefix P
    protected HashMap<String,List<MapRec>> nsURI    = null; // nsURI.get(U)    -> list of prefixes mapped to namespace U
    
    /**
     * Constructs an empty Schema object. Add schema documents, namespace URIs,
     * and XML Catalog documents later, then check for import errors or generate
     * the schema XSModel, etc. All of the catalog and parser bootstrapping
     * happens here, throwing an exception if any of it fails.
     *
     * @throws ParserConfigurationException
     */
    public NTSchema() throws ParserConfigurationException {
        // Bootstrap the parsers when the first object is created
        if (sfact == null) {
            try {
                sfact = SAXParserFactory.newInstance();
                sfact.setNamespaceAware(true);
                sfact.setValidating(false);
                saxp = sfact.newSAXParser();
            } catch (SAXException ex) {
                throw (new ParserConfigurationException("Can't initialize suitable SAX parser" + ex.getMessage()));
            }
        }
        if (direg == null) {
            try {
                direg = DOMImplementationRegistry.newInstance();
                xsimpl = (XSImplementation) direg.getDOMImplementation("XS-Loader");
                xsloader = xsimpl.createXSLoader(null);
            } catch (Exception ex) {
                throw (new ParserConfigurationException("Can't initializte XML Schema parser" + ex.getMessage()));
            }
        }
    }

    /**
     * Constructs a Schema object for the specified schema documents and catalog
     * files.
     *
     * @param catalogFiles list of initial XML Catalog files
     * @param schemaOrNamespace list of initial schema documents or namespace URIs
     * @throws ParserConfigurationException
     */
    public NTSchema(List<String> catalogFiles, List<String> schemaOrNamespace)
            throws ParserConfigurationException {

        this();

        // Check and save the catalog files as canonical file URIs
        for (String cn : catalogFiles) {
            addCatalogFile(cn);
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
                    addSchemaNamespaceURI(u.toString());
                }
            } catch (URI.MalformedURIException ex) {
                // IGNORE
            }
            if (u == null) {
                addSchemaFile(sn);
            }
        }
    }
    
    // ----- SCHEMA INITIALIZATION -------------------------------------------

    /**
     * Add one catalog file to the list of catalogs used to resolve URIs. This
     * may change the schema, so initialization, assembly checking, and schema
     * validation may also change.
     *
     * @param cfn catalog file path
     */
    public final void addCatalogFile(String cfn) {
        catalogFiles.add(cfn);
        resolver = null;
        initErrors = null;
        loadDocs = null;
        xsmodel = null;
    }

    /**
     * Add one file to the list of initial schema documents. This changes the
     * schema, so initialization, assembly checking, and schema validation may
     * also change.
     *
     * @param sfn schema file path
     */
    public final void addSchemaFile(String sfn) {
        schemaFiles.add(sfn);
        initErrors = null;
        loadDocs = null;
        xsmodel = null;
    }

    /**
     * Add one URI to the list of initial namespace URIs specifying initial schema
     * documents. This changes the schema, so initialization, assembly checking,
     * and schema validation may also change.
     
     * @param sns namespace URI string
     */
    public final void addSchemaNamespaceURI(String sns) {
        initialNSs.add(sns);
        initErrors = null;
        loadDocs = null;
        xsmodel = null;
    }

    /**
     * Returns a list of file URIs for the initial list of catalog files
     * used to specify the schema.
     * 
     * @return list of provided catalog file paths
     */
    public List<String> getCatalogFiles() {
        initialize();
        return catalogFiles;
    }

    /**
     * Returns a list of file URIs for all the initial and subordinate
     * catalog files requested during catalog file parsing
     * 
     * @return list of all catalog files requested
     */
    public List<String> getAllCatalogFiles() {
        initialize();
        return resolver.allCatalogFiles();
    }

    /**
     * Returns a list of file URIs for the valid catalog files loaded
     * during catalog file parsing.
     * 
     * @return list of all valid catalog files
     */
    public List<String> getAllValidCatalogFiles() {
        initialize();
        return resolver.allValidCatalogFiles();
    }

    /**
     * Returns a list of initial schema documents used to generate the schema.
     *
     * @return list of initial schema documents
     */
    public List<String> getSchemaFiles() {
        initialize();
        return schemaFiles;
    }

    /**
     * Returns a list of namespaces used to specify the schema. These
     * namespaces are resolved against the catalog, and the corresponding
     * local resources are used to generate the schema.
     *
     * @return list of initial namespace URIs
     */
    public List<String> getSchemaNSURIs() {
        initialize();
        return initialNSs;
    }
    
    /**
     * Returns a list of file: URIs, one for each initial schema document 
     * provided as a file name or namespace URI.
     * 
     * @return list of initial schema document file URIs
     */
    public List<String> getAllInitialSchemaURIs() {
        initialize();
        return allSchemaFileURIs;
    }

    /**
     * Returns the catalog resolver used to construct the schema.
     *
     * @return catalog resolver object
     */
    public NTCatalogResolver resolver() {
        initialize();
        return resolver;
    }

    /**
     * Returns the result of parsing the catalog files used to specify the
     * schema. Includes result of parsing subordinate catalogs.
     *
     * @return a string containing catalog parsing results
     */
    public String catalogParsingResults() {
        initialize();
        return resolver().validationResultString();
    }

    /**
     * Returns any errors encountered in parsing the catalog files used to
     * specify the schema. Includes errors found while parsing subordinate
     * catalogs.
     *
     * @return a string containing catalog parsing errors; empty string if none
     */
    public String catalogParsingErrors() {
        initialize();
        return resolver().validationErrorString();
    }

    /**
     * Returns any initialization errors for the schema. Includes any errors in
     * the catalog files.
     *
     * @return a string containing schema initialization errors; empty string if
     * none
     */
    public String initializationErrorMessages() {
        initialize();
        return initErrors;
    }

    private void initialize() {
        if (initErrors != null) {
            return;
        }
        StringBuffer errs = new StringBuffer(128);
        if (resolver == null) {
            resolver = new NTCatalogResolver();
            if (catalogFiles.size() > 0) {
                  resolver.setCatalogList(catalogFiles.toArray(new String[0]));
            }
        }
        errs.append(resolver.validationErrorString());

        initialSchemaFileURIs = new ArrayList<>();
        allSchemaFileURIs = new ArrayList<>();
        for (String s : schemaFiles) {
            String suri = null;
            File f = new File(s);
            if (f.canRead()) {
                suri = canonicalFileURI(f);
            }
            if (suri != null) {
                initialSchemaFileURIs.add(suri);
                allSchemaFileURIs.add(suri);
            } else {
                errs.append(String.format("can't read schema file %s\n", s));
            }
        }
        initialNSURIs = new ArrayList<>();
        for (String s : initialNSs) {
            String rv;
            try {
                rv = resolver.resolveURI(s);
                if (rv == null) {
                    errs.append(String.format("can't resolve namespace %s\n", s));
                } else if (!rv.startsWith("file:")) {
                    errs.append(String.format("namespace %s resolves to non-local resource %s\n", s, rv));
                } else {
                    initialNSURIs.add(s);
                    allSchemaFileURIs.add(rv);
                }
            } catch (IOException ex) {
                errs.append(String.format("invalid URI syntax for initial namespace %s\n", s));
            }
        }
        if (allSchemaFileURIs.size() < 1) {
            errs.append("no readable schema documents provided\n");
        }
        initErrors = errs.toString();
    }

    // ------------- SCHEMA ASSEMBLY CHECKING -------------------------------

    /**
     * Returns the root directory for catalog and schema documents as a file
     * URI.
     *
     * @return schema root directory URI
     */
    public String schemaRootDirectory() {
        assemblyCheck();
        if (schemaRootDirectory.length() < 1) {
            return "unknown";
        }
        return schemaRootDirectory;
    }
    
    /**
     * Returns a list of the schema documents actually loaded during the
     * schema assembly check.
     * @return list of assembled schema documents
     */
    public List<String> assembledSchemaDocuments () {
        List<String> res = new ArrayList<>();
        for (String s : loadedFiles) {
            res.add(s);
        }
        return res;
    }
    
    /**
     * Returns a string describing each document load attempt during the
     * schema assembly check. Each entry in the string is formatted as follows:
     * <pre><code>
     filename:line# IMPORT path/to/file (ns=http://namespace/uri sl=schema/location/path)
       resolved namespace != resolved schemaLocation
       namespace URI resolves to non-local resource</code></pre> 
     * <p>
     * Returns
     * an empty string if no errors encountered.
     *
     * @return string of assembly log messages
     */
    public String assemblyLogMessages() {
        return assemblyMessages(true);
    }
    
    /**
     * Returns a string describing document load attempts with findings. 
     * Document loads with no checked error, inconsistency, or ambiguity
     * are not listed.
     * @return string of assembly findings
     */
    public String assemblyMessages() {
        return assemblyMessages(false);
    }
    
    /**
     * Returns a list of all schema document load attempts during the schema
     * assembly check.
     *
     * @return list of load-attempt records
     */
    public List<LoadRec> assemblyList() {
        assemblyCheck();
        return loadDocs;
    }
    
    private String assemblyMessages(boolean logMsgs) {
        assemblyCheck();
        if (loadDocs.size() < 1) {
            return ("No initial schema document\n");
        }
        StringBuilder e = new StringBuilder(256);
        for (LoadRec r : loadDocs) {
            boolean flag = false;
            for (String m : r.msgs) {
                if (m.startsWith("[log] ")) {
                    if (!logMsgs) {
                        continue;
                    }
                    m = m.substring(6);
                }
                if (!flag) {
                    e.append(assemblyMessageHeader(r));
                    flag = true;
                }
                e.append("  ").append(m);
            }
            if (!flag && logMsgs) {
                e.append(assemblyMessageHeader(r));
            }
        }
        return e.toString();
    }

    private String assemblyMessageHeader(LoadRec r) {
        String prp;
        if (r.parent == null) {
            prp = "[initial load]";
        } else {
            prp = pileRelativePath(r.parent);
        }
        String h = String.format("%s:%d %s %s (ns=%s sl=%s)\n", 
                prp, r.pline, r.fkind.toUpperCase(), pileRelativePath(r.fileURI), r.ns, r.sloc);
        return h;
    }

    private String pileRelativePath(String u) {
        return u.substring(schemaRootDirectoryLength);
    }

    /*
     * Xerces assembles schema documents depth-first.  
     * We check them breadth-first.
     * Doesn't matter because we check all the import and include elements in 
     * all the documents.
    */
    private void assemblyCheck() {
        if (loadDocs != null) {
            return;
        }
        initialize();
        namespaceFile = new HashMap<>();
        attemptedFiles = new HashSet<>();
        loadedFiles = new HashSet<>();
        loadDocs = new ArrayList<>();
        for (String furi : initialSchemaFileURIs) {
            LoadRec lr = new LoadRec();
            lr.fkind = "load";
            lr.slocRef = furi;
            loadDocs.add(lr);
        }
        for (String ns : initialNSURIs) {
            LoadRec lr = new LoadRec();
            lr.fkind = "load";
            lr.ens = lr.ns = ns;
            loadDocs.add(lr);
        }
        int idx = 0;
        while (idx < loadDocs.size()) {
            LoadRec r = loadDocs.get(idx);
            loadDocument(r);
            idx++;
            // System.out.println(idx);
            // System.out.println(r.toString());
        }
        if (loadDocs.size() > 0) {
            schemaRootDirectory = loadDocs.get(0).fileURI;
            for (LoadRec r : loadDocs) {
                schemaRootDirectory = commonPrefix(schemaRootDirectory, r.fileURI);
            }
            schemaRootDirectoryLength = schemaRootDirectory.length();
        }
    }

    private void loadDocument(LoadRec r) {
        try {
            r.nsRef = resolver().resolveURI(r.ns);
            if (r.slocRef == null) {    // could be initial schema doc
                r.sloc = r.sloc.trim();
                r.slocRef = resolver().resolveURI(r.sloc);
            }
        } catch (Exception ex) {
            /* ignore */
        }
        if (r.ns != null) {
            if (r.nsRef == null && catalogFiles.size() > 0) {
                r.msgs.add(String.format("namespace URI does not resolve\n"));
            } else if (!r.nsRef.startsWith("file:")) {
                r.msgs.add(String.format("namespace URI resolves to non-local resource\n"));
            }
        } else {
            if ("import".equals(r.fkind)) {
                r.msgs.add(String.format("no namespace attribute\n"));
            }
        }
        if (r.sloc != null) {
            if (r.slocRef != null) {
                if (!r.slocRef.startsWith("file:")) {
                    r.msgs.add(String.format("schemaLocation resolves to non-local resource\n"));
                }
            } else {
                r.slocRef = canonicalFileURI(r.fileURI, r.sloc);
            }
        } else {
            if (!"load".equals(r.fkind)) {
                r.msgs.add(String.format("no schemaLocation attribute\n"));
            }
        }
        if (r.nsRef != null && r.slocRef != null) {
            if (r.nsRef.equals(r.slocRef)) {
                loadDocumentFromURI(r, r.nsRef);
            } else {
                r.msgs.add(String.format("resolved namespace %s != resolved schemaLocation %s\n", r.nsRef, r.slocRef));
                loadDocumentFromURI(r, r.nsRef);
                loadDocumentFromURI(r, r.slocRef);
            }
        } else if (r.nsRef != null) {
            loadDocumentFromURI(r, r.nsRef);
        } else if (r.slocRef != null) {
            loadDocumentFromURI(r, r.slocRef);
        } else {
            r.msgs.add(String.format("can't determine schema document to be loaded"));
        }
    }

    private void loadDocumentFromURI(LoadRec r, String furi) {
        if (!furi.startsWith("file:")) {
            return;
        }
        r.fileURI = furi;
        if (attemptedFiles.contains(furi)) {
            if (loadedFiles.contains(furi)) {
                r.msgs.add(String.format("[log] already loaded %s\n", furi));
            }
            else {
                r.msgs.add(String.format("[log] already failed to load %s\n", furi));
            }
        }
        else {
            r.msgs.add(String.format("[log] loading %s\n", furi));
            String lns = namespaceFile.getOrDefault(r.ns, furi);
            if (!lns.equals(furi)) {
                r.msgs.add(String.format("namespace %s already loaded from a different file (%s)\n", r.ns, lns));
            }
            attemptedFiles.add(furi);
            Handler myhandler = new Handler(this.loadDocs, r);
            try {
                saxp.parse(furi, myhandler);
                loadedFiles.add(furi);
            } catch (SAXException ex) {
                String em = exceptionReason(ex);
                r.msgs.add(String.format("can't parse schema document %s: %s\n", furi, em));
            } catch (IOException ex) {
                String em = exceptionReason(ex);
                r.msgs.add(String.format("can't read %s: %s\n", furi, em));
            }
        }
    }

    /**
     * A callback class for SAX parsing of schema documents
     */
    private class Handler extends DefaultHandler {

        private final LoadRec r;
        private final List<LoadRec> lds;
        private Locator loc;

        Handler(List<LoadRec> lds, LoadRec r) {
            super();
            this.lds = lds;
            this.r = r;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.loc = locator; //Save the locator, so that it can be used later for line tracking when traversing nodes.
        }

        @Override
        public void startElement(String ens, String local, String raw, Attributes attrs) throws SAXException {
            if (W3C_XML_SCHEMA_NS_URI.equals(ens)) {
                if ("schema".equals(local)) {
                    String tns = attrs.getValue("targetNamespace");
                    if (tns != null && r.ens != null && !tns.equals(r.ens)) {
                        r.msgs.add(String.format("targetNamespace %s != expected namespace %s\n", tns, r.ens));
                    }
                } else if ("import".equals(local)) {
                    LoadRec nr = new LoadRec();
                    nr.fkind = "import";
                    nr.parent = r.fileURI;
                    nr.pline = loc.getLineNumber();
                    nr.fileURI = r.fileURI;
                    nr.ns = nr.ens = attrs.getValue("namespace");
                    nr.sloc = attrs.getValue("schemaLocation");
                    lds.add(nr);
                } else if ("include".equals(local) || "redefine".equals(local)) {
                    LoadRec nr = new LoadRec();
                    nr.fkind = local;
                    nr.parent = r.fileURI;
                    nr.pline = loc.getLineNumber();
                    nr.fileURI = r.fileURI;
                    nr.ens = r.ens;
                    nr.sloc = attrs.getValue("schemaLocation");
                    lds.add(nr);
                }
            }
        }
    }

    /**
     * A class for recording the results of an attempt to load a schema
     * document.
     */
    public class LoadRec {
        public String fkind;            // operation attempting the load: import, include, redefine, or initial load
        public String fileURI;          // canonical file URI of the document to be loaded
        public String parent;           // canonical file URI of parent document (or null for initial load)
        public int pline;               // line number of import/include/redefine element in parent document
        public String ens;              // expected document namespace
        public String ns;               // namespace attribute from import element
        public String sloc;             // schemaLocation attribute from import/include/redefine element
        public String nsRef;            // namespace resolution URI (or null if not resolved)
        public String slocRef;          // schemaLocation resolution or path (or null if no schemaLocation)
        public List<String> msgs;       // empty list if no messages for this document load attempt

        LoadRec() {
            fkind = fileURI = parent = ens = ns = sloc = nsRef = slocRef = null;
            pline = 0;
            msgs = new ArrayList<>();
        }

        @Override
        public String toString() {
            StringBuilder s = new StringBuilder(256);
            s.append(String.format(
                    "parent= %s\nline= %d\noperation=%s\ntarget=%s\nexpected namespace= %s\n" +
                    "namespace attribute= %s\nschemaLocation attribute= %s\nnamespace resolution= %s\n" +
                    "schemaLocation resolution or path= %s\n",
                    parent, pline, fkind, fileURI, ens, ns, sloc, nsRef, slocRef));
            if (msgs.size() > 0) {
                for (String ms : msgs) {
                    s.append("  ").append(ms);
                }
            }
            return s.toString();
        }
    }

    // ------------ SCHEMA XSMODEL CONSTRUCTION -----------------------------
    
    /**
     * Return all schema construction messages produced by Xerces
     * @return schema construction messages
     */
    public String xsConstructionMessages () {
        xsmodel();
        return xsConstructionMessages;
    }
    
    /**
     * Return results of all resolution operations performed during schema 
     * construction
     * @return resolution messages
     */
    public String xsResolutionMessages () {
        xsmodel();
        return resolver().resolutionMessages();
    }
    
    /**
     * Return string list of namespaces processed by Xerces. Each entry shows
     * the namespace URI plus list of document that contributed to the namespace.
     * @return namespaces and contributing documents
     */
    public String xsNamespaces () {
        if (xsNamespaces != null) {
            return xsNamespaces;
        }
        if (xsmodel() == null) {
            xsNamespaces = "";
            return xsNamespaces;
        }
        StringBuilder msgs = new StringBuilder();
        XSNamespaceItemList nsil = xsmodel.getNamespaceItems();
        for (int i = 0; i < nsil.getLength(); i++) {
            XSNamespaceItem nsi = nsil.item(i);   
            String ns = nsi.getSchemaNamespace();
            StringList docs = nsi.getDocumentLocations();
            if (docs.getLength() > 1) {
                msgs.append(String.format("%s <-\n", ns));
                for (int di = docs.getLength()-1; di >= 0; di--) {
                    msgs.append(String.format("  %s\n", docs.item(di)));
                }                
            }
            else if (docs.getLength() > 0) {
                msgs.append(String.format("%s <- %s\n", ns, docs.get(0)));
            }
        }
        xsNamespaces = msgs.toString();
        return xsNamespaces;
    }
    
    /**
     * Return warnings derived from Xerces XSModel. Complains about <ul>
     * <li> Namespace prefix mapped to more than one URI
     * <li> Namespace URI mapped to more than one prefix</ul>
     * @return schema construction warnings
     */
    public String xsWarningMessages() {
        return null;
    }
    
    /**
     * Returns the XSModel constructed by Xerces for this schema.
     * @return schema XSModel object
     */
    public XSModel xsmodel () {
        if (xsConstructionMessages != null) {
            return xsmodel;
        }
        initialize();
        SchemaErrorHandler ehandler = new SchemaErrorHandler();
        DOMConfiguration config = xsloader.getConfig();
        config.setParameter("validate", true);
        config.setParameter("resource-resolver", resolver());
        config.setParameter("error-handler",ehandler);
        resolver().resetResolutions();
        StringListImpl slist = new StringListImpl(
                allSchemaFileURIs.toArray(new String[0]),
                allSchemaFileURIs.size());
        xsmodel = xsloader.loadURIList(slist);
        if (xsmodel == null) {
            ehandler.msgs.append("schema loader returned null");
        }
        xsConstructionMessages = ehandler.toString();
        return xsmodel;
    }
   
    private class SchemaErrorHandler implements DOMErrorHandler {
        public final StringBuilder msgs = new StringBuilder();
        @Override
        public boolean handleError(DOMError error) {
            short sevCode = error.getSeverity();
            String sevstr;
            if (sevCode == DOMError.SEVERITY_FATAL_ERROR) { sevstr = "[fatal]"; } 
            else if (sevCode == DOMError.SEVERITY_ERROR)  { sevstr = "[error]"; } 
            else { sevstr = "[warn]"; }

            DOMLocator loc = error.getLocation();
            String uri = loc.getUri();
            String fn  = "";
            if (uri != null) {
                int index = uri.lastIndexOf('/');
                if (index != -1) {
                    fn = uri.substring(index + 1)+":";
                }
            }
            msgs.append(String.format("%s %s %d:%d %s\n", 
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

    protected class MapRec {
        String ns = null;       // namespace in which mapping appears
        String val = null;      // mapped namespace prefix or namespace URI 
        MapRec(String ns, String val) {
            this.ns = ns;
            this.val = val;
        }
        @Override
        public String toString () {
            return String.format("[%s,%s]", val, ns);
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

    static String commonPrefix(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int lim = min(len1, len2);
        int i = 0;
        while (i < lim && (s1.charAt(i) == s2.charAt(i))) {
            i++;
        }
        if (i == len1) {
            return s1;
        } else if (i == len2) {
            return s2;
        } else if (i == 0) {
            return "";
        } else {
            return s1.substring(0, i);
        }
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
