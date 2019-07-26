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
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import static gov.niem.tools.niemtool.NTConstants.STRUCTURES_NS_URI_PREFIX;
import static gov.niem.tools.niemtool.Translate.X2J_EXTENDED;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A SAX handler class for converting NIEM XML to NIEM JSON
 * @author Scott Renner, The MITRE Corporation
 */
public class NTXMLHandler extends DefaultHandler {
    
    protected static final Set<String> INTEGER_TYPES = Set.of(
        "integer", "nonPositiveInteger", "long", "nonNegativeInteger", 
        "negativeInteger", "int", "unsignedLong", "positiveInteger", "short", 
        "unsignedInt", "byte", "unsignedShort","unsignedByte"
    );
    
    protected final NTSchemaModel model;      // schema model for this message format
    protected final JsonObject data;          // add data here
    protected final JsonObject context;       // add context bindings here if unexpected namespace used
    protected final NamespaceBindings nsbind; // modifiable copy of model bindings
    protected final HashSet<String> uriUsed;  // found a component with this namespace in xml data

    protected final StringBuilder content;    // gathers content of simple content elements
    protected final Stack<JsonObject> ostk;   // parent objects of the element being processed
    protected String messageFormatID;         // 
    protected int resultFlags;
    protected final ArrayList<JsonObject> objWithMetadata;    // for second-pass of objects with metadata
    protected final HashMap<String,String> metadataElement;   // maps @id to metadata element compact IRI    
    
    NTXMLHandler (NTSchemaModel m, JsonObject d, JsonObject c) {
        super();
        model = m;
        data = d;
        context = c;
        nsbind = m.namespaceBindings().copyOf();
        uriUsed = new HashSet<>();
        objWithMetadata = new ArrayList<>();
        metadataElement = new HashMap<>();
        content = new StringBuilder(80);
        ostk = new Stack<>();
        ostk.push(data);
        messageFormatID = "";
        resultFlags = 0;
    }
    
    /**
     * Returns the message format identifier for the processed NIEM XML.
     * This is the URI of the message element.
     * @return message format URI
     */
    public String messageFormatID () {
        return messageFormatID;
    }
    
    /**
     * Returns a status code from translating the XML input data.
     * @return 
     */
    public int resultFlags () {
        return resultFlags;
    }
    
    // All of the namespaces in the schema mode are already in the
    // namespace map.  Remember each namespace declaration anyway, for 
    // elements matching a schema wildcard.
    @Override
    public void startPrefixMapping(String prefix, String uri) {
        nsbind.assignPrefix(prefix, uri);
    }
    
