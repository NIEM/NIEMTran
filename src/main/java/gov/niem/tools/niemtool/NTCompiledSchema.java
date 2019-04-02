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

import java.util.HashSet;
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
        
        // Generate unique prefix mapping for each namespace declared in the schema
        // Prefer standard prefix for NIEM model namespace
        // Reserve rdf prefix for RDF
        HashSet<String> assigned = new HashSet<>();
        assigned.add("rdf");
        ntmodel.addNamespacePrefix(RDF_NS_URI, "rdf");
        nsURIMaps().forEach((ns, value) -> {
            if (RDF_NS_URI.equals(ns)) { 
                return;     // skip to next ns,value pair 
            }
            String stdPrefix = ContextMapping.commonPrefix(ns);
            String firstPrefix = value.get(0).val;
            boolean isStd = false;
            boolean same = true;
            // Examine every prefix mapped to ns; All the same? Any the std?
            for (int i = 0; i < value.size(); i++) {
                String p = value.get(i).val;
                isStd = isStd || stdPrefix.equals(p);
                same = same && firstPrefix.equals(p);
            }
            // If namespace always mapped to one prefix, try that prefix
            // Otherwise, if namespace ever mapped to standard prefix, try that
            // Otherwise, try the first prefix
            String usePrefix = firstPrefix;
            if (!same && isStd){
                usePrefix = stdPrefix;
            }
            // If the first-choice prefix is already assigned, try all the others
            if (assigned.contains(usePrefix)) {
                for (int i = 0; i < value.size(); i++) {
                    String p = value.get(i).val;
                    if (!assigned.contains(p)) {
                        usePrefix = p;
                        break;
                    }
                }
            }
            // If every mapped prefix is assigned, mung until successful
            if (assigned.contains(usePrefix)) {
                String base = usePrefix;
                int ct = 1;
                do {
                    usePrefix = String.format("%s_%d", base, ct++);
                } while (assigned.contains(usePrefix));
            }
            // Record the prefix mapping
            ntmodel.addNamespacePrefix(ns, usePrefix);
            assigned.add(usePrefix);
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

