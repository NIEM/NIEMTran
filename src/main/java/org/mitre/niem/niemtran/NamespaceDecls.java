/*
 * Copyright 2020 The MITRE Corporation. All rights reserved.
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
package org.mitre.niem.niemtran;

import static org.mitre.niem.niemtran.NTConstants.NIEM_RELEASE_PREFIX;
import static org.mitre.niem.niemtran.NTConstants.RDF_NS_URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class for information about namespaces derived from parsing a schema 
 * document set. Builds a list of all the namespace declarations, remembering
 *   the prefix/uri pair, 
 *   schema document and line number of the declaration, 
 *   target namespace of the schema document
 *   element nesting depth of the declaration
 *   order of declaration encountered (1st, 2nd, ...)
 * Generates warning messages about 
 *   one prefix declared for multiple URIs
 *   one URI with multiple prefixes declared
 *   NIEM namespaces that does not have its well-known prefix declared
 * 
 * @author SAR
  * <a href="mailto:sar@mitre.org">sar@mitre.org</a>* 
 */

public class NamespaceDecls {   
    
    static List<NSDeclRec> emptyList = new ArrayList<>();
    
    private final List<NSDeclRec> maps;                 // array of namespace declaration records
    private Map<String,List<NSDeclRec>> pfmap = null;   // pfmap.get(P)=  list of decls with prefix P
    private Map<String,List<NSDeclRec>> urimap = null;  // urimap.get(U)= list of decls with URI U
    private List<String> warnMsgs = null;
    
    NamespaceDecls () {
        maps = new ArrayList<>();
    }
       
    /**
     * Called when xmlns:prefix="uri" is encountered at the specified file
     * and line number. The SAX parser does not know the target namespace 
     * or the NIEM version of the schema document at this point. Those are
     * set when the schema document parsing is complete.
     * @param prefix
     * @param uri
     * @param filepath
     * @param lineNum
     * @param nestLevel
     */
    public void addNamespaceDecl (String prefix, String uri, String filepath, int lineNum, int nestLevel) {
        maps.add(new NSDeclRec(prefix, uri, "unknown", filepath, lineNum, nestLevel, maps.size()+1));
        pfmap = null;
    }
    
    /**
     * Called to set the target namespace and NIEM version of the schema document 
     * containing the N namespace declarations encountered during its parsing
     * @param tns
     * @param niemVersion
     * @param nrecs 
     */
    public void claimNSDecls (String tns, String niemVersion, int nrecs) {
        int size = maps.size();
        for (int i = size - nrecs; i < size; i++) {
            NSDeclRec ndr = maps.get(i);
            ndr.targetNS = tns;
            if (niemVersion == null) {
                ndr.sortPriority = 3;
            }
            else if (tns.startsWith(NIEM_RELEASE_PREFIX)) {
                ndr.sortPriority = 2;
            }
            else {
                ndr.sortPriority = 1;
            }
        }
        pfmap = null;
    }
    
