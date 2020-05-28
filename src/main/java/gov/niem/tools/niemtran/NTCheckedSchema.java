/* 
 * NOTICE
 *
 * This software was produced for the U. S. Government
 * under Basic Contract No. W56KGU-18-D-0004, and is
 * subject to the Rights in Noncommercial Computer Software
 * and Noncommercial Computer Software Documentation
 * Clause 252.227-7014 (FEB 2012)
 * 
 * Copyright 2020 The MITRE Corporation.
 */

package gov.niem.tools.niemtran;

import static gov.niem.tools.niemtran.NTConstants.CONFORMANCE_ATTRIBUTE_NAME;
import static gov.niem.tools.niemtran.NTConstants.CONFORMANCE_TARGET_NS_URI_PREFIX;
import static gov.niem.tools.niemtran.NTConstants.NDR_CT_URI_PREFIX;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import static org.apache.commons.lang3.StringUtils.getCommonPrefix;
import org.apache.xerces.util.URI;
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
 * <li> Initialization checking (inherited from NTSchema):
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
 * <li>include element found in namespace that has a catalog entry
 * <li>namespace prefix or namespace URI with more than one binding
 * <li>non-standard prefix for a NIEM namespace
 * <li>external namespaces (those without a NIEM conformance assertion)</ul>
 *
 * <p>
 * <li> Schema construction checking (inherited from NTSchema). Uses the 
 * Xerces XML Schema API to construct the XSModel object. Reports: <ul>
 * 
 * <li>any errors or warnings returned by Xerces
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
    
    private String schemaRootDirectory = null;              // file URI for root directory of all schema documents 
    private List<String> logMsgs = null;                    // cached assembly log
    private List<String> warnMsgs = null;                   // cached assembly warnings
    private List<String> nsbindMsgs = null;                 // cached namespace binding messages
    private boolean schemaWarnings = false;                 // any assembly warnings?
    private List<LoadRec> loadDocs = null;                  // schema load requests / results
    private Set<String> attemptedFiles = null;              // file URI of schema document load attempts
    private Set<String> loadedFiles = null;                 // file URI of schema documents successfully loaded
    private Map<String,String> namespaceFile = null;        // map of namespace URI to most recent schema document
    private Map<String,String> nsNIEMversion = null;        // nsNIEMversion.get(uri) = NIEM version of namespace
    private NamespaceDecls nsDecls = null;                  // record of all namespace declarations encountered

    
    public NTCheckedSchema () throws ParserConfigurationException {
        super();
    }
    
    public NTCheckedSchema (List<String> catalogs, List<String>schemaOrNSs) 
            throws ParserConfigurationException {
        super(catalogs,schemaOrNSs);
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
        assembleSchema();
        if (schemaRootDirectory.length() < 1) {
            return "unknown";
        }
        return schemaRootDirectory;
    }
    
    /**
     * Returns a list of the schema documents actually loaded during the
     * schema assembly check, one document per line.
     * @return list of assembled schema documents
     */
    public List<String> assembledSchemaDocuments () {
        assembleSchema();
        List<String> res = new ArrayList<>();
        res.addAll(loadedFiles);
        return res;
    }
    
    /**
     * Returns a verbose message showing the results of every import, include,
     * and redefine element in all of the schema documents in the schema pile. 
     * @return list of assembly log messages
     */
    public List<String> assemblyLogMessages() {
        if (logMsgs == null) {
            logMsgs = assemblyMessages(true);
        }
        return logMsgs;
    }
    
    /**
     * Returns the warnings generated by schema assembly checking.
     * @return list of assembly warning messages
     */
    public List<String> assemblyWarningMessages() {
        if (warnMsgs == null) {
            warnMsgs = assemblyMessages(false);
        }
        return warnMsgs;
    }
    
    /**
     * Returns true if schema assembly checking produced any warnings.
     * @return warning flag
     */
    public boolean assemblyWarnings() {
        return schemaWarnings;
    }
    
    private List<String> assemblyMessages(boolean allMsgs) {
        assembleSchema();
        List<String> msgs = new ArrayList<>();
        if (loadDocs.isEmpty()) {
            msgs.add("Empty schema\n");
            return msgs;
        }
        // Process messages generated during schema document parsing
        String rootPattern = "\\*"+schemaRootDirectory;
        for (LoadRec r : loadDocs) {
            if (allMsgs || r.warnFlag) {
                if (r.parent == null) {
                    if (r.ns != null) {
                        String fpath = r.nsRes.replace(schemaRootDirectory, "");
                        msgs.add(String.format("INITIAL LOAD of namespace %s\n", r.ns));
                        msgs.add(String.format("  namespace resolves to %s\n", fpath));
                        msgs.add(String.format("  %s appended to schema document queue\n", fpath));
                    }
                    else {
                        String fpath = r.slocRes.replace(schemaRootDirectory, "");
                        msgs.add(String.format("INITIAL LOAD of schema document %s\n", fpath));
                        msgs.add(String.format("  %s appended to schema document queue\n", fpath));
                    }
                }
                else {
                    String relParent = r.parent.substring(schemaRootDirectory.length());
                    msgs.add(String.format("%s at %s:%d ns=%s sl=%s\n",
                            r.fkind.toUpperCase(), relParent, r.pline, r.ns, r.sloc));
                    for (MsgRec rm : r.msgs) {
                        if (allMsgs || rm.warnFlag) {
                            msgs.add("  "+rm.msg.replaceAll(rootPattern,""));
                        }
                    }
                }
            }
        }
        // Generate warning messages from all the namespace bindings
        msgs.addAll(nsbindMsgs);
        return msgs;
    }
    
    
    // ---------- SCHEMA ASSEMBLY CHECKING --------------------- 
    
    /**
     * Returns the set of target namespace URIs for all the schema documents
     * in the schema document set.
     * @return set of namespace URIs
     */
    protected Set<String> namespaces() {
        assembleSchema();
        return nsNIEMversion.keySet();
    }
    
    /**
     * Returns the NIEM version of a namespace (obtained from the conformance
     * target assertion in the schema document, if any).  Returns the empty
     * string for an external schema.     * 
     * @param nsURI -- namespace URI
     * @return namespace NIEM version
     */
    protected String namespaceNIEMVersion (String nsURI) {
        assembleSchema();
        return nsNIEMversion.get(nsURI);
    }
    
    /**
     * Returns an object describing all of the namespace declarations in 
     * the schema document set.
     * @return 
     */
    protected NamespaceDecls namespaceDeclarations() {
        assembleSchema();
        return nsDecls;
    }
    
    /*
     * Xerces constructs a schema by processing schema documents depth-first. 
     * Check them here breadth-first.  Doesn't matter because we process all of 
     * the schema documents referred to by all of the import and include elements 
     * encountered (in all the documents).
     */
    private void assembleSchema() {
        if (loadDocs != null) {
            return;
        }
        attemptedFiles = new HashSet<>();
        loadedFiles    = new HashSet<>();
        namespaceFile  = new HashMap<>();
        nsNIEMversion  = new HashMap<>();
        nsDecls  = new NamespaceDecls();
        loadDocs = new ArrayList<>();
        initialize();
        for (String furi : initialSchemaFileURIs) {
            LoadRec lr = new LoadRec();
            lr.fkind = "load";
            lr.slocRes = furi;
            loadDocs.add(lr);
        }
        for (String ns : initialNSs) {
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
        // documents and catalog documents.  This is the checked schema root directory.
        // Might not be the same as the value in NTSchema class, if schema
        // assembly checking loads a document that Xerces doesn't load.
        List<String> docs = new ArrayList<>();
        for (String curi : getAllCatalogFiles()) {
            docs.add(curi);
        }
        docs.addAll(attemptedFiles);
        schemaRootDirectory = getCommonPrefix(docs.toArray(new String[0]));
        
        // Generate warning messages from all the namespace bindings
        nsbindMsgs = nsDecls.nsDeclWarnings();
        schemaWarnings = schemaWarnings || !nsbindMsgs.isEmpty();        
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
            if (r.ns != null) {
                r.ns = r.ns.trim();
                r.nsRes = resolver().resolveURI(r.ns);
            }
            if (r.sloc != null) {
                r.sloc = r.sloc.trim();
                r.slocRes = resolver().resolveURI(r.sloc);
            }
        } catch (Exception ex) {
            /* ignore */
        }
        // create various warnings based on resolution results
        // strip out any attempt to load non-local resource
        if (r.ns != null) {
            if (r.nsRes == null && getInitialCatalogFiles().size() > 0) {
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
        // At this point we should know the file URI of the schema
        // document(s) to be loaded. 
        if (r.nsRes != null && r.slocRes != null && !r.nsRes.equals(r.slocRes)) {
            r.warn("resolved namespace != resolved schemaLocation\n");
            r.warn("namespace resolves to      %s\n", r.nsRes);
            parseDocument(r, r.nsRes);
            r.warn("schemaLocation resolves to %s\n", r.slocRes);
            parseDocument(r, r.slocRes);  // process both files            
        }
        else {
            r.log("namespace resolves to      %s\n", r.nsRes != null ? r.nsRes : "null");
            r.log("schemaLocation resolves to %s\n", r.slocRes != null ? r.slocRes : "null");         
        }
        if (r.nsRes != null) {
            parseDocument(r, r.nsRes);
        } else if (r.slocRes != null) {
            parseDocument(r, r.slocRes);
        } else {
            r.warn("can't determine a schema document to parse");
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
    private void parseDocument(LoadRec r, String furi) { 
        if (loadedFiles.contains(furi)) {
            r.log("*%s already parsed\n", furi);
        }
        else if (attemptedFiles.contains(furi)) {
            r.log("*%s already found non-parsable\n", furi);
        }
        else {
            if (r.ns != null) {
                String lns = namespaceFile.getOrDefault(r.ns, furi);
                r.log("now parsing schema document *%s\n", furi);
                if (!lns.equals(furi)) {
                    r.warn("namespace %s was also created from *%s\n", r.ns, lns);
                }
            }
            attemptedFiles.add(furi);
            
            Handler myhandler = new Handler(r, furi);
            try {
                SAXParser saxp = ParserBootstrap.sax2Parser();
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
        private final LoadRec r;            // for warning and log messages
        private final String furi;          // file URI of file we are psrsing
        private Locator loc = null;         // line numbering
        private String targetNS = null;     // target namespace URI
        private int nestLevel = 0;          // schema element nesting level
        private int nsDeclCt = 0;           // number of namespace declarations in document

        Handler(LoadRec r, String furi) {
            super();
            this.r = r;
            this.furi = furi;
        }
        
        @Override
        public void setDocumentLocator(Locator locator) {
            this.loc = locator; 
        }

        @Override
        public void startPrefixMapping(String p, String u) throws SAXException {
            if (p.isEmpty()) return;
            if (W3C_XML_SCHEMA_NS_URI.equals(u)) return;
            nsDecls.addNamespaceDecl(p, u, furi, loc.getLineNumber(), nestLevel);
            nsDeclCt++;
        }
              
        @Override
        public void startElement(String elementNS, String local, String raw, Attributes attrs) throws SAXException {
            nestLevel++;
            if (W3C_XML_SCHEMA_NS_URI.equals(elementNS)) {
                // Process <xs:schema> element
                if ("schema".equals(local)) {
                    // Determine target namespace
                    targetNS = attrs.getValue("targetNamespace");
                    if (targetNS == null) {
                        r.warn("no targetNamespace attribute\n");
                    }
                    else {
                        namespaceFile.put(targetNS, furi);
                    }
                    if (targetNS != null && r.ens != null && !targetNS.equals(r.ens)) {
                        r.warn("targetNamespace %s != expected namespace %s\n", targetNS, r.ens);
                    }
                    // Determine NIEM version from conformance target assertion
                    if ("".equals(nsNIEMversion.getOrDefault(targetNS, ""))) {
                        String nsv = "";
                        for (int i = 0; i < attrs.getLength(); i++) {
                            String auri = attrs.getURI(i);
                            String av = attrs.getValue(i);
                            String aln = attrs.getLocalName(i);
                            if (auri.startsWith(CONFORMANCE_TARGET_NS_URI_PREFIX) && CONFORMANCE_ATTRIBUTE_NAME.equals(aln)) {
                                for (String ctv : av.split("\\s+")) {
                                    if (ctv.startsWith(NDR_CT_URI_PREFIX)) {
                                        ctv = ctv.substring(NDR_CT_URI_PREFIX.length());
                                        int sp = ctv.indexOf('/');
                                        if (sp >= 0) {
                                            nsv = ctv.substring(0, sp);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        nsNIEMversion.put(targetNS, nsv);
                    }
                }
                // Process <xs:import> found at line "nr.pline" in file "nr.parent"
                // Add the imported document to the end of the document list
                else if ("import".equals(local)) {
                    LoadRec nr = new LoadRec();
                    nr.fkind = "import";
                    nr.parent = furi;
                    nr.pline = loc.getLineNumber();
                    nr.ns = nr.ens = attrs.getValue("namespace");
                    nr.sloc = attrs.getValue("schemaLocation");
                    loadDocs.add(nr);
                // Process <xs:include> or <xs:redefine>
                // Add the document to the end of the document list
                } else if ("include".equals(local) || "redefine".equals(local)) {
                    LoadRec nr = new LoadRec();
                    nr.fkind = local;
                    nr.parent = furi;
                    nr.pline = loc.getLineNumber();
                    nr.ens = r.ens;
                    nr.sloc = attrs.getValue("schemaLocation");
                    loadDocs.add(nr);
                    // Xerces can't handle include and redefine elements in a 
                    // namespace that has a catalog entry
                    if (r.nsRes != null) {
                        r.warn("<%s \"%s\"> found in a namespace that has a catalog entry\n", local, nr.sloc);
                    }                    
                }
            }
        }
        
        @Override
        public void endElement (String uri, String lname, String qname) throws SAXException {
            nestLevel--;
        }
        
        @Override
        public void endDocument () throws SAXException {
            nsDecls.claimNSDecls(targetNS, nsNIEMversion.get(targetNS), nsDeclCt);             
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
        private List<MsgRec> msgs;       // empty list if no messages for this document load effort

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
            msgs.add(new MsgRec(false, String.format("[log]"+fmt, args))); 
        }
        
        private void warn (String fmt, Object... args  ) {
            schemaWarnings = true;
            warnFlag = true;
            msgs.add(new MsgRec(true, String.format(fmt, args)));
        }
    }
    
    private class MsgRec {
        boolean warnFlag;
        String msg;
        MsgRec (boolean wf, String m) {
            warnFlag = wf;
            msg = m;
        }
    }
    
    
    // ---- STATIC HELPER FUNCTIONS ----------------------------------
  
    static String canonicalFileURI (String parentURI, String fpath) {
        File pf = null;
        if (parentURI != null) {
            try {
                URI puri = new URI(parentURI);
                if ("file".equals(puri.getScheme())) {
                    pf = new File(puri.getPath());
                    if (!pf.isDirectory()) {
                        pf = pf.getParentFile();
                    }
                }
            } catch (URI.MalformedURIException ex) {
                // IGNORE
            }
        }
        if (pf == null) {
            return canonicalFileURI(fpath);
        }
        File cf = new File(pf, fpath);
        return canonicalFileURI(cf);
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
