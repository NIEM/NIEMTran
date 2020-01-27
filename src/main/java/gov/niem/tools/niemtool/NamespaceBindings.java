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

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

/**
 * A class to represent mappings between namespace URIs and the prefix string
 * to be used in a JSON-LD context. Guarantees that each prefix is bound
 * to exactly one URI, and that each URI is bound to exactly one prefix.
 * It is used when parsing the schema.  It is also used when translating
 * the XML document -- which may include namespace declarations not found
 * in the schema.
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class NamespaceBindings {
    
    private final HashMap<String,String> prefixOf;  // prefixOf.get(U) -> P, where namespace U is bound to prefix P
    private final HashMap<String,String> uriOf;     // uriOf.get(P)    -> U, where prefix P is bound to namespace U
    private JsonObject contextObj = null;           // cached JsonObject with context bindings
    private String contextString = null;            // cached JSON string with context bindings
    
    public NamespaceBindings () {
        prefixOf = new HashMap<>();
        uriOf    = new HashMap<>();
    }
    
    public NamespaceBindings (HashMap<String,String>p, HashMap<String,String>u) {
        prefixOf = p;
        uriOf = u;
    }
    
    public NamespaceBindings (NamespaceBindings orig) {
        prefixOf = new HashMap<>(orig.prefixOf);
        uriOf = new HashMap<>(orig.uriOf);
    }
       
    public JsonObject contextObj () {
        if (contextObj == null) {
            contextObj = new JsonObject();
            uriOf.forEach((prefix,nsuri) -> {
                if (nsuri.endsWith("#")) {
                    contextObj.addProperty(prefix, nsuri);
                }
                else {
                    contextObj.addProperty(prefix, nsuri + "#");
                }
            });
        }
        return contextObj;
    }
    
    public String contextString () {
        if (contextString == null) {
            contextString = contextObj().toString();
        }
        return contextString;
    }
    
    /**
     * Returns the namespace prefix bound to the specified URI in this map.
     * @param uri
     * @return prefix bound to uri
     */
    public String getPrefix (String uri) {
        return prefixOf.get(uri);
    }
    
    /**
     * Returns the namespace URI bound to the specified prefix in this map.
     * @param prefix
     * @return uri bound to prefix
     */
    public String getURI (String prefix) {
        return uriOf.get(prefix);
    }
    
    /**
     * Returns the map of uri to prefix
     * @return 
     */
    public Map<String,String> getDecls () {
        return prefixOf;
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
        String uriPrefix = prefixOf.getOrDefault(uri, "");
        if (uriPrefix.isEmpty()) {

            // Generate unique synthetic prefix if specified prefix is bound
            if (uriOf.containsKey(prefix)) {
                String base = prefix;
                int ct = 1;
                do {
                    prefix = String.format("%s_%d", base, ct++);
                } while (uriOf.containsKey(prefix));
            }
            uriOf.put(prefix, uri);
            prefixOf.put(uri, prefix);
            contextObj = null;
            contextString = null;
        }
    }
    
}
