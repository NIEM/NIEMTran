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
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class XSNamespaceInfo {
    private ParserBootstrap parsers;
    private String schemaRoot;                              // common prefix of schema document absolute file URIs
    private int schemaRootLen;                              // length of common prefix    
    private final List<String> xsNamespaceDocs;             // namespace & contributing documents from XSModel
    private final List<String> nsList;                      // sorted list of namespaces in schema (extension, NIEM model, external)
    private final Map<String,String> nsNDRversion;          // from ct:conformanceTargets; "" for external namespace
    private List<String> xsWarnings;                  // schema warnings derived from XSModel
    private List<String> xsNIEMWarnings;              // niem-specific schema warnings from XSModel    
    
    // For each namespace, record mapping of namespace prefix to URI declared
    // nsDecls.get(N).get(P)==U, where namespace N contains xmlns:P=U
    // Within a namespace, P can only be mapped to a single U
    private final Map<String,HashMap<String,String>> nsDecls;   
  
    // For each prefix, record how it is mapped in each namespace
    // nsPrefixs.get(P).get(N)==U, a namespace containing xmlns:P=U
    // For a prefix P, N can only map P to a single U
    private final Map<String,HashMap<String,String>> nsPrefix;    
    
    // For each URI, record how each namespace maps it to a prefix
    // nsURIs.get(U)->list of (P,N) tuples, where P is mapped to U in namespace N
    // A uri U can be mapped to any number of prefix Ps in a single namespace N
    private final Map<String,List<MapRec>> nsURI;       

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
        xsWarnings      = null;
        xsNIEMWarnings  = null;
        processNamespaceItems(xs);
    }

    /**
     * Return list of namespaces processed by Xerces. Each entry shows
     * the namespace URI plus list of document that contributed to the namespace.
     * @return list of namespaces and contributing documents
     */
    public List<String> xsNamespaceList() {
        return xsNamespaceDocs;
    }
    
    /**
     * Return warnings derived from Xerces XSModel. Complains about <ul>
     * <li> Namespace prefix mapped to more than one URI
     * <li> Namespace URI mapped to more than one prefix</ul>
     * @return list of warnings
     */
    public List<String> xsWarningMessages() {
        if (xsWarnings == null) {
            xsWarnings = new ArrayList<>();
            // Iterate through the prefix mappings, 
            // generate warning when a prefix is mapped to >1 URI
            nsPrefix.forEach((prefix, map) -> {
                long ctURIsMappedToPrefix = map.values().stream().distinct().count();
                if (ctURIsMappedToPrefix > 1) {
                    xsWarnings.add(String.format("prefix \"%s\" is mapped to multiple namespaces:\n", prefix));
                    map.forEach((ns, uri) -> {
                        xsWarnings.add(String.format("  to %s in namespace %s\n", uri, ns));
                    });
                }
            });
            // Iterate through the URI mappings,
            // generate warning when a URI is mapped to >1 prefix
            nsURI.forEach((uri, nlst) -> {
                long ctPrefixMappedToURI = nlst.stream().map(MapRec::getPrefix).distinct().count();
                if (ctPrefixMappedToURI > 1) {
                    xsWarnings.add(String.format("multiple prefixes are mapped to namespace %s:\n", uri));
                    nlst.forEach((mr) -> {
                        xsWarnings.add(String.format("  prefix \"%s\" in namespace %s\n", mr.prefix, mr.ns));
                    });
                }
            });  
        }
        return xsWarnings;
    }
    
    /**
     * Return NIEM-specific warnings derived from Xerces XSModel.<ul>
     * <li>External namespaces
     * <li>Non-standard prefix for namespace in NIEM model</ul>
     * @return list of NIEM-specfic warnings
     */
    public List<String> xsNIEMWarningMessages() {
        if (xsNIEMWarnings == null) {
            xsNIEMWarnings = new ArrayList<>();
            // Iterate through the URI mappings,
            // Generate warning for non-standard prefix mappings        
            nsURI.forEach((uri, nlst) -> {
                String expected = ContextMap.wellKnownPrefix(uri);   // expected prefix
                if (!expected.isEmpty()) {
                    nlst.forEach((mr) -> {
                        if (!expected.equals(mr.prefix)) {
                            xsNIEMWarnings.add(
                                    String.format("namespace %s mapped to non-standard prefix %s (in namespace %s)\n",
                                            uri, mr.prefix, mr.ns));
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
    
    public Map<String,String> nsNDRversion() {
        return nsNDRversion;
    }    
    
    /**
     * Extract information from the Xerces XSModel namespace information items.
     * Each namespace in the schema model has a synthetic annotation contatining
     * the namespace declarations and attributes from the xs:schema element
     * Process it to construct the prefix and namespace mappings, and the NDR version 
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
        // Sort namespace list, then strip ordering character prepended by processAnnotation
        nsList.sort((s1,s2) -> s1.compareTo(s2));
        nsList.replaceAll((s) -> s.substring(1));             
    }

    /**
     * Parse the synthetic xs:annotation from a schema namespace item.
     * Adds the prefix mappings and namespace URI mappings for this namespace.
     * Determines the NDR version of this namespace.
     */    
    private void processAnnotation (String ns, String annotation) {
        AnnotationHandler h = new AnnotationHandler(this, ns);
        InputSource is = new InputSource(new StringReader(annotation));
        try {
            SAXParser saxp = parsers.sax2Parser();  
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
        private final XSNamespaceInfo obj;  // the XSNamespaceInfo object doing the processing
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
                
                // Record: namespace ns declares prefix p is mapped to URI u
                HashMap<String,String> map = obj.nsDecls.get(ns);
                if (map == null) {
                    map = new HashMap<>();
                    obj.nsDecls.put(ns, map);
                }
                map.put(p, u);
                
                // Record: prefix p is mapped to uri u in namespace ns
                map = obj.nsPrefix.get(p);
                if (map == null) {
                    map = new HashMap<>();
                    obj.nsPrefix.put(p, map);
                }
                map.put(ns, u);
                
                // Record: uri u in namespace ns is bound to prefix p
                List<MapRec> mlst = obj.nsURI.get(u);
                if (mlst == null) {
                    mlst = new ArrayList<>();
                    obj.nsURI.put(u, mlst);
                }
                mlst.add(new MapRec(p, ns));
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
                                    ndrVersion = ctv;
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }        
    }
    
    private class MapRec {
        String prefix;
        String ns;
        MapRec (String p, String n) {
            prefix = p;
            ns = n ;
        }
        String getPrefix() {
            return prefix;
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
 