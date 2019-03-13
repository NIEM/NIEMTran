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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import static gov.niem.tools.niemtool.NTConstants.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * @author Scott Renner <sar@mitre.org>
 */
public class NTXMLtoJSON {

    static final HashSet<String> integerTypes = new HashSet<>();
    static final String[] integerTypeNames = new String[] {
        "integer", "nonPositiveInteger", "long", "nonNegativeInteger", 
        "negativeInteger", "int", "unsignedLong", "positiveInteger", "short", 
        "unsignedInt", "byte", "unsignedShort","unsignedByte"
    };
    static {
        integerTypes.addAll(Arrays.asList(integerTypeNames));
    }       
    private static SAXParser saxParser = null;
    
    private NTSchemaModel model = null;

    NTXMLtoJSON (NTSchemaModel m) {
        model = m;
    }
    
    NTXMLtoJSON (Reader r) throws FormatException {
        model = new NTSchemaModel(r);
    }
    
    NTXMLtoJSON (String s) throws FormatException {
        model = new NTSchemaModel(s);
    }
        
    public String xml2jsonString (InputStream is) {
        return null;
    }
    
    public JsonObject xml2jsonObject (InputStream is) throws ParserConfigurationException, SAXException, IOException {
        if (saxParser == null) {
            try {
                SAXParserFactory spf = SAXParserFactory.newInstance();
                spf.setValidating(false);
                spf.setNamespaceAware(true);
                saxParser = spf.newSAXParser();            
            } catch (SAXException ex) {
                throw (new ParserConfigurationException("Can't initialize suitable SAX parser" + ex.getMessage()));
            }
        }
        JsonObject root = new JsonObject();
        MyHandler handler = new MyHandler(model, root);
        saxParser.parse(is, handler);
        return root;
    }
    
    private class MyHandler extends DefaultHandler {

        private final NTSchemaModel model;
        private final JsonObject root;
        private final HashMap<String,Stack<String>> pmap;       // current and shadowed prefix mappings
        private final StringBuilder content;                    // simple element content, could come in >1 callback call
        private final Stack<JsonObject> pstack;                 // stack of parent objects, one per nested xml element
        private final Stack<JsonObject> cstack;                 // stack of context value objects, one per, usually null
        private JsonObject cxt;                                 // current context object
        private int preserveCt;                                 // # of xml:space="preserve" attributes in current stack
        private boolean needRDF;                                // does @context need an RDF mapping?
        
        MyHandler (NTSchemaModel m, JsonObject r) {
            super();
            model = m;
            root = r;
            content = new StringBuilder(80);
            pmap = new HashMap<>();
            pstack = new Stack<>();
            cstack = new Stack<>();
            pstack.push(r);
            // cstack.push(null);
            cxt = null;
            preserveCt = 0;
            needRDF = false;
        }
        
        @Override
        public void startPrefixMapping(String prefix, String uri) {
            // System.out.println(String.format("startPrefix(%s,%s)", prefix, uri));
            if ("rdf".equals(prefix) && !RDF_NS_URI.equals(uri)) {
                // FIXME oh, you evil bastard
            }
            else if(W3C_XML_SCHEMA_INSTANCE_NS_URI.equals(uri)) {
                // do nothing
            }
            // Don't make a context entry for namespace decls already in effect
            else {
                String cmap = addPrefixMap(prefix, uri);
                if (cmap == null || !cmap.equals(uri)) {
                    if (cxt == null) {
                        cxt = new JsonObject();
                    }
                    cxt.addProperty(prefix, uri);
                }
            }
        }

