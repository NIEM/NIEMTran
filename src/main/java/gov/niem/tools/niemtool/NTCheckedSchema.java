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

package gov.niem.tools.niemtool;

import static gov.niem.tools.niemtool.XSNamespaceInfo.commonPrefix;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * A class for finding inconsistencies and ambiguities in the assembly of
 * an XML Schema object from a collection of XML Schema documents.
 * <p>
 * The class is intended for development, not production. It supplies details about 
 * the operation of the Xerces parser and the Apache Commons XML resolver during 
 * schema construction.  It reports inconsistencies and ambiguities in the 
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
 * <li>Are all of the catalog files readable and valid XML Catalog documents
 * (including subordinate catalogs)?</ul>
 *
 * <p>
 * <li> Schema assembly checking. Parses each schema document (using vanilla
 * SAX, not the Xerces XML Scheme API), following every <code>import</code>,
 * <code>include</code>, and <code>redefine</code> element. Checks each of
 * those elements for completeness and consistency. Reports the following 
 * findings:<ul>
 *
 * <lI>namespace URI resolves to non-local resource
 * <li>schemaLocation URI resolves to non-local resource
 * <li>resolved namespace != resolved schemaLocation
 * <li>can't determine a schema document to load
 * <li>can't parse schema document
 * <li>can't read schema document file
 * <li>target namespace != expected namespace
 * <li>namespace already loaded from a different file
 * <li>no catalog entry for namespace URI
 * <li>no namespace attribute in import element
 * <li>no schemaLocation attribute in element
 * <li>include element found in namespace that has a catalog entry</ul>
 *
 * <p>
 * <li> Schema construction checking. Uses the Xerces XML Schema API to construct
 * the XSModel object. Reports: <ul>
 * 
 * <li>any errors or warnings returned by Xerces
 * <li>namespace prefix or namespace URI with more than one binding
 * <li>non-standard prefix for a NIEM namespace
 * <li>external namespaces (those without a NIEM conformance assertion)
 * <li>each namespace constructed, with list of documents contributing to its content
 * <li>result of all XML Catalog resolutions performed during construction</ul></ol>
 *
 * <p>
 * Example usage: <pre><code>
 try {
     NTCheckedSchema s = new NTCheckedSchema();
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
     System.exit(1);
 }
 System.out.println("Schema root directory: " + s.schemaRootDirectory();
 if (!s.assemblyWarningMessages().isEmpty()) {
     System.out.println("Schema assembly findings:");
     System.out.print(s.assemblyWarningMessages());
 }
 XSModel xs = s.xmodel();
 System.out.println(xs == null ? "Schema construction FAILED" : "Schema construction SUCCEDED");
 if (!s.xsWarningMessages().isEmpty() || !s.xsConstructionMessages().isEmpty()) {
     System.out.println("Schema construction errors and warnings:"
     System.out.print(s.xsConstructionMessages());
     System.out.print(s.xsWarningMessages());
 }</code></pre>
 * 
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class NTCheckedSchema extends NTSchema {
    
    private String schemaRootDirectory = "";                // file URI for root directory of all schema documents 
    private boolean schemaWarnings = false;                       // any assembly warnings?
    private List<LoadRec> loadDocs = null;                  // schema load requests / results
    private HashSet<String> attemptedFiles = null;          // file URI of schema document load attempts
    private HashSet<String> loadedFiles = null;             // file URI of schema documents successfully loaded
    private HashMap<String, String> namespaceFile = null;   // map of namespace URI to schema document
    private XSNamespaceInfo nsInfo = null;                  // Xerces namespace info item annotations

    
    public NTCheckedSchema () throws ParserConfigurationException {
        super();
    }
    
    public NTCheckedSchema (List<String> catalogs, List<String>schemaOrNSs) 
            throws ParserConfigurationException {
        super(catalogs,schemaOrNSs);
    }   
    
    @Override
    protected void reset () {
        super.reset();
        loadDocs = null;
    }

    
    // ----- RESULTS OF SCHEMA ASSEMBLY CHECKING --------------
    //
    // Assembly checking parses all the schema documents using vanilla SAX,
    // generating warnings about potential ambiguities.
    
    /**
     * Returns the root directory for catalog and schema documents as a file
     * URI. All catalog and schema documents are located somewhere in this tree.
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
        assemblyCheck();
        List<String> res = new ArrayList<>();
        for (String s : loadedFiles) {
            res.add(s);
        }
        return res;
    }
    
    /**
     * Returns a message list logging each schema document load attempt. 
     * Returns an empty list if no errors encountered.
     * @return list of assembly log messages
     */
    public List<String> assemblyLogMessages() {
        return assemblyMessages(true);
    }
    
    /**
     * Returns a message list of schema document load attempts with warnings. 
     * @return list of assembly warning messages
     */
    public List<String> assemblyWarningMessages() {
        return assemblyMessages(false);
    }
    
    /**
     * Returns true if schema assembly generated any warning messages.
     * @return warning flagg
     */
    public boolean assemblyWarnings() {
        return schemaWarnings;
    }
    
    
    // ----------- RESULTS OF SCHEMA CONSTRUCTION CHECKING ---------
    //
    // Schema construction is performed by Xerces.  Info and warning 
    // messages are generated from the Xerces namespace information items
    // found in the XSModel object.
    
    public List<String> xsNamespaceList() {
        constructionCheck();
        return nsInfo.xsNamespaceList();
    }
    
    public List<String> xsWarningMessages() {
        constructionCheck();
        return nsInfo.xsWarningMessages();
    }
    
    public List<String> xsNIEMWarningMessages() {
        constructionCheck();
        return nsInfo.xsNIEMWarningMessages();
    }
    
    
// ---------- SCHEMA ASSEMBLY CHECKING --------------------- 
    
    private List<String> assemblyMessages(boolean allMsgs) {
        assemblyCheck();
        List<String> res = new ArrayList<>();       
        if (loadDocs.size() < 1) {
            res.add("Empty schema\n");
            return res;
        }
        for (LoadRec r : loadDocs) {
            if (allMsgs || r.warnFlag) {
                if (r.parent == null) {
                    if (r.ns != null) {
                        String furi = r.nsRes.replace(schemaRootDirectory, "");
                        res.add(String.format("INITIAL LOAD of namespace %s\n", r.ns));
                        res.add(String.format("  namespace resolves to %s\n", furi));
                        res.add(String.format("  %s appended to schema document queue\n", furi));
                    }
                    else {
                        String furi = r.slocRes.replace(schemaRootDirectory, "");
                        res.add(String.format("INITIAL LOAD of schema document %s\n", furi));
                        res.add(String.format("  %s appended to schema document queue\n", furi));
                    }
                }
                else {
                    String relParent = r.parent.substring(schemaRootDirectory.length());
                    res.add(String.format("At %s:%d %s ns=%s sl=%s\n",
                            relParent, r.pline, r.fkind.toUpperCase(), r.ns, r.sloc));
                    for (String s : r.msgs) {
                        res.add("  " + s);
                    }
                }
            }
        }
        return res;
    }

    /*
     * Xerces constructs a schema by processing schema documents depth-first. 
     * We check them  breadth-first.  Doesn't matter because we process all of 
     * the schema documents referred to by all of the import and include elements 
     * encountered (in all the documents).
     */
    private void assemblyCheck() {
        if (loadDocs != null) {
            return;
        }
        namespaceFile = new HashMap<>();
        attemptedFiles = new HashSet<>();
        loadedFiles = new HashSet<>();
        loadDocs = new ArrayList<>();
        for (String furi : getInitialSchemaFileURIs()) {
            LoadRec lr = new LoadRec();
            lr.fkind = "load";
            lr.slocRes = furi;
            loadDocs.add(lr);
        }
        for (String ns : getInitialNSURIs()) {
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
        // Find the longest common prefix of the file URIs for all schema
        // documents and catalog documents.  This is the schema root directory.
        String[] afiles = attemptedFiles.toArray(new String[0]);
        if (afiles.length > 0) {
            schemaRootDirectory = afiles[0];
        }
        else {
            schemaRootDirectory = "file:/";
        }
        for (int i = 1; i < afiles.length; i++) {
            schemaRootDirectory = commonPrefix(schemaRootDirectory, afiles[i]);
        }        
        String pat = "\\*" + schemaRootDirectory;
        for (LoadRec r : loadDocs) {
            r.msgs.replaceAll(e -> e.replaceAll(pat, ""));
        }
    }

    /**
     * Process a schema document load attempt and record result.
     * 
     * As each schema document is parsed (as a plain XML document), each {import, 
     * include, redefine} element turns into a LoadRec object added to a queue.
     * In this way, schema documents are processed breadth-first.
     * 
     * Those objects are processed here.  Input values are:
     * 
     * r.fkind      type of request (import,include,redefine,initial-load) 
     * r.parent     file URI of schema document containing the request element
     * r.ns         @namespace in request (eg. namespace="xxx")
     * r.sloc       @schemaLocation in request (eg. schemaLocation="xxx")
     * r.slocres    URI of initial schema document (for LOAD request only)
     * 
     * Actual document load and parsing is handled by loadDocumentFromURI.
     * This routine only handles catalog resolution and various warnings that
     * result.
     * 
     * @param r Describes this document load effort 
     */
    private void loadDocument(LoadRec r) {
        // attempt catalog resolution of @namespace and @schemaLocation
        try {
            r.nsRes = resolver().resolveURI(r.ns);
            if (r.sloc != null) {    // could be initial schema doc
                r.sloc = r.sloc.trim();
                r.slocRes = resolver().resolveURI(r.sloc);
            }
        } catch (Exception ex) {
            /* ignore */
        }
        // create various warnings based on resolution results
        // strip out any attempt to load non-local resource
        if (r.ns != null) {
            if (r.nsRes == null && getCatalogFiles().size() > 0) {
                r.warn("no catalog entry for namespace %s\n", r.ns);
            } else if (r.nsRes != null && !r.nsRes.startsWith("file:")) {
                r.warn("namespace %s resolves to non-local resource %s\n", r.ns, r.nsRes);
                r.nsRes = null;
            }
        } else {
            if ("import".equals(r.fkind)) {
                r.warn("no namespace attribute in import element\n");
            }
        }
        if (r.sloc != null) {
            if (r.slocRes != null) {
                if (!r.slocRes.startsWith("file:")) {
                    r.warn("schemaLocation %s resolves to non-local resource %s\n", r.sloc, r.slocRes);
                    r.slocRes = null;
                }
            } else {
                r.slocRes = canonicalFileURI(r.parent, r.sloc);
            }
        } else {
            if (!"load".equals(r.fkind)) {
                r.warn("no schemaLocation attribute in %s element\n", r.fkind);
            }
        }
        // log resolution results for this load attempt
        if (r.nsRes != null) {
            r.log("namespace resolves to *%s\n", r.nsRes);
        }
        if (r.slocRes != null) {
            r.log("schemaLocation resolves to *%s\n", r.slocRes);
        }
        // At this point we should know the file path of the schema
        // document(s) to be loaded. 
        if (r.nsRes != null && r.slocRes != null) {
            if (r.nsRes.equals(r.slocRes)) {
                loadDocumentFromURI(r, r.nsRes);
            } else {
                r.warn("resolved namespace != resolved schemaLocation\n");
                loadDocumentFromURI(r, r.nsRes);
                loadDocumentFromURI(r, r.slocRes);  // process both files
            }
        } else if (r.nsRes != null) {
            loadDocumentFromURI(r, r.nsRes);
        } else if (r.slocRes != null) {
            loadDocumentFromURI(r, r.slocRes);
        } else {
            r.warn("can't determine a schema document to load");
        }
    }

    /**
     * Load and parse a schema document (as plain XML).
     * 
     * This is a separate routine because a single include element can result
     * in loading two separate schema documents (if @namespace and @schemalocation
     * attributes don't resolve to the same document.
     * 
     * @param r     append warning and log messages to this LoadRec
     * @param furi  file URI of schema document to load and parse
     */
    private void loadDocumentFromURI(LoadRec r, String furi) {
        if (loadedFiles.contains(furi)) {
            r.log("*%s already queued\n", furi);
        }
        else if (attemptedFiles.contains(furi)) {
            r.log("*%s already found non-loadable\n", furi);
        }
        else {
            r.log("*%s appended to schema document queue\n", furi);
            if (r.ns != null) {
                String lns = namespaceFile.getOrDefault(r.ns, furi);
                if (!lns.equals(furi)) {
                    r.warn("namespace %s is also loaded from *%s\n", r.ns, lns);
                }
            }
            namespaceFile.put(r.ns, furi);
            attemptedFiles.add(furi);
            
            // Pass the list of load requests/results to the SAX handler
            // so that the handler can append any {import,include,redefine} 
            // it encounters.
            Handler myhandler = new Handler(this.loadDocs, r, furi);
            try {
                SAXParser saxp = parsers.sax2Parser();
                saxp.parse(furi, myhandler);
                loadedFiles.add(furi);
            } catch (SAXException | ParserConfigurationException ex) {
                String em = exceptionReason(ex);
                r.warn("*%s can't be parsed: %s\n", furi, em);
            } catch (IOException ex) {
                String em = exceptionReason(ex);
                r.warn("*%s can't be read: %s\n", furi, em);
            }
        }
    }

    /**
     * A callback class for SAX parsing of schema documents
     */
    private class Handler extends DefaultHandler {

        private final List<LoadRec> lds;    // master list of requests/results 
        private final LoadRec r;            // for warning and log messages
        private final String furi;          // URI of file we are psrsing
        private Locator loc = null;         // line numbering

        Handler(List<LoadRec> lds, LoadRec r, String furi) {
            super();
            this.lds = lds;
            this.r = r;
            this.furi = furi;
        }

        @Override
        /**
         * Save the locator.
         * Use it later for line tracking when traversing nodes.
         */
        public void setDocumentLocator(Locator locator) {
            this.loc = locator; 
        }

        @Override
        public void startElement(String ens, String local, String raw, Attributes attrs) throws SAXException {
            if (W3C_XML_SCHEMA_NS_URI.equals(ens)) {
                if ("schema".equals(local)) {
                    String tns = attrs.getValue("targetNamespace");
                    if (tns != null && r.ens != null && !tns.equals(r.ens)) {
                        r.warn("targetNamespace %s != expected namespace %s\n", tns, r.ens);
                    }
                }
                // Process <import> found at line "nr.pline" in file "nr.parent"
                else if ("import".equals(local)) {
                    LoadRec nr = new LoadRec();
                    nr.fkind = "import";
                    nr.parent = furi;
                    nr.pline = loc.getLineNumber();
                    nr.ns = nr.ens = attrs.getValue("namespace");
                    nr.sloc = attrs.getValue("schemaLocation");
                    lds.add(nr);
                } else if ("include".equals(local) || "redefine".equals(local)) {
                    LoadRec nr = new LoadRec();
                    nr.fkind = local;
                    nr.parent = furi;
                    nr.pline = loc.getLineNumber();
                    nr.ens = r.ens;
                    nr.sloc = attrs.getValue("schemaLocation");
                    lds.add(nr);
                    // Xerces can't handle include and redefine elements in a 
                    // namespace that has a catalog entry
                    if (r.nsRes != null) {
                        r.warn("<%s \"%s\"> found in a namespace that has a catalog entry\n", local, nr.sloc);
                    }                    
                }
            }
        }
    }

    /**
     * A class for queuing a schema document load attempt, and recording the
     * result of processing the load attempt. 
     */
    private class LoadRec {
        private String fkind;            // operation attempting the load: import, include, redefine, or initial load
        private String parent;           // canonical file URI of parent document (or null for initial load)
        private int pline;               // line number of import/include/redefine element in parent document
        private String ens;              // expected document namespace (needed for include & redefine)
        private String ns;               // namespace attribute from import element
        private String sloc;             // schemaLocation attribute from import/include/redefine element
        private String nsRes;            // namespace resolution URI (or null if not resolved)
        private String slocRes;          // schemaLocation resolution or path (or null if no schemaLocation)
        private boolean warnFlag;        // false if all messages are log entries
        private List<String> msgs;       // empty list if no messages for this document load effort

        LoadRec() {
            fkind = parent = ens = ns = sloc = nsRes = slocRes = null;
            pline = 0;
            warnFlag = false;
            msgs = new ArrayList<>();
        }
        
        /**
         * Put a * character before the string format (i.e. "*%s") if the
         * string is a file URI that you want converted into a path 
         * relative to schemaRootDirectory.
         */        
        private void log (String fmt, Object... args) {
            msgs.add(String.format(fmt, args));             
        }
        
        private void warn (String fmt, Object... args  ) {
            schemaWarnings = true;
            warnFlag = true;
            log(fmt, args);
        }

        private void relativizeMsgs (String prefix) {
            String pat = "*" + prefix;
            msgs.replaceAll(e -> e.replaceAll(pat, ""));
        }
    }
    
    // ----------- SCHEMA CONSTRUCTION CHECKING -------------------
    
    private void constructionCheck () {
        if (nsInfo == null) {
            nsInfo = new XSNamespaceInfo(this.xsmodel());
        }
    }
    
    
    public String testOutput () {
        StringBuilder sb = new StringBuilder(512);
        if (!this.initializationErrorMessages().isEmpty()) {
            sb.append("*Initialization:");
            concat(sb,this.initializationErrorMessages());
        }
        else {
            sb.append(String.format("*Schema root: %s\n", this.schemaRootDirectory()));
            sb.append("*Assembly:\n");
            concat(sb,this.assemblyWarningMessages());
            sb.append("*Construction:\n");
            concat(sb,this.xsConstructionMessages());
            concat(sb,this.xsNamespaceList());
            concat(sb,this.xsWarningMessages());
            concat(sb,this.xsNIEMWarningMessages());
        }
        return(sb.toString());
    }    
    
    private static void concat (StringBuilder sb, List<String> sl) {
        for (String s : sl) {
            sb.append(s);
        }
    }
}
