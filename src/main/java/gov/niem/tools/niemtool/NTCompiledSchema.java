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
import java.util.ArrayList;
import org.apache.xerces.xs.XSModelGroup;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSParticle;
import org.apache.xerces.xs.XSTerm;

/**
 * A class for producing a NTSchemaModel object from an XML schema, in order
 * to drive translation between NIEM XML, NIEM JSON, and NIEM RDF. 
 * 
 * @author Scott Renner <sar@mitre.org>
 */
public class NTCompiledSchema extends NTSchema {
        
    private NTSchemaModel ntmodel = null;
    private XSNamespaceInfo nsInfo = null;
    
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
        NamespaceBindings nsbind = new NamespaceBindings();
        ntmodel =  new NTSchemaModel();   
        nsInfo  = new XSNamespaceInfo(xs);
    
        // Assign unique prefix for each namespace in schema.
        // Start with declarations in the extension schemas, on the assumption
        // that the message designer knew what he was doing.
        // Follow with declarations in NIEM model schemas, to get the usual
        // namespace prefixes (unless the designer chose something else).
        // Declarations in external schemas come last.
        // rdf: prefix always means RDF, no matter what crazy-ass thing
        // may be in the extension or external schemas.
        nsbind.assignPrefix(RDF_NS_URI, "rdf");
        nsInfo.nsList().forEach((ns) -> {
            nsInfo.nsDecls().get(ns).forEach((uri,prefix) -> {
                nsbind.assignPrefix(uri, prefix);
            });
        });
        nsbind.getDecls().forEach((uri,prefix) -> {
            ntmodel.addNamespacePrefix(uri, prefix);
        });
        // Find external namespaces
        nsInfo.nsNDRversion().forEach((ns, ver) -> {
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
        // Find wildcards
        map = xs.getComponents(XSTypeDefinition.COMPLEX_TYPE);
        for (int i = 0; i < map.getLength(); i++) {
            XSComplexTypeDefinition ctype = (XSComplexTypeDefinition)map.item(i);
            if (ctype.getAttributeWildcard() != null) {
                ntmodel.setHasWildcard(true);
            }
            else if (ctype.getContentType() == XSComplexTypeDefinition.CONTENTTYPE_ELEMENT) {
                XSParticle part = ctype.getParticle();
                ArrayList<XSTerm> terms = new ArrayList<>();
                collectTerms(terms, part);
                for (XSTerm t : terms) {
                    if (t.getType() == XSConstants.WILDCARD) {
                        ntmodel.setHasWildcard(true);
                    }
                }
            }
        }
        return ntmodel;
    }
    
    private void collectTerms(List<XSTerm> terms, XSParticle p) {
        XSTerm t = p.getTerm();
        if (t.getType() != XSConstants.MODEL_GROUP) {
            terms.add(t);
        }
        else {
            XSModelGroup mg = (XSModelGroup) t;
            XSObjectList pl = mg.getParticles();
            for (int i = 0; i < pl.getLength(); i++) {
                XSParticle np = (XSParticle)pl.item(i);
                collectTerms(terms, np);
            }
        }
    }
    
    private static String genTypeString (XSSimpleTypeDefinition stype) {
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

