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

import java.io.StringReader;
import java.util.ArrayList;
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
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import static gov.niem.tools.niemtool.NTConstants.*;

/**
 *
 * @author Scott Renner <sar@mitre.org>
 */
public class NTCompiledSchema extends NTSchema {
        
    private NTSchemaModel ntmodel = null;
    
    public NTCompiledSchema (List<String> catalogs, List<String>schemaOrNSs) throws ParserConfigurationException {
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
        
        // Iterate through the prefix mappings to generate a context
        processNamespaceItems();
        nsPrefix.forEach((prefix,value) -> {
           boolean same = true;
           String first = value.get(0).val;
           for (int i = 1; same && i < value.size(); i++) {
               same = (first.equals(value.get(i).val));
           }            
           if (same)  {
               ntmodel.addContext(prefix, value.get(0).val);
           }
        });
        // Iterate through the namespace mappings
        nsURI.forEach((ns, value) -> {
           boolean same = true;
           String first = value.get(0).val;
           for (int i = 1; same && i < value.size(); i++) {
               same = (first.equals(value.get(i).val));
           }
           if (same) {
               ntmodel.addNamespacePrefix(ns, first);
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

