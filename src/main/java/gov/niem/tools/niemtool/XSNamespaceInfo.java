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

import static gov.niem.tools.niemtool.NTConstants.APPINFO_NS_URI_PREFIX;
import static gov.niem.tools.niemtool.NTConstants.CONFORMANCE_ATTRIBUTE_NAME;
import static gov.niem.tools.niemtool.NTConstants.CONFORMANCE_TARGET_NS_URI_PREFIX;
import static gov.niem.tools.niemtool.NTConstants.NDR_CT_URI_PREFIX;
import static gov.niem.tools.niemtool.NTConstants.NIEM_RELEASE_PREFIX;
import static gov.niem.tools.niemtool.NTConstants.NIEM_XS_PREFIX;
import java.io.StringReader;
import static java.lang.Math.min;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import javax.xml.parsers.SAXParser;
import org.apache.xerces.xs.StringList;
import org.apache.xerces.xs.XSAnnotation;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSNamespaceItem;
import org.apache.xerces.xs.XSNamespaceItemList;
import org.apache.xerces.xs.XSObjectList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * A class for information contained in Xerces XSModel namespace information items.
 * Each namespace in the schema model has a synthetic annotation. From this,
 * construct the prefix and namespace mappings, and the NDR version.
 * Also generate warnings.
 * 
 * @author Scott Renner <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */
public class XSNamespaceInfo {
    private ParserBootstrap parsers;
    private String schemaRoot;                              // common prefix of schema document absolute file URIs
    private int schemaRootLen;                              // length of common prefix    
    private final List<String> xsNamespaceDocs;             // namespace & contributing documents from XSModel
    private final List<String> xsWarnings;                  // schema warnings derived from XSModel
    private final List<String> xsNIEMWarnings;              // niem-specific schema warnings from XSModel
    private final List<String> nsList;                      // sorted list of namespaces in schema (extension, NIEM model, external)
    private final Map<String,String> nsNDRversion;          // from ct:conformanceTargets; "" for external namespace
    private final Map<String,HashMap<String,String>> nsDecls;     // nsDecls.get(N).get(U)   -> P, where namespace N contains xmlns:P=U
    private final Map<String,HashMap<String,String>> nsPrefix;    // nsPrefixs.get(P).get(U) -> N, a namespace containing xmlns:P=U
    private final Map<String,HashMap<String,String>> nsURI;       // nsURIs.get(U).get(N)    -> P, the prefix bound to U in namespace N

    XSNamespaceInfo (XSModel xs) {
        try {
            parsers = new ParserBootstrap();
        } catch (Exception ex) {
            // IGNORE -- how did you get an XSModel object?
        }
        nsList          = new ArrayList<>();
        nsDecls         = new HashMap<>();
        nsPrefix        = new HashMap<>();
        nsURI           = new HashMap<>();
        nsNDRversion    = new HashMap<>();
        xsNamespaceDocs = new ArrayList<>();
        xsWarnings      = new ArrayList<>();
        xsNIEMWarnings  = new ArrayList<>();
        processNamespaceItems(xs);
    }

    /**
     * Return list of namespaces processed by Xerces. Each entry shows
     * the namespace URI plus list of document that contributed to the namespace.
     * @return list of namespaces and contributing documents
     */
    public List<String> xsNamespaceList () {
        return xsNamespaceDocs;
    } 
    
    /**
     * Return warnings derived from Xerces XSModel. Complains about <ul>
     * <li> Namespace prefix mapped to more than one URI
     * <li> Namespace URI mapped to more than one prefix</ul>
     * @return list of warnings
     */
    public List<String> xsWarningMessages() {
        return xsWarnings;
    }
    
    /**
     * Return NIEM-specific warnings derived from Xerces XSModel.<ul>
     * <li>External namespaces
     * <li>Non-standard prefix for namespace in NIEM model</ul>
     * @return list of NIEM-specfic warnings
     */
    public List<String> xsNIEMWarningMessages() {
        return xsNIEMWarnings;
    }
    
    /**
     * Returns an ordered list of namespaces declared in the schema.
     * NIEM-conforming extension schemas are first.
     * NIEM reference schemas in the release are next.
     * External schemas come last.
     * @return ordered namespace list
     */
    public List<String> nsList() {
        return nsList;
    }
    