    /**
     * Constructs and returns various warnings based on the set of 
     * namespace declarations found in a schema document set.
     * @return 
     */
    public List<String> nsDeclWarnings () {
        if (warnMsgs != null) { 
            return warnMsgs; 
        }
        warnMsgs = new ArrayList<>();
        createIndex();
        pfmap.keySet().stream().sorted().forEach((p) -> {
            List<NSDeclRec> ml = pfmap.get(p);
            long mapct = ml.stream().map(m -> m.uri).distinct().count();
            if (mapct > 1) {
                warnMsgs.add(String.format("prefix \"%s\" mapped to multiple URIs:\n", p));
                ml.forEach((m) -> {
                    warnMsgs.add(String.format("  to \"%s\" at *%s:%d\n", m.uri, m.fp, m.linenum));
                });
            }
        });
        urimap.keySet().stream().sorted().forEach((u) -> {
            List<NSDeclRec> ml = urimap.get(u);
            long mapct = ml.stream().map(m -> m.prefix).distinct().count();
            if (mapct > 1) {
                warnMsgs.add(String.format("uri \"%s\" mapped to multiple prefixes:\n", u));
                ml.forEach((m) -> {
                    warnMsgs.add(String.format("  to \"%s\" at *%s:%d\n", m.prefix, m.fp, m.linenum));
                });
            }
        });
        ContextMap cmap = ContextMap.getInstance();
        urimap.keySet().stream().sorted().forEach((u) -> {
            String niemPrefix = cmap.wellKnownPrefix(u);
            if (niemPrefix != null) {
                List<NSDeclRec> ml = urimap.get(u);
                long nonstd = ml.stream().filter(m -> !m.prefix.equals(niemPrefix)).count();
                if (nonstd > 0) {
                    warnMsgs.add(String.format("NIEM namespace \"%s\" bound to non-standard prefix:\n", u));
                    ml.forEach((m) -> {
                        if (!niemPrefix.equals(m.prefix)) {
                            warnMsgs.add(String.format("  to \"%s\" at *%s:%d\n", m.prefix, m.fp, m.linenum));
                        }
                    });
                }
            }
        });
        String hdr = "Well-known \"rdf\" prefix bound to non-standard namespace URI:\n";
        for (NSDeclRec m: pfmap.getOrDefault("rdf", emptyList)) {
            if (!RDF_NS_URI.equals(m.uri)) {
                if (hdr != null) warnMsgs.add(hdr);
                warnMsgs.add(String.format("  to \"%s\" at *%s:%d\n", m.uri, m.fp, m.linenum));
                hdr = null;
            }
        }
        hdr = "RDF namespace URI bound to non-standard prefix:\n";
        for (NSDeclRec m: urimap.getOrDefault(RDF_NS_URI, emptyList)) {
            if (!"rdf".equals(m.prefix)) {
                if (hdr != null) warnMsgs.add(hdr);
                warnMsgs.add(String.format("  to \"%s\" at *%s:%d\n", m.prefix, m.fp, m.linenum));
                hdr = null;
            }
        }
        return warnMsgs;
    }
    
    public Map<String,List<NSDeclRec>> prefixDecls () {
        createIndex();
        return pfmap;
    }
    
    public List<NSDeclRec> nsDecls () {
        createIndex();
        return maps;
    }

    private void createIndex () {
        if (pfmap != null) { return; }
        pfmap  = new HashMap<>();
        urimap = new HashMap<>();
        maps.forEach((m) -> {
            if (pfmap.get(m.prefix) == null) { pfmap.put(m.prefix, new ArrayList<>()); }
            if (urimap.get(m.uri) == null)   { urimap.put(m.uri, new ArrayList<>()); }
            pfmap.get(m.prefix).add(m);
            urimap.get(m.uri).add(m);
        });
        // Sort the namespace declarations into priority order for prefix mapping
        // First, declarations in extension schemas (assume designers know what they want)
        // Second, declarations in NIEM reference schemas (prefer well-known prefixes)
        // Last, declarations in external schemas.
        // For declarations in the same namespace, those from outer elements before inner.
        // Break ties by retaining the order in which declarations were found.
        Collections.sort(maps);
    }
       
    //
    // A structure for recording a namespace declaration
    //
    class NSDeclRec implements Comparable<NSDeclRec> {
        String prefix;          // xmlns:prefix="uri"
        String uri;             // xmlns:prefix="uri"
        String targetNS;        // declaration found in this namespace
        String fp;              // path of schema document
        int linenum;            // line number
        int nestLevel;          // depth of this element in schema document
        int decNum;             // nth declaration found in schema pile
        int sortPriority = 0;
        
        NSDeclRec (String p, String u, String pns, String f, int n, int lvl, int ct) {
            prefix = p;
            uri = u;
            targetNS = pns;
            fp = f;
            linenum =  n;
            nestLevel = lvl;
            decNum = ct;
        }
        
        // Schema document type comes first: extension, reference, external
        // Within a namespace, declarations in outer schema elements come before inner
        // For namespaces of sort priority, sort in order of parsing
        @Override
        public int compareTo (NSDeclRec o) {
            if (sortPriority < o.sortPriority) { return -1; }
            else if (sortPriority > o.sortPriority) { return 1; }
            else if (targetNS.equals(o.targetNS)) {
                if (nestLevel < o.nestLevel) { return -1; }
                else { return 1; }
            }
            else if (decNum < o.decNum) { return -1; }
            return 1;
        }
    }
}
