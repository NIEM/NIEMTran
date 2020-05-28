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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import static gov.niem.tools.niemtran.NTConstants.STRUCTURES_NS_URI_PREFIX;
import static gov.niem.tools.niemtran.Translator.X2J_OK;
import static gov.niem.tools.niemtran.Translator.X2J_OMITTED;
import java.math.BigInteger;
import java.util.Set;
import java.util.Stack;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A SAX handler class for converting NIEM XML to NIEM JSON.  It is a helper
 * class for Translator.
 * 
 * What to do with XML components from external namespaces? This is controlled
 * on a per-namespace basis by the externalNShandler map in the schema model.
 * Possible values are "", "*", and the name of a Java class
 *   ""  = omit this data (and set X2J_OMITTED)
 *   "*" = treat data as NIEM conforming
 *   [name] = use this class to generate JSON for this component (not implemented)
 * 
 * What to do with wildcard XML components from namespaces that are not defined by
 * the schema document set? This is controlled by argument to the class
 * constructor. The choices are:
 *   * omit this data (and set X2J_OMITTED)
 *   * treat as NIEM conforming, and extend the schema model context
 * 
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class NTXMLHandler extends DefaultHandler {
    
    protected static final Set<String> INTEGER_TYPES = Set.of(
        "integer", "nonPositiveInteger", "long", "nonNegativeInteger", 
        "negativeInteger", "int", "unsignedLong", "positiveInteger", "short", 
        "unsignedInt", "byte", "unsignedShort","unsignedByte"
    );
    
    protected final NTSchemaModel model;            // schema model for this message format
    protected final JsonObject data;                // put converted data here
    protected final JsonObject econtext;            // put extended econtext here if unexpected namespace encountered
    protected final NamespacePrefixMap pmap_orig;   // read-only econtext mapping from schema model
    protected final StringBuilder content;          // gathers content of simple content elements
    protected final Stack<JsonObject> ostk;         // parent objects of the element being processed
    protected NamespacePrefixMap pmap_mod;          // modified copy of econtext mapping (lazy init)  
    protected String messageFormatID;               // namespace of the document element
    protected int resultFlags;  
    
    /**
     * @param m -- schema model for this document; not modified by handler
     * @param d -- an empty JsonObject; handler adds json data to it
     * @param c -- an empty JsonObject, or null.
     *       if null, components from unknown namespaces are omitted (and X2J_OMITTED set)
     *       if not null, treat as NIEM-conforming, add context pairs to this object
     */
    NTXMLHandler (NTSchemaModel m, JsonObject d, JsonObject c) {
        super();
        model   = m;
        data    = d;
        econtext = c;
        pmap_orig  = new NamespacePrefixMap(m.prefixMap());
        pmap_mod   = null;
        content = new StringBuilder(80);
        ostk    = new Stack<>();
        ostk.push(data);
        messageFormatID = "";
        resultFlags = X2J_OK;
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
     * Returns the status flags from translating the XML input data.
     * X2J_OMITTED is set iff some input XML was not translated
     * X2J_EXTENDED is set iff this.oontext is not empty
     * @return translation status flags
     */
    public int resultFlags () {
        return resultFlags;
    }
    
    
    @Override
    public void startElement(String eNamespace, String eLocalName, String eQName, Attributes atts) {
        // Note the message format if this is the document element
        if (ostk.size() == 1) {
            messageFormatID = componentURI(eNamespace, eLocalName);
        }
        // Create new Json object for this element, but don't add to parent object
        String elementIRI = componentURI(eNamespace, eLocalName);       
        JsonObject cobj = new JsonObject();
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
                // FIXME: more complicated than cobj.addProperty("@base", av);
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
                av = av.strip();
                
                // structures:uri, id, and ref all become @id keyword 
                if ("id".equals(aln) || "ref".equals(aln) || "uri".equals(aln)) {
                    String ref;
                    if ("uri".equals(aln)) {
                        ref = av;       // uri value already is a relative URI
                    }
                    else {
                        ref = idref(av); // convert ID and IDREF values into relative URI
                    }
                    cobj.addProperty("@id", ref);
                }            
                // structures:metadata becomes a name-value pair in the element object
                else if (aqn.endsWith(":metadata")) {
                    
                    // is the attribute value a single IDREF or an IDREF list?
                    JsonElement jv = null;
                    if (av.matches("\\S+\\s")) {
                        JsonArray jva = new JsonArray();
                        String[] vals = av.split("\\s+");
                        for (String v : vals) {
                            JsonObject jo = new JsonObject();
                            jo.addProperty("@id", idref(v));
                            jva.add(jo);
                        }
                        jv = jva;                        
                    }
                    else {
                        JsonObject jo = new JsonObject();
                        jo.addProperty("@id", idref(av));
                        jv = jo;
                    }
                    String aprfx = "structures";
                    String akey = aprfx + ":" + "metadata";
                    cobj.add(akey, jv);
                }
                else if (aqn.endsWith("relationshipMetadata")) {
                    // FIXME
                }
            } 
            // done with attributes from structures namespace
            // ignore xsi: attributes
            else if (W3C_XML_SCHEMA_INSTANCE_NS_URI.equals(auri)) {
                // do nothing for xsi:type, xsi:nil, etc.
            }
            // "ordinary" attribute becomes a name-value pair in the element object.
            // wildcard attributes may be omitted; namespacePrefix() handles that.
            // attributes of list type are handled in genJsonValue.
            else {
                String aprfx = namespacePrefix(auri, aqn);
                if (aprfx != null) {
                    String akey = aprfx + ":" + aln;
                    String simpleType = model.attributeType(attributeURI);
                    JsonElement jv = genJsonValue(av, simpleType);
                    cobj.add(akey, jv);
                }
            }
        }
        // Done processing attributes.  Nothing more to do for complex content.
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

    @Override
    public void endElement (String eNamespace, String eLocalName, String eQName) {

        // Is this an unknown wildcard element?  namespacePrefix() handles
        // those, and returns null if we are omitting their data.
        String nsPrefix   = namespacePrefix(eNamespace, eQName);
        if (nsPrefix == null) return;
        
        String elementIRI = componentURI(eNamespace, eLocalName);        
        String elementKey = nsPrefix + ":" + eLocalName;  
        JsonObject cobj = ostk.pop();   // element with eQName
        JsonObject pobj = ostk.peek();  // parent of cobj
        JsonElement jval;               // value for pobj.add(key)
        
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

        // 1st time adding this key, just add the new name-value pair.
        if (!pobj.has(elementKey)) {
           pobj.add(elementKey, jval);
        }
        else {
            // 2nd time adding key, replace cval with an array [cval,jval]
            JsonElement cval = pobj.get(elementKey);
            if (!cval.isJsonArray()) {
                pobj.remove(elementKey);
                JsonArray cva = new JsonArray();
                cva.add(cval);
                cva.add(jval);
                pobj.add(elementKey,cva);
            }
            // 3rd or later time for this key, append jval to the cval array
            else {
                JsonArray cva = (JsonArray)cval;
                cva.add(jval);
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
            return new JsonPrimitive(Boolean.valueOf(val.trim()));
        } 
        // else if ("decimal".equals(simpleType)) {
        //    return new JsonPrimitive(new BigDecimal(val));
        //} 
        else if ("double".equals(simpleType)) {
            return new JsonPrimitive(Double.valueOf(val));
        } 
        else if ("float".equals(simpleType)) {
            return new JsonPrimitive(Float.valueOf(val));
        } 
        else if (INTEGER_TYPES.contains(simpleType)) {
            return new JsonPrimitive(new BigInteger(val.trim()));
        }
        return new JsonPrimitive(val);
    }
    
    // Get the namespace prefix for a namespace URI.
    // Special handling for wildcard elements and attributes; their namespace
    // may not be in the schema model context.
    // Returns null if wildcard components should be omitted.
    // Returns a possibly-munged prefix if wildcard components should be
    // treated as NIEM conforming, and the namespace added to the
    // schema model context
    private String namespacePrefix (String nsURI, String cQName) {
        
        // Return namespace prefix from schema model context if it's there
        String prefix = pmap_orig.getPrefix(nsURI);
        if (prefix != null) return prefix;
        
        // Return namespace prefix from extended context if it's there
        if (pmap_mod != null) {
            prefix = pmap_mod.getPrefix(nsURI);
            if (prefix != null) return prefix;
        }
        // Not there? This must be a wildcard element or attribute.
        // Are we omitting those?
        if (econtext == null) {
            resultFlags |= X2J_OMITTED;
            return null;
        }
        // Get a candidate prefix from the component QName
        prefix = "noprefix";
        int cx = cQName.indexOf(":");
        if (cx >= 0) prefix = cQName.substring(0,cx);
        
        // Now add the candidate prefix to the econtext mapping, possibly
        // munging the candidate if it's already assigned.
        if (pmap_mod == null) pmap_mod = new NamespacePrefixMap(pmap_orig);
        pmap_mod.assignPrefix(prefix, nsURI);
        prefix = pmap_mod.getPrefix(nsURI);
        econtext.addProperty(prefix, nsURI);
        return prefix;
    }
    
    // every IDREF becomes a URI relative to the document base 
    static String idref (String s) {
        if (s.startsWith("#")) {
            return s;
        }
        else {
            return "#" + s;
        }
    }
    
    // returns the component URI by composing namespace and local name
    static String componentURI (String namespace, String localName) {
        if (namespace.endsWith("#")) {
            return namespace + localName;
        }
        else {
            return namespace + "#" + localName;
        }
    }
}
