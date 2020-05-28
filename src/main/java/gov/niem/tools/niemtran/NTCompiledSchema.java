/* 
 * NOTICE
 *
 * This software was produced for the U. S. Government
 * under Basic Contract No. W56KGU-18-D-0004, and is
 * subject to the Rights in Noncommercial Computer Software
 * and Noncommercial Computer Software Documentation
 * Clause 252.227-7014 (FEB 2012)
 * 
 * Copyright 2020 The MITRE Corporation.
 */

package gov.niem.tools.niemtran;

import static gov.niem.tools.niemtran.NTConstants.APPINFO_NS_URI_PREFIX;
import static gov.niem.tools.niemtran.NTConstants.STRUCTURES_NS_URI_PREFIX;
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
import java.io.IOException;
import java.util.ArrayList;
import org.apache.xerces.xs.XSModelGroup;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSParticle;
import org.apache.xerces.xs.XSTerm;

/**
 * A class for producing a NTSchemaModel object from an XML schema.
 * The NTSchemaModel object holds the information needed to drive translation 
 * between NIEM XML, NIEM JSON, and NIEM RDF. At present only the XML->JSON
 * translation is implemented.
 * 
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class NTCompiledSchema extends NTCheckedSchema {
        
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
        
        // Set the preferred prefix for every schema document's namespace
        // The NamespaceDecls object has all of the namespace declarations in
        // the schema document pile, in priority order. But for the context
        // mappings, we only want to map the target namespaces from the assembled 
        // schema documents. The NTModel object knows how to handle the case
        // where a single prefix string is mapped to more than one target namespace URI.
        NamespaceDecls ndl = namespaceDeclarations();
        List<NamespaceDecls.NSDeclRec> decls = ndl.nsDecls();
        decls.forEach((d) -> {
            if (namespaces().contains(d.uri)) {
                ntmodel.assignPrefix(d.prefix, d.uri);
            }
        });
        
        // Get the external namespaces, set their default handler
        namespaces().forEach((nsURI) -> {
            if (!nsURI.startsWith(APPINFO_NS_URI_PREFIX) &&
                !nsURI.startsWith(STRUCTURES_NS_URI_PREFIX) &&
                "".equals(namespaceNIEMVersion(nsURI))) {
                ntmodel.addExternalNS(nsURI);
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
        // FIXME -- what about attributes?
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

