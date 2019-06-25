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

import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.xerces.xs.XSAttributeDeclaration;
import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSConstants;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSNamedMap;
import org.apache.xerces.xs.XSObject;
import org.apache.xerces.xs.XSSimpleTypeDefinition;
import org.apache.xerces.xs.XSTypeDefinition;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import static gov.niem.tools.niemtool.NTConstants.*;
import java.io.IOException;

/**
 *
 * @author Scott Renner <sar@mitre.org>
 */
public class NTCompiledSchema extends NTSchema {
        
    private NTSchemaModel ntmodel = null;
    
    public NTCompiledSchema () throws ParserConfigurationException, IOException {
        super();
    }
    
    public NTCompiledSchema (List<String> catalogs, List<String>schemaOrNSs) 
            throws ParserConfigurationException, IOException {
        super(catalogs,schemaOrNSs);
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
               
        // Now determine a unique prefix for each namespace in schema
        // using the namespace ordering for priorty in case of prefix conflict.
        NamespaceMap nsmap = new NamespaceMap();
        nsmap.assignPrefix(RDF_NS_URI, "rdf"); 
        nsList().forEach((namespace) -> {
            List<MapRec> maps = nsDecls().get(namespace);
            maps.forEach((rec) -> { 
                nsmap.assignPrefix(rec.ns, rec.val);
            });
        });
        nsmap.nsmap().forEach((ns, prefix) -> {
            ntmodel.addNamespacePrefix(ns, prefix);
        });

        // Find external namespaces
        nsNDRversion().forEach((ns, ver) -> {
            if ("".equals(ver)){
                ntmodel.addExternalNS(ns);
            }
        });
        // Get the simple types for simple elements
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
        // Get the simple types for attributes        
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
        
    private static String buildURI (XSObject item) {
        if (item.getNamespace().endsWith("#")) {
            return item.getNamespace()+item.getName();
        }
        else {
            return item.getNamespace()+"#"+item.getName();
        }
    }

}

