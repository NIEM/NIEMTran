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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * A class to represent the aspects of a NIEM schema needed to drive translation
 * between NIEM XML, NIEM JSON, and NIEM RDF. 
 * 
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class NTSchemaModel {
    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();
       
    private final String modelVersion = "1.0";
    
    private NamespacePrefixMap prefixMap;               // maps namespace URI to JSON-LD prefix 
    private Map<String,String> attributes;              // attribute URI -> XSD type (string, list/IDREF, etc.)
    private Map<String,String> simpleElements;          // element URI   -> XSD type
    private Map<String,String> externalNSHandler;       // namespace URI -> name of class for components from this NS
    private boolean hasWildcard;
    
    public NTSchemaModel () {     
        this.prefixMap        = new NamespacePrefixMap();
        this.attributes       = new HashMap<>();
        this.simpleElements   = new HashMap<>();
        this.externalNSHandler = new HashMap<>();
        this.hasWildcard = false;
    }
    
    public NTSchemaModel(String jsonString) throws FormatException {
        NTSchemaModel m = null;
        try {
            m = gson.fromJson(jsonString, NTSchemaModel.class);
            this.prefixMap        = m.prefixMap;
            this.attributes       = m.attributes;
            this.simpleElements   = m.simpleElements;
            this.externalNSHandler = m.externalNSHandler;
            this.hasWildcard       = m.hasWildcard;
        } catch (RuntimeException ex) {
            throw (new FormatException("Can't initialize NTSchemaModel", ex)); 
        }
    }
    
    public NTSchemaModel(Reader r) throws FormatException {
        NTSchemaModel m = null;
        try {
            m = gson.fromJson(r, NTSchemaModel.class);
            this.prefixMap        = m.prefixMap;
            this.attributes       = m.attributes;
            this.simpleElements   = m.simpleElements;
            this.externalNSHandler = m.externalNSHandler;
            this.hasWildcard       = m.hasWildcard;
        } catch (RuntimeException ex) {
            throw (new FormatException("Can't initialize NTSchemaModel", ex));            
        }
    }
    
    public NamespacePrefixMap prefixMap () {
        return prefixMap;
    }
    
    public String prefixOf (String uri) {
        return prefixMap.getPrefix(uri);
    }
    
    public String attributeType(String uri) {
        return attributes.get(uri);
    }
    
    public String simpleElementType(String uri) {
        return simpleElements.get(uri);
    }
    
    public Map<String,String> attributes () {
        return attributes;
    }
    
    public Map<String,String> simpleElements () {
        return simpleElements;
    }
    
    public Map<String,String> externalNSHandler () {
        return externalNSHandler;
    }
    
    public boolean hasWildcard() {
        return hasWildcard;
    }
    
    public void setHasWildcard(boolean val) {
        hasWildcard = val;
    }
    
    public void addAttribute(String uri, String type) {
        attributes.put(uri, type);
    }
    
    public void addSimpleElement(String uri, String type) {
        simpleElements.put(uri, type);    
    }
    
    public void assignPrefix (String prefix, String namespace) {
        prefixMap.assignPrefix(prefix, namespace);
    }
    
    public void addExternalNS (String ns) {
        externalNSHandler.put(ns, "");
    }
    
    public void generateContext (JsonObject cxt) {
        prefixMap.getPrefixMap().forEach((uri, prefix) -> {
            cxt.addProperty(prefix, uri);
        });
    }
    
    public String toJson () {
        return gson.toJson(this);
    }
    
}

