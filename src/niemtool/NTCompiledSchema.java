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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.xerces.xs.XSAnnotation;
import org.apache.xerces.xs.XSAttributeDeclaration;
import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSConstants;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSNamedMap;
import org.apache.xerces.xs.XSNamespaceItem;
import org.apache.xerces.xs.XSNamespaceItemList;
import org.apache.xerces.xs.XSObject;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSSimpleTypeDefinition;
import org.apache.xerces.xs.XSTypeDefinition;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import static niemtool.NTConstants.*;

/**
 *
 * @author Scott Renner <sar@mitre.org>
 */
public class NTCompiledSchema extends NTSchema {
     
    private NTSchemaModel ntmodel = null;
    private final HashMap<String,List<MapRec>> nsPrefix = new HashMap<>();
    private final HashMap<String,List<MapRec>> nsURI    = new HashMap<>();
    private final StringBuilder msgs = new StringBuilder();
    
    public NTCompiledSchema (List<String> catalogs, List<String>schemaOrNSs) throws ParserConfigurationException {
        super(catalogs,schemaOrNSs);
    }
    
    public String compilationMessages () {
        return msgs.toString();
    }
    
    public NTSchemaModel ntmodel () {
        if (ntmodel != null) {
            return ntmodel;
        }
        XSModel xs = xsmodel();
        if (xs == null) {
            return null;
        }        
        ntmodel =  new NTSchemaModel();        
        
        // Each namespace in the schema model has a synthetic annotation contatining
        // the namespace declarations and attributes from the xs:schema element
        // Process it to construct the prefix and namespace mappings, and the NDR version
        XSNamespaceItemList nsil = xs.getNamespaceItems();
        for (int i = 0; i < nsil.getLength(); i++) {
            XSNamespaceItem nsi = nsil.item(i);
            String ns = nsi.getSchemaNamespace();
            if (!W3C_XML_SCHEMA_NS_URI.equals(ns) && !ns.startsWith(STRUCTURES_NS_URI_PREFIX)) {
                XSObjectList annl = nsi.getAnnotations();
                for (int j = 0; j < annl.getLength(); j++) {
                    XSAnnotation an = (XSAnnotation)annl.get(j);
                    String as = an.getAnnotationString();
                    processAnnotation(ns, as);
                }
            }
        }
        // Now iterate through the prefix mappings to generate a context
        nsPrefix.forEach((prefix,value) -> {
           boolean same = true;
           String first = value.get(0).val;
           for (int i = 1; same && i < value.size(); i++) {
               same = (first.equals(value.get(i).val));
           }            
           if (same)  {
               ntmodel.addContext(prefix, value.get(0).val);
           }
           else {
               msgs.append(String.format("[warn] multiple namespaces mapped to prefix \"%s\"\n", prefix));
               value.forEach((mr) -> {
                   msgs.append(String.format("  mapped to %s in namespace %s\n", mr.val, mr.ns));
               });
           }
        });
        // Now iterate through the namespace mappings
        nsURI.forEach((uri,value) -> {
           boolean same = true;
           String first = value.get(0).val;
           for (int i = 1; same && i < value.size(); i++) {
               same = (first.equals(value.get(i).val));
           }
           if (same) {
               ntmodel.addNamespacePrefix(uri, value.get(0).val);
           }
           else {
               msgs.append(String.format("[warn] multiple prefixes mapped to namespace %s\n", uri));
               value.forEach((mr) -> {
                   msgs.append(String.format("  mapped to prefix \"%s\" in namespace %s\n", mr.val, mr.ns));
               });
           }
        });      
        // Now get the simple types for simple elements
        XSNamedMap map = xs.getComponents(XSConstants.ELEMENT_DECLARATION);       
        for (int i = 0; i < map.getLength(); i++) {
            XSElementDeclaration item = (XSElementDeclaration) map.item(i);
            XSTypeDefinition type = item.getTypeDefinition();
            XSSimpleTypeDefinition stype;
            if (type.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) {
                XSComplexTypeDefinition ctype = (XSComplexTypeDefinition) type;
                stype = ctype.getSimpleType();
            }
            else {
                stype = (XSSimpleTypeDefinition) type;
            }
            if (stype != null) {
                String ts = genTypeString(stype);
                ntmodel.addSimpleElement(buildURI(item), ts);
            }
        }     
        // Now get the simple types for attributes        
        map = xs.getComponents(XSConstants.ATTRIBUTE_DECLARATION);
        for (int i = 0; i < map.getLength(); i++) {
            XSAttributeDeclaration item = (XSAttributeDeclaration) map.item(i);
            XSSimpleTypeDefinition stype = item.getTypeDefinition();
            String ts = genTypeString(stype);
            ntmodel.addAttribute(buildURI(item), ts);
        }
        return ntmodel;
    }
    
