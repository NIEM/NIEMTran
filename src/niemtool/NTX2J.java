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
package niemtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.FileReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A class for translating NIEM XML to NIEM JSON
 * @author Scott Renner <sar@mitre.org>
 */
public class NTX2J {

    private static final String RDF_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String XSI_URI = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String XSD_URI = "http://www.w3.org/2001/XMLSchema";
    private static final String STRUCTURES_URI_PREFIX = "http://release.niem.gov/niem/structures/";
    
    private static final String BOOLEAN_TYPE_URI = XSD_URI + "#boolean";
    private static final String DECIMAL_TYPE_URI  = XSD_URI + "#decimal";
    private static final String DOUBLE_TYPE_URI  = XSD_URI + "#double";
    private static final String FLOAT_TYPE_URI   = XSD_URI + "#float";

    private static final HashSet<String> integerTypes = new HashSet();
    private static final String[] integerTypeNames = new String[] {
        "integer", "nonPositiveInteger", "long", "nonNegativeInteger", 
        "negativeInteger", "int", "unsignedLong", "positiveInteger", "short", 
        "unsignedInt", "byte", "unsignedShort","unsignedByte"
    };
    static {
        for (String s : integerTypeNames) {
            integerTypes.add(XSD_URI + "#" + s);
        }
    }    
    
    private final NTSchemaModel model;
    private String xsiPrefix = "xsi:";
    private String structuresPrefix = "structures:";
    private boolean needRDF = false;
    
    NTX2J (NTSchemaModel m) {
        model = m;
    }
    
    NTX2J (FileReader fr) throws FormatException {
        model = new NTSchemaModel(fr);
    }
    
    NTX2J (String s) throws FormatException {
        model = new NTSchemaModel(s);
    }
    
    public String xml2jsonString (Document doc) {
        return xml2jsonObject(doc).toString();
    }
    
    public JsonObject xml2jsonObject (Document doc) {
        needRDF  = false;
        Element root = doc.getDocumentElement();
        String rootName = root.getNodeName();
        JsonObject jdoc = new JsonObject();     
        doElement(root,jdoc,null);
        if (needRDF) {
            JsonObject val = jdoc.getAsJsonObject(rootName);
            JsonObject cxt = val.getAsJsonObject("@context");
            if (cxt.get("rdf") == null) {
                cxt.addProperty("rdf:", RDF_URI);
            }
        }
        return jdoc;
    }

    /**
     * Converts XML element to a JSON pair, which is added to the parent JsonObject.
     * Key of the added pair is the element URI.
     * Value of the added pair could be an object or a scalar.
     * Also adds as needed to @context pair in parent JsonObject.
     * @param e XML element
     * @param p parent JsonObject
     * @param a ??
     */
    private void doElement(Element e, JsonObject p, JsonArray a) {
        doContext(e,p);
        if (hasChildElement(e)) {
            //doComplexElement(e,p,a);
        }
        else {
            doSimpleElement(e,p,a);
        }
    }
    
    private void doContext(Element e, JsonObject p) {
        JsonObject oc = p.getAsJsonObject("@context");
        JsonObject nc = genContext(e);
        if (nc != null) {
            if (oc == null) {
                oc = new JsonObject();
                p.add("@context", oc);
            }
            for (String nk : nc.keySet()) {
                JsonPrimitive nv = nc.getAsJsonPrimitive(nk);
                JsonPrimitive ov = oc.getAsJsonPrimitive(nk);
                if (ov == null) {
                    oc.add(nk,nv);
                }
                else if (!nv.equals(ov)) {
                    System.out.println("Can't redefine "+nk+" as "+nv);    
                    System.exit(1);
                }
            }
        }
    }
    