    @Override
    public void startElement(String eNamespace, String eLocalName, String eQName, Attributes atts) {
        // Note the message format if this is the document element
        if (ostk.size() == 1) {
            messageFormatID = componentURI(eNamespace, eLocalName);
        }
        // Create new Json object for this element, but don't add to parent object
        String elementIRI = componentURI(eNamespace, eLocalName);
        String nsPrefix   = nsbind.getPrefix(eNamespace);
        String elementKey = nsPrefix + ":" + eLocalName;          
        JsonObject cobj = new JsonObject();
        uriUsed.add(eNamespace);
        ostk.push(cobj);

        // Process element's attributes, add to element's json object
        for (int i = 0; i < atts.getLength(); i++) {
            String aqn = atts.getQName(i);
            String aln = atts.getLocalName(i);
            String auri = atts.getURI(i);
            String av = atts.getValue(i);
            String attributeURI = componentURI(auri, aln);
            
            // xml:base becomes @base keyword
            if ("xml:base".equals(aqn)) {
                cobj.addProperty("@base", av);
            } 
            // handle xml:lang attribute
            else if ("xml:lang".equals(aqn)) {
                // FIXME
            }
            // handle xml:space attribute
            else if ("xml:space".equals(aqn)) {
                // FIXME
            } 
            // Handle attributes from structures namespace
            else if (auri.startsWith(STRUCTURES_NS_URI_PREFIX)) {
                // structures:uri, id, and ref all become @id keyword 
                if ("id".equals(aln) || "ref".equals(aln) || "uri".equals(aln)) {
                    String ref;
                    if ("uri".equals(aln)) {
                        ref = av;       // uri value already is a relative URI
                    }
                    else {
                        ref = "#" + av; // convert id and ref values into relative URI
                    }
                    cobj.addProperty("@id", ref);
                    // Remember compact IRI of metadata elements for cleanup phase
                    if (eLocalName.endsWith("Metadata")) {
                        metadataElement.put(ref, elementKey);
                    }
                }
                // structures:metadata becomes a placeholder for the metadata object reference                
                else if (aqn.endsWith(":metadata")) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("@id", "#" + av);
                    cobj.add(STRUCTURES_NS_URI_PREFIX, obj);
                    objWithMetadata.add(cobj);
                }
            } 
            // ignore xsi: attributes
            else if (W3C_XML_SCHEMA_INSTANCE_NS_URI.equals(auri)) {
                // do nothing for xsi:type, xsi:nil, etc.
            }
            // "ordinary" attribute becomes a name-value pair in the element object
            else {
                String aprfx = nsbind.getPrefix(auri);
                String akey = aprfx + ":" + aln;
                String simpleType = model.attributeType(attributeURI);
                JsonElement jv = genJsonValue(av, simpleType);
                cobj.add(akey, jv);
                uriUsed.add(auri);
            }
        }
        // Element with simple content? Empty the content-capture buffer
        // There will be some "characters" events, then an end-element, and
        // nothing in between, because no mixed content allowed.
        if (model.simpleElementType(elementIRI) != null) {
            content.setLength(0);
        }
    }
        
    @Override
    public void characters (char[] data, int start, int length) {
        content.append(data, start, length);
    }

    public void endElement (String eNamespace, String eLocalName, String eQName) {
        String elementIRI = componentURI(eNamespace, eLocalName);        
        String nsPrefix   = nsbind.getPrefix(eNamespace);
        String elementKey = nsPrefix + ":" + eLocalName;  
        JsonObject cobj = ostk.pop();   // element with eQName
        JsonObject pobj = ostk.peek();  // parent of cobj
        JsonElement jval = null;        // value for pobj.add(key)
        
        // Construct value of element with simple content
        String simpleType = model.simpleElementType(elementIRI);
        if (simpleType != null) {
            String sval = content.toString().trim();
            JsonElement val = genJsonValue(sval, simpleType);
            // Simple content element with attributes needs rdf:value
            if (cobj.size() > 0) {
                cobj.add("rdf:value", val);
                jval = cobj;
            }
            // Simple content with no attributes is a pair in the parent object
            else {
                jval = val;
            }
        }
        // Construct value of element with children
        else {
            jval = cobj;
        }
        // Special handing for complex augmentation elements
        // Add the children of the augmentation element to the parent object
        // Augmentation element itself does not appear in json output
        if (eLocalName.endsWith("Augmentation") && jval.isJsonObject()) {
            cobj.entrySet().forEach((pair) -> {
                addToObject(pobj, pair.getKey(), pair.getValue());
            });
        }
        // Any other element, just add to parent object
        else {
            addToObject(pobj, elementKey, jval);
        }
    }
    
    // Add key-value pair to an object.  1st, 2nd, subsequent additions of the
    // same key are handled differently
    protected static void addToObject(JsonObject pobj, String elementKey, JsonElement jval) {

        // If it's the 1st time to add this key, just add the new name-value pair.
        if (!pobj.has(elementKey)) {
           pobj.add(elementKey, jval);
        }
        else {
            // 2nd time addking key, replace cval with an array [cval,jval]
            JsonElement cval = pobj.get(elementKey);
            if (!cval.isJsonArray()) {
                pobj.remove(elementKey);
                JsonArray cva = new JsonArray();
                cva.add(cval);
                cva.add(jval);
                pobj.add(elementKey,cva);
            }
            // 3rd or later time for this key, append to the cval array
            else {
                JsonArray cva = (JsonArray)cval;
                cva.add(jval);
            }                     
        }
    }

    @Override
    public void endDocument () {
        
        // Replace "structures:metadata" keys with compact IRI of the metadata element
        for (JsonObject obj : objWithMetadata) {
            JsonObject phold = obj.getAsJsonObject(STRUCTURES_NS_URI_PREFIX);           
            String id = phold.get("@id").getAsString();
            String mdkey = metadataElement.get(id);
            
            obj.remove(STRUCTURES_NS_URI_PREFIX);
            obj.add(mdkey, phold);            
        }
        
        // Make a copy of the model context object.
        // If the input XML uses any unanticipated namespace (in an attribute or
        // element matching a wildcard), add the (prefix,ns) pair to the
        // "context" object and set the X2J_EXTENDED status flag.
        model.namespaceBindings().contextObj().entrySet().forEach((pair) -> {
            context.add(pair.getKey(), pair.getValue());
        });
        for (String ns : uriUsed) {
            String prefix = model.namespaceBindings().getPrefix(ns);
            if (prefix == null) {
                prefix = nsbind.getPrefix(ns);
                context.addProperty(prefix, ns);
                resultFlags = (short) (resultFlags | X2J_EXTENDED);
            }
        }
    }
    
    // Creates the right kind of JsonElement based on base type
    protected static JsonElement genJsonValue(String val, String simpleType) {
        if (simpleType == null) {
            // Component not declared in schema; assume a string value.
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
        else if (INTEGER_TYPES.contains(simpleType)) {
            return new JsonPrimitive(new BigInteger(val));
        }
        return new JsonPrimitive(val);
    }
    
    static String componentURI (String namespace, String localName) {
        if (namespace.endsWith("#")) {
            return namespace + localName;
        }
        else {
            return namespace + "#" + localName;
        }
    }
}