    public Map<String,HashMap<String,String>> nsDecls () {
          return nsDecls;
    }
    
    public Map<String,HashMap<String,String>> nsPrefixMaps() {
        return nsPrefix;
    }
    
    public Map<String,HashMap<String,String>> nsURIMaps() {
        return nsURI;
    }
    
    public Map<String,String> nsNDRversion() {
        return nsNDRversion;
    }    
    
    /**
     * Extract information from the Xerces XSModel namespace information items.
     * Each namespace in the schema model has a synthetic annotation contatining
     * the namespace declarations and attributes from the xs:schema element
     * Process it to construct the prefix and namespace mappings, and the NDR version 
     * Generate the warning messages.
     */
    private void processNamespaceItems (XSModel xs) {
        if (xs == null) { return; }
        XSNamespaceItemList nsil = xs.getNamespaceItems();
        if (nsil.isEmpty()) { return; }
        
        // Parse each namespace annotation, generate prefix & namespace mappings
        schemaRoot = nsil.item(0).getDocumentLocations().item(0);
        for (int i = 0; i < nsil.getLength(); i++) {
            XSNamespaceItem nsi = nsil.item(i);   
            String ns = nsi.getSchemaNamespace();
            if (!W3C_XML_SCHEMA_NS_URI.equals(ns)) {
                // Process annnotatons, generate nsPrefixMaps, nsURIMaps, nsNDRversion
                XSObjectList annl = nsi.getAnnotations();
                for (int ai = 0; ai < annl.getLength(); ai++) {
                    XSAnnotation an = (XSAnnotation)annl.get(ai);
                    String as = an.getAnnotationString();
                    processAnnotation(ns, as);
                }
            }
            StringList docs = nsi.getDocumentLocations();
            for (int j = 0; j < docs.getLength(); j++) {
                schemaRoot = commonPrefix(schemaRoot,docs.item(j));
            }
        }
        // Construct list of namespaces and contributing documents
        schemaRootLen = schemaRoot.length();
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
                    xsNamespaceDocs.add(msg.toString());
                } else if (docs.getLength() == 1) {
                    xsNamespaceDocs.add(String.format("%s <- %s\n", ns, docs.item(0).substring(schemaRootLen)));
                } else {
                    xsNamespaceDocs.add(String.format("%s <- NOTHING???\n", ns));
                }
            }
        }
        // Sort namespace list, then strip ordering character
        nsList.sort((s1,s2) -> s1.compareTo(s2));
        nsList.replaceAll((s) -> s.substring(1));             
                
        // Iterate through the prefix mappings, generate multiple-map warnings
        nsPrefix.forEach((prefix,map) -> {
            if (map.keySet().size() > 1) {
                StringBuilder msg = new StringBuilder();
                msg.append(String.format("prefix \"%s\" is mapped to multiple namespaces\n", prefix));
                map.forEach((uri,ns) -> {
                    msg.append(String.format("  to %s in namespace %s\n", uri, ns));
                });
                xsWarnings.add(msg.toString());
            }
        });
        // Iterate through the namespace mappings
        // Generate warnings for multiple-map and non-standard namespace prefix
        nsURI.forEach((uri,map) -> {
            if (map.values().stream().distinct().count() > 1) {
                StringBuilder msg = new StringBuilder();
                msg.append(String.format("multiple prefixes are mapped to namespace %s\n", uri));
                map.forEach((ns,prefix) -> {
                    msg.append(String.format("  prefix \"%s\" in namespace %s\n", prefix, ns));
                });
                xsWarnings.add(msg.toString());
            }
            String expected = ContextMap.commonPrefix(uri);   // expected prefix
            if (!expected.isEmpty()) {
                map.forEach((ns,prefix) -> {
                    if (!expected.equals(prefix)) {
                        xsNIEMWarnings.add(
                            String.format("namespace %s mapped to non-standard prefix %s (in namespace %s)\n",
                                    uri, prefix, ns));
                    }
                });
            }
        });
        // Iterate through the namespace versions, find external namespaces
        nsNDRversion.forEach((ns, ver) -> {
            if ("".equals(ver)) {
                xsNIEMWarnings.add(String.format("namespace %s is external (no NIEM conformance assertion)\n", ns));
            }
        });
    }

    /**
     * Parse the synthetic xs:annotation from a schema namespace item.
     * Adds the prefix mappings and namespace URI mappings for this namespace.
     * Determines the NDR version of this namespace.
     */    
    private void processAnnotation (String ns, String annotation) {
        AnnotationHandler h = new AnnotationHandler(this, ns);
        InputSource is = new InputSource(new StringReader(annotation));
        SAXParser saxp = parsers.sax2Parser();        
        try {
            saxp.parse(is, h);
        } catch (Exception ex) {
            // IGNORE
        }
        nsNDRversion.put(ns, h.ndrVersion);
        // Going to sort the namespace list when complete
        if ("".equals(h.ndrVersion))                 { nsList.add("3"+ns); }    // externals last
        else if (ns.startsWith(NIEM_RELEASE_PREFIX)) { nsList.add("2"+ns); }    // NIEM release namespaces middle
        else                                         { nsList.add("1"+ns); }    // NIEM extensions first
    }        
    
    // SAX handler to parse a Xerces namespace annotation
    private class AnnotationHandler extends DefaultHandler {
        private final XSNamespaceInfo obj;
        private final String ns;            // URI of namespace annotation being processed
        private String ndrVersion = "";     // from ct:conformanceTarget attribute in namespace being processed
        AnnotationHandler (XSNamespaceInfo obj, String ns) {
            super();
            this.obj = obj;
            this.ns = ns;
        }
        @Override
        public void startPrefixMapping(String p, String u) {
            // within namespace ns, encountered a namespace decl mapping prefix p to uri u
            if (!"".equals(p) 
                    && !W3C_XML_SCHEMA_NS_URI.equals(u)
                    && !u.startsWith(APPINFO_NS_URI_PREFIX)
                    && !u.startsWith(CONFORMANCE_TARGET_NS_URI_PREFIX)
                    && !u.startsWith(W3C_XML_SCHEMA_INSTANCE_NS_URI)
                    && !u.startsWith(NIEM_XS_PREFIX)) {
                
                // Record: namespace ns declares uri u is mapped to prefix p
                HashMap<String,String> m = obj.nsDecls.get(ns);
                if (m == null) {
                    m = new HashMap<>();
                    obj.nsDecls.put(ns, m);
                }
                m.put(u, p);
                
                // Record: prefix p is mapped to uri u in namespace ns
                m = obj.nsPrefix.get(p);
                if (m == null) {
                    m = new HashMap<>();
                    obj.nsPrefix.put(p, m);
                }
                m.put(u, ns);
                
                // Record: uri u in namespace ns is bound to prefix p
                m = obj.nsURI.get(u);
                if (m == null) {
                    m = new HashMap<>();
                    obj.nsURI.put(u, m);
                }
                m.put(ns, p);
            }
        } 
        @Override
        // Extract NDR version from NDR conformance target attribute
        public void startElement (String ens, String ename, String raw, Attributes atts) {
            if ("".equals(ndrVersion)) {
                for (int i = 0; i < atts.getLength(); i++) {
                    String auri = atts.getURI(i);
                    String av = atts.getValue(i);
                    String aln = atts.getLocalName(i);
                    if (auri.startsWith(CONFORMANCE_TARGET_NS_URI_PREFIX) && CONFORMANCE_ATTRIBUTE_NAME.equals(aln)) {
                        for (String ctv : av.split("\\s+")) {
                            if (ctv.startsWith(NDR_CT_URI_PREFIX)) {
                                ctv = ctv.substring(NDR_CT_URI_PREFIX.length());
                                int sp = ctv.indexOf('/');
                                if (sp >= 0) {
                                    ctv = ctv.substring(0, sp);
                                    ndrVersion = av;
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }        
    }

    public static String commonPrefix(String s1, String s2) {
        if (s1 == null) {
            if (s2 == null) {
                return "";
            }
            else return s2;
        }
        else if (s2 == null) { return s1; }
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
}
 