        @Override
        public void startElement(String eNamespace, String eLocalName, String eQName, Attributes atts) {
            
            // System.out.println(String.format("startElement(%s,%s,%s,natts=%d", eNamespace, eLocalName, eQName, atts.getLength()));
            
            // Process element attributes, store in JsonObject for this element
            // New namespace declarations in this element will already be in "cxt" JsonObject
            JsonObject cobj = new JsonObject();
            for (int i = 0; i < atts.getLength(); i++) {
                String aqn  = atts.getQName(i);
                String aln =  atts.getLocalName(i);
                String auri = atts.getURI(i);
                String av   = atts.getValue(i);
                System.out.println(String.format(" %s   %s = %s", auri, aqn, av));
                if ("xml:base".equals(aqn)) {
                    cobj.addProperty("@base", av);                   
                }
                else if ("xml:lang".equals(aqn)) {
                    cobj.addProperty("@language", av);
                }
                else if ("xml:space".equals(aqn)) {
                    if ("preserve".equals(av)) {
                        preserveCt++;
                        cobj.addProperty("xml:space", "preserve");  // remove before setting @context
                    }
                }
                else if (auri.startsWith(STRUCTURES_NS_URI_PREFIX)) {
                    if (aqn.endsWith(":id") || aqn.endsWith(":ref") || aqn.endsWith(":uri")) {
                        cobj.addProperty("@id", av);
                    }
                }
                else if (W3C_XML_SCHEMA_INSTANCE_NS_URI.equals(auri)) {
                    // do nothing
                }
                else {
                    String typeURI = buildURI(auri, aln);
                    String simpleType = model.attributeType(typeURI);
                    JsonElement jv = genJsonValue(av, simpleType);
                    cobj.add(aqn, jv);
                }
            }
            // Mixed content is not allowed, and so we can use a StringBuilder
            // instead of a stack of element character content
            String simpleType = model.simpleElementType(buildURI(eNamespace, eLocalName));
            if (simpleType != null) {
                content.setLength(0);
            }
            pstack.push(cobj);
            cstack.push(cxt);
            cxt = null;
        }
        
        @Override
        public void characters (char[] ch, int start, int length) {
            content.append(ch, start, length);
        }

        @Override
        public void endElement(String eNamespace, String eLocalName, String eQName) {
            
            String key = eQName;
            String simpleType = model.simpleElementType(buildURI(eNamespace, eLocalName));
            JsonObject cobj = pstack.pop();
            JsonObject pobj = pstack.peek();
            cxt =  cstack.pop();
            
            // Simple element? Get the string value
            String val = null;
            JsonElement jsonVal = null;
            if (simpleType != null) {
                val = content.toString();
                if (preserveCt < 1) {
                    val = val.trim();
                }
                jsonVal = genJsonValue(val, simpleType);
            }
            // Handle xml:space attribute
            if (cobj.has("xml:space")) {
                cobj.remove("xml:space");
                preserveCt--;
            }       
            // Handle context
            if (cxt != null) {
                
                // Usual case, namespace declarations on root element
                if (pobj == root) {
                    pobj.add("@context", cxt);
                }
                // Namespace declarations below root element, blech
                else {
                    int ci = eQName.indexOf(':');
                    String eQNamePrefix = eQName.substring(0, ci);

                    // Element declares its own prefix, double-blech
                    if (cxt.has(eQNamePrefix)) {
                        
                        // Element namespace decl shadows its own prefix
                        // Or a sibling element has declared this prefix differently
                        // Triple-blech! No prefix for you!
                        JsonObject pcxt = cstack.peek();
                        if ((pcxt != null && pcxt.has(eQNamePrefix) && pcxt.get(eQNamePrefix).getAsString().equals(eNamespace))
                                || prefixIsShadowing(eQNamePrefix)) {
                            key = buildURI(eNamespace, eLocalName);
                            cxt.remove(eQNamePrefix);
                        }
                        // Put element namespace decl into parent object context
                        else {
                            pcxt = cstack.pop();
                            if (pcxt == null) {
                                pcxt = new JsonObject();
                            }
                            pcxt.addProperty(eQNamePrefix, eNamespace); // replace previous value
                            cstack.push(pcxt);
                        }
                        // 
                    }
                    // (Remaining) namespace decls apply only to child elements
                    if (simpleType != null && cxt.size() > 0) {
                        cobj.add("@context", cxt);
                    }
                }
            }
            // Replace all single-element array values with the element vzlue          
            for (String k : cobj.keySet()) {
                JsonElement v = cobj.get(k);
                if (v.isJsonArray()) {
                    JsonArray va = (JsonArray)v;
                    if (va.size() == 1) {
                        JsonElement ve = va.get(0);
                        cobj.add(k, ve);
                    }
                }
            }            
            // Special handling for augmentation 
            // Add all of their children as child of the parent
            // The augmentation element itself does not appear in the json
            if (eLocalName.endsWith("Augmentation")) {
                for (String augKey : cobj.keySet()) {
                    JsonElement v = cobj.get(augKey);
                    JsonArray pea = pobj.getAsJsonArray(augKey);
                    if (pea == null) {
                        pea = new JsonArray();
                        pobj.add(augKey, pea);
                    }
                    pea.add(v);
                }
            }
            // An ordinary element is added as a child of the parent
            else {
                JsonElement pv = cobj;
                JsonArray pea = pobj.getAsJsonArray(key);
                if (pea == null) {
                    pea = new JsonArray();
                    pobj.add(key, pea);
                }
                if (simpleType != null) {
                    if (cobj.size() > 0) {
                        cobj.add("rdf:value", jsonVal);
                        needRDF = true;
                    } else {
                        pv = jsonVal;
                    }
                }
                pea.add(pv);
            }          
        }
        
