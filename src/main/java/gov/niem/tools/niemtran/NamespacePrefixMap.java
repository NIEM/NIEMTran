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

import static gov.niem.tools.niemtran.NTConstants.RDF_NS_URI;
import java.util.HashMap;
import java.util.Map;

/**
 * A class to represent the map between namespace URIs and the prefix string
 * to be used in a JSON-LD context. Guarantees that each prefix is bound
 * to exactly one URI, and that each URI is bound to exactly one prefix.
 * 
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class NamespacePrefixMap {
    
    private final Map<String,String> prefixOf;  // prefixOf.get(U) -> P, where namespace U is bound to prefix P
    
    public NamespacePrefixMap () {
        prefixOf     = new HashMap<>();
        prefixOf.put(RDF_NS_URI, "rdf");            // never a munged prefix for RDF
    } 
  
    public NamespacePrefixMap (NamespacePrefixMap orig) {
        prefixOf     = new HashMap<>(orig.prefixOf);
    }
    
    public Map<String,String> getPrefixMap () {
        return prefixOf;
    }
         
    /**
     * Returns the namespace prefix bound to the input URI.
     * @param uri
     * @return prefix bound to uri
     */
    public String getPrefix (String uri) {
        return prefixOf.get(uri);
    }
    
    /**
     * Assign a namespace prefix to a namespace URI.
     * Does nothing if URI is already bound.
     * Creates a unique synthetic prefix if the prefix is already bound.
     * @param prefix of namespace declaration
     * @param uri of namespace declaration
     */
    public void assignPrefix(String prefix, String uri) {

        // Is this URI already bound?  If so, do nothing
        if (!prefixOf.containsKey(uri)) {

            // Generate unique synthetic prefix if specified prefix is bound
            // "prefix" is munged to "prefix_1"
            // "prfx_1" is munged to "prfx_1x1"
            if (prefixOf.containsValue(prefix)) {
                String base = prefix;
                String spat = "%s_%d";
                int ct = 0;
                if (prefix.matches("_[0-9]+$")) {
                    spat = "%sx%d";
                }
                do {
                    prefix = String.format(spat, base, ++ct);
                } while (prefixOf.containsValue(prefix));
            }
            prefixOf.put(uri, prefix);
        }
    }
    
}