    /**
     * Generates a JSON-LD @context object from an XML element.
     * Ordinary namespace declarations produce a pair in the context object.
     * The schema instance namespace declaration (xsi:) is ignored, as is the
     * declaration of any version of the NIEM structures namespace.
     * The xml:lang attribute produces a pair with key = @language.
     * @param e XML element information item
     * @return JSON-LD context object
     */
    public JsonObject genContext(Element e) {
        JsonObject cxt = null;
        NamedNodeMap atts = e.getAttributes();
        for (int i = 0; i < atts.getLength(); i++) {
            Attr a = (Attr) atts.item(i);
            String ann = a.getNodeName();
            if (ann.startsWith("xmlns:") || ann.equals("xml:lang")) {
                String key = ann.substring(6);
                String av = a.getValue();
                if ("rdf".equals(key) && !RDF_URI.equals(av)) {
                    System.out.println("Error: namespace prefix 'rdf' not defined as " + RDF_URI);
                    System.exit(1);
                }
                if (ann.equals("xml:lang")) {
                    key = "@language";
                } else if (av.equals(XSI_URI)) {
                    xsiPrefix = key;
                    key = null;
                } else if (av.startsWith(STRUCTURES_URI_PREFIX)) {
                    structuresPrefix = key;
                    key = null;
                } else if (!av.endsWith("#")) {
                    av = av + "#";
                }
                if (key != null) {
                    if (cxt == null) {
                        cxt = new JsonObject();
                    }
                    cxt.addProperty(key, av);
                }
            }
        }
        return cxt;
    }
   
    private void doSimpleElement(Element e, JsonObject p, JsonArray a) {
        String en         = e.getNodeName();
        String euri       = buildURI(e.getNamespaceURI(),e.getLocalName());
        String etype      = model.simpleElementType(euri);
        JsonPrimitive ev  = simpleContent(e,etype);
        JsonObject atts   = genAttributes(e);
        JsonElement addThis;
        
        if (atts == null) {
            addThis = ev;
        }
        else {
            atts.add("rdf:value",ev);
            addThis = atts;
        }
        if (a == null) {
            p.add(en,addThis);
        }
        else {
            a.add(addThis);
        }
    }
    
    private JsonPrimitive simpleContent(Element e, String typeURI) {
        String val = normalizeString(e.getTextContent());
        return simpleContent(val,typeURI);
    }
    
    private JsonPrimitive simpleContent(Attr a, String typeURI) {
        String val = a.getValue();
        return simpleContent(val,typeURI);
    }
    
    private JsonPrimitive simpleContent(String val, String typeURI) {
        if (typeURI == null) {
            return new JsonPrimitive(val);
        }
        else if (typeURI.equals(BOOLEAN_TYPE_URI)) {
            return new JsonPrimitive(Boolean.valueOf(val));
        }
        else if (typeURI.equals(DECIMAL_TYPE_URI)) {
            return new JsonPrimitive(new BigDecimal(val));
        }
        else if (typeURI.equals(DOUBLE_TYPE_URI)) {
            return new JsonPrimitive(new Double(val));
        }
        else if (typeURI.equals(FLOAT_TYPE_URI)) {
            return new JsonPrimitive(new Float(val));
        }
        else if (integerTypes.contains(typeURI)) {
            return new JsonPrimitive(new BigInteger(val));
        }
        else {
            return new JsonPrimitive(val);
        }
    }
    
    public JsonObject genAttributes(Element e) {
        JsonObject aobj = new JsonObject();
        NamedNodeMap atts = e.getAttributes();
        for (int i = 0; i < atts.getLength(); i++) {
            Attr a = (Attr)atts.item(i);
            String ns  = a.getNamespaceURI();
            String an  = a.getName();
            String aln = a.getLocalName();
            String uri = buildURI(ns,aln);
            String val = a.getValue();
            String type = model.attributeType(uri);
            if (a.getNodeName().startsWith("xmlns:")) {
                // do nothing
            }
            else if (an.equals("xml:base")) {
                aobj.addProperty("@base",val);
            }
            else if (ns.equals(XSI_URI) && aln.equals("nil")) {
                // do nothing
            }
            else if (ns.startsWith(STRUCTURES_URI_PREFIX)) {
                if (an.endsWith(":id") || an.endsWith(":ref") || an.endsWith(":uri")) {
                    aobj.addProperty("@id",val);
                }
            }
            else {
                JsonElement rv = simpleContent(a,type);
                aobj.add(an,rv);
            }
        }
        if (aobj.size() < 1) {
            return null;
        }
        return aobj;
    }    
    
    public String normalizeString (String s) {
        return s;
    }

    public static boolean hasChildElement (Element e) {
        NodeList nl = e.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }

    public static String buildURI (String ns, String name) {
        if (ns.endsWith("#")) {
            return ns + name;
        }
        else {
            return ns +"#" + name;
        }
    }    
}
