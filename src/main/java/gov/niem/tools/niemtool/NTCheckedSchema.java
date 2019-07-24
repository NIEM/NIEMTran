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
 * <li> Schema assembly. Parses each schema document (using vanilla
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
 * <li> Schema construction. Uses the Xerces XML Schema API to construct
 * the XSModel object. Reports: <ul>
 * 
 * <li>any errors or warnings returned by Xerces
 * <li>namespace prefix or namespace URI with more than one binding
 * <li>non-standard prefix for a NIEM namespace
 * <li>external namespaces (those without a NIEM conformance assertion)
 * <li>each namespace constructed, with list of documents contributing to its content
 * <li>result of all XML Catalog resolutions performed during construction</ol>
 *
 * <p>
 * Example usage:
 * <pre><code>
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
 * @author Scott Renner <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class NTCheckedSchema extends NTSchema {
    
    private String schemaRootDirectory = "";                // file URI for root directory of all schema documents 
    private int schemaRootDirectoryLength = 0;              // length of root directory URI
    private List<LoadRec> loadDocs = null;                  // results of each document load attempt
    private HashSet<String> attemptedFiles = null;          // schema document load attempts
    private HashSet<String> loadedFiles = null;             // schema documents successfully loaded
    private HashMap<String, String> namespaceFile = null;   // map of namespace URI to schema document
    private XSNamespaceInfo nsInfo = null;                  // Xerces namespace info item annotations

    
    public NTCheckedSchema () throws ParserConfigurationException {
        super();
    }
    
    public NTCheckedSchema (List<String> catalogs, List<String>schemaOrNSs) 
            throws ParserConfigurationException {
        super(catalogs,schemaOrNSs);
    }   

    
    // ----- RESULTS OF SCHEMA ASSEMBLY AND CONSTRUCTION CHECKING --------------
        
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
     * Returns a list of messages describing each document load attempt during the
     * schema assembly check. Each entry in the list is formatted as follows:
     * <pre><code>
     filename:line# IMPORT path/to/file ns=http://namespace/uri sl=schema/location/path
       resolved namespace != resolved schemaLocation
       namespace URI resolves to non-local resource</code></pre> 
     * <p>
     * Returns an empty list if no errors encountered.
     * @return list of assembly log messages
     */
    public List<String> assemblyLogMessages() {
        return assemblyMessages(true);
    }
    
    /**
     * Returns a string describing document load attempts with warnings. 
     * Documents loaded with no checked error, inconsistency, or ambiguity
     * are not listed.
     * @return string of assembly warnings
     */
    public List<String> assemblyWarningMessages() {
        constructionCheck();
        return assemblyMessages(false);
    }
    
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
        String pilePrefix = "*" + schemaRootDirectory; // convert absolute file URI to relative path
        List<String> res = new ArrayList<>();       
        if (loadDocs.size() < 1) {
            res.add("No initial schema document\n");
            return res;
        }
        for (LoadRec r : loadDocs) {
            if (allMsgs || r.warnFlag) {
                res.add(r.msgHeader());
                for (String m : r.msgs) {
                    m = m.replace(pilePrefix, ""); // convert absolute file URI to relative path
                    res.add("  " + m);
                }
            }
        }
        return res;
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
        if (loadDocs.size() > 0) {
            schemaRootDirectory = loadDocs.get(0).fileURI;
            for (LoadRec r : loadDocs) {
                schemaRootDirectory = commonPrefix(schemaRootDirectory, r.fileURI);
                schemaRootDirectory = commonPrefix(schemaRootDirectory, r.fileURI_2);
            }
            for (String cf : getAllCatalogFiles()) {
                schemaRootDirectory = commonPrefix(schemaRootDirectory, cf);
            }
            schemaRootDirectoryLength = schemaRootDirectory.length();
        }
    }

    private void loadDocument(LoadRec r) {
        try {
            r.nsRes = resolver().resolveURI(r.ns);
            if (r.sloc != null) {    // could be initial schema doc
                r.sloc = r.sloc.trim();
                r.slocRes = resolver().resolveURI(r.sloc);
            }
        } catch (Exception ex) {
            /* ignore */
        }
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
                r.slocRes = canonicalFileURI(r.fileURI, r.sloc);
            }
        } else {
            if (!"load".equals(r.fkind)) {
                r.warn("no schemaLocation attribute in %s element\n", r.fkind);
            }
        }
        if (r.nsRes != null) {
            r.log("namespace resolves to *%s\n", r.nsRes);
        }
        if (r.slocRes != null) {
            r.log("schemaLocation resolves to *%s\n", r.slocRes);
        }
        if (r.nsRes != null && r.slocRes != null) {
            if (r.nsRes.equals(r.slocRes)) {
                loadDocumentFromURI(r, r.nsRes);
            } else {
                r.warn("resolved namespace != resolved schemaLocation\n");
                loadDocumentFromURI(r, r.nsRes);
                loadDocumentFromURI(r, r.slocRes);  // load both files
                r.fileURI = r.nsRes;
                r.fileURI_2 = r.slocRes;            // remember both files for schema root determination later
            }
        } else if (r.nsRes != null) {
            loadDocumentFromURI(r, r.nsRes);
        } else if (r.slocRes != null) {
            loadDocumentFromURI(r, r.slocRes);
        } else {
            r.warn("can't determine a schema document to load");
        }
    }

    private void loadDocumentFromURI(LoadRec r, String furi) {
        if (!furi.startsWith("file:")) {
            return;
        }
        r.fileURI = furi;
        if (attemptedFiles.contains(furi)) {
            if (loadedFiles.contains(furi)) {
                r.log("already loaded *%s\n", furi);
            }
            else {
                r.log("already failed to load *%s\n", furi);
            }
        }
        else {
            r.log("loading *%s\n", furi);
            if (r.ns != null) {
                String lns = namespaceFile.getOrDefault(r.ns, furi);
                if (!lns.equals(furi)) {
                    r.warn("namespace %s also loaded from *%s\n", r.ns, lns);
                }
            }
            namespaceFile.put(r.ns, furi);
            attemptedFiles.add(furi);
            Handler myhandler = new Handler(this.loadDocs, r);
            SAXParser saxp = parsers.sax2Parser();
            try {
                saxp.parse(furi, myhandler);
                loadedFiles.add(furi);
            } catch (SAXException ex) {
                String em = exceptionReason(ex);
                r.warn("can't parse schema document *%s: %s\n", furi, em);
            } catch (IOException ex) {
                String em = exceptionReason(ex);
                r.warn("can't read *%s: %s\n", furi, em);
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
                        r.warn("targetNamespace %s != expected namespace %s\n", tns, r.ens);
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
                    // Xerces can't handle include and redefine elements in a namespace that has a catalog entry
                    if (r.nsRes != null) {
                        r.warn("<%s \"%s\"> found in a namespace that has a catalog entry\n", local, nr.sloc);
                    }                    
                }
            }
        }
    }

    /**
     * A class for recording the results of a schema document load attempt
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
        private String fileURI;          // canonical file URI of the document to be loaded        
        private String fileURI_2;        // other file URI to be loaded (if namespace != schemaLocation)
        private boolean warnFlag;        // false if all messages are log entries
        private List<String> msgs;       // empty list if no messages for this document load attempt

        LoadRec() {
            fkind = parent = ens = ns = sloc = nsRes = slocRes = fileURI = fileURI_2 = null;
            pline = 0;
            warnFlag = false;
            msgs = new ArrayList<>();
        }
        
        private String msgHeader () {
            String prp;
            if (parent == null) {
                prp = "[initial load]";
            } else {
                prp = parent.substring(schemaRootDirectoryLength);
            }
            String h = String.format("%s:%d %s ns=%s sl=%s\n",
                    prp, pline, fkind.toUpperCase(), ns, sloc);
            return h;           
        }
        
        private void warn (String fmt, Object... args  ) {
            warnFlag = true;
            log(fmt, args);
        }

        /**
         * Put a * character before the string format (i.e. "*%s") if the
         * string is a file URI that you want converted into a path 
         * relative to schemaRootDirectory.
         */        
        private void log (String fmt, Object... args) {
            msgs.add(String.format(fmt, args));             
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