    private String genTypeString (XSSimpleTypeDefinition stype) {
        String result = "";
        if (stype == null) {
            int k = 0;
        }
        if (stype.getItemType() != null) {
            result = "list/";
            stype = stype.getItemType();
        }
        while (!W3C_XML_SCHEMA_NS_URI.equals(stype.getNamespace())
                && null != stype.getBaseType()) {
            stype = (XSSimpleTypeDefinition) stype.getBaseType();
        }
        result = result + stype.getName();
        return result;
    }
    

    /**
     * Parse the synthetic xs:annotation from a schema namespace item.
     * Adds the prefix mappings and namespace URI mappings for this namespace.
     * Determines the NDR version of this namespace.
     */
    private void processAnnotation (String namespace, String annotation) {
        Handler h = new Handler(this, namespace);
        InputSource is = new InputSource(new StringReader(annotation));
        try {
            saxp.parse(is, h);
        } catch (Exception ex) {
            // IGNORE
        }
        ntmodel.addNamespaceVersion(namespace, h.ndrVersion);
    }
   
    private class Handler extends DefaultHandler {
        private final NTCompiledSchema obj;    
        private final String namespace;
        String ndrVersion = "";
        Handler(NTCompiledSchema obj, String namespace) {
            super();
            this.obj = obj;
            this.namespace = namespace;
        }
        @Override
        public void startPrefixMapping(String prefix, String uri) {
            if (!"".equals(prefix) 
                    && !W3C_XML_SCHEMA_NS_URI.equals(uri)
                    && !uri.startsWith(APPINFO_NS_URI_PREFIX)
                    && !uri.startsWith(CONFORMANCE_TARGET_NS_URI_PREFIX)
                    && !uri.startsWith(W3C_XML_SCHEMA_INSTANCE_NS_URI)
                    && !uri.startsWith(NIEM_XS_PREFIX)) {
                // Record the declared namespace uri in the prefix map
                List<MapRec> mlist = obj.nsPrefix.get(prefix);
                if (mlist == null) {
                    mlist = new ArrayList<>();
                    obj.nsPrefix.put(prefix, mlist);
                }
                MapRec nr = new MapRec(namespace, uri);
                mlist.add(nr);
                // Record the declared prefix in the namespace uri map
                mlist = obj.nsURI.get(uri);
                if (mlist == null) {
                    mlist = new ArrayList<>();
                    obj.nsURI.put(uri, mlist);
                }
                nr = new MapRec(namespace, prefix);
                mlist.add(nr);
            }
        }
        @Override
        public void startElement (String ens, String ename, String raw, Attributes atts) {
            if ("".equals(ndrVersion)) {
                for (int i = 0; i < atts.getLength(); i++) {
                    String auri = atts.getURI(i);
                    String av = atts.getValue(i);
                    String aln = atts.getLocalName(i);
                    if (auri.startsWith(CONFORMANCE_TARGET_NS_URI_PREFIX) && CONFORMANCE_ATTRIBUTE_NAME.equals(aln)) {
                        av = av.substring(NDR_NS_URI_PREFIX.length());
                        int sp = av.indexOf('/');
                        if (sp >= 0) {
                            av = av.substring(0, sp);
                        }
                        ndrVersion = av;
                        return;
                    }
                }
            }
        }
    }
    
    
    private static String buildURI (XSObject item) {
        if (item.getNamespace().endsWith("#")) {
            return item.getNamespace()+item.getName();
        }
        else {
            return item.getNamespace()+"#"+item.getName();
        }
    }
    
    
    private class MapRec {
        String ns = null;
        String val = null;
        MapRec(String ns, String val) {
            this.ns = ns;
            this.val = val;
        }
        @Override
        public String toString () {
            return String.format("[%s,%s]", val, ns);
        }
    }
}