        @Override
        public void endDocument () {
            // System.out.println("end document");
            if (needRDF) {
                cxt = (JsonObject)root.get("@context");
                if (!cxt.has("rdf")) {
                    cxt.addProperty("rdf", RDF_NS_URI);
                }
            }
                
        }

        @Override
        public void endPrefixMapping(String prefix) {
            // System.out.println(String.format("endPrefix(%s)", prefix));
            removePrefixMap(prefix);
        }
        
        public JsonElement genJsonValue (String val, String simpleType) {
            if (simpleType == null) {
                return new JsonPrimitive(val);
            }
            // Handle list type
            if (simpleType.startsWith("list/")) {
                val = val.trim();
                String itemType = simpleType.substring(5);
                String[] values = val.split("\\s+");
                
                // No array for list with one element 
                if (values.length < 2) {
                    return genJsonValue(values[0], itemType);
                }
                // Create array for >1 element
                JsonArray res = new JsonArray();
                for (int i = 0; i < values.length; i++) {
                    res.add(genJsonValue(values[i], itemType));
                }
                return res;
            }
            // Not a list type, return JsonPrimitive based on simpleType
            else if ("boolean".equals(simpleType)) {
                return new JsonPrimitive(Boolean.valueOf(val));
            } 
            else if ("decimal".equals(simpleType)) {
                return new JsonPrimitive(new BigDecimal(val));
            } 
            else if ("double".equals(simpleType)) {
                return new JsonPrimitive(new Double(val));
            } 
            else if ("float".equals(simpleType)) {
                return new JsonPrimitive(new Float(val));
            } 
            else if (integerTypes.contains(simpleType)) {
                return new JsonPrimitive(new BigInteger(val));
            } 
            return new JsonPrimitive(val);            
        }
        
        /**
         * Add a prefix mapping.  Returns the prior, now-shadowed mapping.
         * Returns null if prefix not currently mapped.
         * @param prefix namespace prefix
         * @param uri namespace URI
         * @return shadowed namespace URI, or null
         */
        private String addPrefixMap (String prefix, String uri) {
            Stack<String> ms = pmap.get(prefix);
            if (ms == null) {
                ms = new Stack<>();
                ms.push(uri);
                pmap.put(prefix, ms);
                return null;
            }
            String rv = null;
            if (ms.size() > 0 )  { 
                rv = ms.peek();
            }
            if (!uri.equals(rv)) {
                ms.push(uri);
            }
            return rv;
        }
        
        private String removePrefixMap (String prefix) {
            Stack<String> ms = pmap.get(prefix);
            if (ms != null) {
                return ms.pop();
            }
            return null;
        }

        private String prefixMap(String prefix) {
            Stack<String> ms = pmap.get(prefix);
            if (ms != null) {
                return ms.peek();
            }
            return null;
        }
        
        private boolean prefixIsShadowing (String prefix) {
            Stack<String> ms = pmap.get(prefix);
            return (ms != null && ms.size() > 1);
        }

        private String buildURI (String ns, String name) {
            if (ns.endsWith("#")) {
                return ns + name;
            }
            else {
                return ns + "#" + name;
            }
        }

    }
}



