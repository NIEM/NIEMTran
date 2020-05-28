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

package org.mitre.niem.niemtran;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.SchemaFactory;

import org.apache.xerces.dom.DOMInputImpl;
import org.apache.xerces.jaxp.SAXParserFactoryImpl;
import org.apache.xerces.util.URI;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xml.resolver.Catalog;
import org.apache.xml.resolver.CatalogManager;
import org.apache.xml.resolver.readers.OASISXMLCatalogReader;
import org.apache.xml.resolver.readers.SAXCatalogReader;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.EntityResolver2;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This class is modified from org.apache.xerces.utils.XMLCatalogResolver, 
 * <br>version ID: XMLCatalogResolver.java 699892 2008-09-28 21:08:27Z mrglavas, 
 * <br>by Michael Glavassevich, IBM. 
 * <br>Original source code used here under Apache 2.0 license.
 * 
 * <p>
 * The source code is identical except where marked by <code>//MODIFIED</code>
 * comments.
 *
 * <p>
 * The purpose is to have a version of the Xerces utility catalog resolver that can
 * report on what it is doing.  This reporting is typically not needed in production,
 * but can be helpful in development. The modifications allow the standard 
 * Xerces catalog resolver to report:
 *
 * <ul><li>Parsing results for all of the catalog files specified by the initial
 * list of catalogs, including all the subordinate catalogs. Catalog files
 * are validated against the XML Schema for XML Catalog documents; these
 * validation results are also reported.
 *
 * <li>Resolution results for all URIs resolved during construction of an 
 * XSModel object.</ul>
 *
 * <p>
 * Catalog file parsing results are obtained through a class extending the
 * org.apache.xml.resolver.Catalog class. The relevant <code>fCatalog</code>
 * member variable is private, which is why this class cannot be implemented
 * by extending the XMLCatalogResolver class.
 *
 * <p>
 * This modified catalog resolver class still handles the resolution of external
 * identifiers and URI references through XML catalogs. This component supports
 * XML catalogs defined by the
 * <a href="http://www.oasis-open.org/committees/entity/spec.html">
 * OASIS XML Catalogs Specification</a>. It encapsulates the
 * <a href="http://xml.apache.org/commons/">XML Commons</a> resolver. An
 * instance of this class may be registered on the parser as a SAX entity
 * resolver, as a DOM LSResourceResolver or as an XNI entity resolver by setting
 * the property (http://apache.org/xml/properties/internal/entity-resolver).
 *
 * <p>
 * It is still intended that this class may be used standalone to perform
 * catalog resolution outside of a parsing context. It may be shared between
 * several parsers and the application.</p>
 *
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class NTCatalogResolver
        implements XMLEntityResolver, EntityResolver2, LSResourceResolver {

    /**
     * Internal catalog manager for Apache catalogs. *
     */
    private CatalogManager fResolverCatalogManager = null;

    /**
     * Internal catalog structure. *
     */
    private NTCatalog fCatalog = null;      // MODIFIED: was Catalog

    /**
     * An array of catalog URIs. *
     */
    private String[] fCatalogsList = null;

    /**
     * Indicates whether the list of catalogs has changed since it was
     * processed.
     */
    private boolean fCatalogsChanged = true;

    /**
     * Application specified prefer public setting. *
     */
    private boolean fPreferPublic = true;

    /**
     * Indicates whether the application desires that the parser or some other
     * component performing catalog resolution should use the literal system
     * identifier instead of the expanded system identifier.
     */
    private boolean fUseLiteralSystemId = true;

    /**
     * <p>
     * Constructs a catalog resolver with a default configuration.</p>
     */
    public NTCatalogResolver() {
        this(null, true);
    }

    /**
     * <p>
     * Constructs a catalog resolver with the given list of entry files.</p>
     *
     * @param catalogs an ordered array list of absolute URIs
     */
    public NTCatalogResolver(String[] catalogs) {
        this(catalogs, true);
    }

    /**
     * <p>
     * Constructs a catalog resolver with the given list of entry files and the
     * preference for whether system or public matches are preferred.</p>
     *
     * @param catalogs an ordered array list of absolute URIs
     * @param preferPublic the prefer public setting
     */
    public NTCatalogResolver(String[] catalogs, boolean preferPublic) {
        init(catalogs, preferPublic);
    }

    // MODIFIED -- new class members and methods begin ------------------------
    
    public static final String XMLCATALOG_NS = "http://www.oasis-open.org/committees/entity/release/1.0/catalog.dtd";
    private static SAXParser catParser = null;                  // XML Catalog parser   
    private List<String> resolutionMsgs = new ArrayList<>();    // catalog resolution results; clear with resetResolutions();
    
    /**
     * Returns a list of file URIs for all requested catalog files. 
     * Includes URIs for catalog files not found, not readable, not
     * parsed, and not valid against the XML Catalog schema.
     * @return list of file URIs for all requested catalog files
     */
    public List<String>allCatalogFiles() {
        List<String> res = new ArrayList<>();
        if (fCatalog != null && fCatalog.parsingResults() != null) {
            for (String s : fCatalog.parsingResults()) {
                int idx = s.indexOf(": ");
                String furi = s.substring(idx + 2);
                res.add(furi);
            }
        }
        return res;
    }
    
    /**
     * Returns a list of file URIs for all valid XML Catalog files.
     * @return list of URIs for all valid catalogs
     */
    public List<String> allValidCatalogFiles() {
        List<String> res = new ArrayList<>();
        for (String s : validationResults()) {
            if (s.endsWith(": OK\n")) {
                String furi = s.substring(0,s.length()-5);
                res.add(furi);
            }
        }
        return res;
    }
    
    /**
     * Returns results of parsing initial and subordinate catalogs.
     * @return list of parsing results
     */
    public List<String> validationResults() {
        if (fCatalogsChanged) {
            try {
                parseCatalogs();
            } catch (IOException ex) {
                // IGNORE -- NTCatalog logs warnings when parsing, can throw IOException
            }
        }
        // NTCatalog returns results like "OK : file:/path"
        // Reverse this order into "file:/path : OK"
        // Append schema validation results for catalogs that parsed OK
        List<String> result = new ArrayList<>();
        if (fCatalog == null) {
            return result;
        }
        for (String rs : fCatalog.parsingResults()) {
            if (!rs.startsWith("OK: ")) {
                int idx = rs.indexOf(": ");
                String furi = rs.substring(idx+2);
                String msg = rs.substring(0,idx);
                result.add(String.format("%s : %s\n", furi, msg));
            }
            else {
                String furi = rs.substring(4);
                String valres = validateCatalog(furi);
                if ("".equals(valres)) {
                    result.add(String.format("%s : OK\n", furi));
                }
                else {
                    StringBuilder res = new StringBuilder(String.format("%s :\n", furi));
                    String[] msgs = valres.split("\n");
                    for (String s : msgs) {
                        res.append("    ").append(s).append("\n");
                    }
                    result.add(res.toString());
                }
            }
        }
        return result;
    }

    /**
     * Return list of catalog validation error messages..
     * Returns an empty list if no errors encountered.
     * @return catalog error messages
     */
    public List<String> validationErrors() {
        List<String> result = new ArrayList<>();
        for (String s : validationResults()) {
            if (!s.endsWith(": OK\n")) {
                result.add(s);
            }
        }
        return result;
    }
    
    /**
     * Return results of all resolution operations since last reset().
     * @return resolution messages
     */
    public List<String> resolutionMessages () {
        return resolutionMsgs;
    }
    
    /**
     * Reset the list of resolution operations to an empty list.
     */
    public void resetResolutions () {
        resolutionMsgs.clear();
    }
    
    /**
     * Validates an OASIS XML Catalog document. Returns messages from 
     * the XML Schema validation. Returns an empty string if no messages
     * produced. Returns <code>??? (parser exception)</code> if the 
     * validating parser fails to execute or is not available.
     * @param furi document file URI
     * @return validation messages
     */
    public String validateCatalog(String furi) {
        if (catParser == null) {
            URL catSchema = this.getClass().getResource("/XMLCatalogSchema.xsd");
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            javax.xml.validation.Schema schema = null;
            try {
                schema = sf.newSchema(catSchema);
            } catch (SAXException ex) {
                Logger.getLogger(NTCatalogResolver.class.getName()).log(Level.SEVERE,
                        "can't construct schema from " + catSchema.toString(), ex);
            }
            try {
                SAXParserFactory spf = SAXParserFactory.newInstance();
                spf.setValidating(false);
                spf.setNamespaceAware(true);
                spf.setSchema(schema);
                catParser = spf.newSAXParser();
            } catch (SAXException ex) {
                Logger.getLogger(NTCatalogResolver.class.getName()).log(Level.SEVERE, "SAXException", ex);
            } catch (ParserConfigurationException ex) {
                Logger.getLogger(NTCatalogResolver.class.getName()).log(Level.SEVERE, "ParserConfigException", ex);
            }
        }
        String res = "??? (parser exception)";
        if (catParser != null) {
            StringBuilder msgs = new StringBuilder();
            CatErrorHandler h = new CatErrorHandler(msgs);
            XMLReader cr = null;
            try {
                cr = catParser.getXMLReader();
                cr.setEntityResolver(h);
                catParser.parse(furi, h);
            } catch (SAXException | IOException ex) {
                Logger.getLogger(NTCatalogResolver.class.getName()).log(Level.SEVERE, null, ex);
            }
            res = msgs.toString();
        }
        return res;
     }
    
    private class CatErrorHandler extends DefaultHandler {
        private StringBuilder msgs;
        private Locator loc;
        CatErrorHandler (StringBuilder msgs) {
            super();
            this.msgs = msgs;
        }
        @Override
        public void error (SAXParseException ex) { 
            int line = loc.getLineNumber();
            msgs.append(String.format("line %d: %s\n",line,ex.getMessage()));
        }
        @Override public void warning (SAXParseException ex)  { error(ex); }
        @Override public void fatalError (SAXParseException ex) { error(ex); }    
        @Override
        public InputSource resolveEntity (String pid, String sid) {
            InputStream catDTD = this.getClass().getResourceAsStream("/catalog.dtd");
            if (!XMLCATALOG_NS.equals(sid)) {
                msgs.append("  non-local entity in catalog file\n");
            }
            return new InputSource(catDTD);
        }
        @Override
        public void setDocumentLocator(Locator locator) {
            this.loc = locator;
        }        
    }
    
    // MODIFIED -- end of new methods ----------------------------------------
    
    /**
     * <p>
     * Returns the initial list of catalog entry files.</p>
     *
     * @return the initial list of catalog entry files
     */
    public final synchronized String[] getCatalogList() {
        return (fCatalogsList != null)
                ? (String[]) fCatalogsList.clone() : null;
    }

    /**
     * <p>
     * Sets the initial list of catalog entry files. If there were any catalog
     * mappings cached from the previous list they will be replaced by catalog
     * mappings from the new list the next time the catalog is queried.</p>
     *
     * @param catalogs an ordered array list of absolute URIs
     */
    public final synchronized void setCatalogList(String[] catalogs) {
        fCatalogsChanged = true;
        fCatalogsList = (catalogs != null)
                ? (String[]) catalogs.clone() : null;
    }

    /**
     * <p>
     * Forces the cache of catalog mappings to be cleared.</p>
     */
    public final synchronized void clear() {
        fCatalog = null;
    }

    /**
     * <p>
     * Returns the preference for whether system or public matches are
     * preferred. This is used in the absence of any occurrence of the
     * <code>prefer</code> attribute on the <code>catalog</code> entry of a
     * catalog. If this property has not yet been explicitly set its value is
     * <code>true</code>.</p>
     *
     * @return the prefer public setting
     */
    public final boolean getPreferPublic() {
        return fPreferPublic;
    }

    /**
     * <p>
     * Sets the preference for whether system or public matches are preferred.
     * This is used in the absence of any occurrence of the <code>prefer</code>
     * attribute on the <code>catalog</code> entry of a catalog.</p>
     *
     * @param preferPublic the prefer public setting
     */
    public final void setPreferPublic(boolean preferPublic) {
        fPreferPublic = preferPublic;
        fResolverCatalogManager.setPreferPublic(preferPublic);
    }

    /**
     * <p>
     * Returns the preference for whether the literal system identifier should
     * be used when resolving system identifiers when both it and the expanded
     * system identifier are available. If this property has not yet been
     * explicitly set its value is <code>true</code>.</p>
     *
     * @return the preference for using literal system identifiers for catalog
     * resolution
     *
     * @see #setUseLiteralSystemId
     */
    public final boolean getUseLiteralSystemId() {
        return fUseLiteralSystemId;
    }

    /**
     * <p>
     * Sets the preference for whether the literal system identifier should be
     * used when resolving system identifiers when both it and the expanded
     * system identifier are available.</p>
     *
     * <p>
     * The literal system identifier is the URI as it was provided before
     * absolutization. It may be embedded within an entity. It may be provided
     * externally or it may be the result of redirection. For example,
     * redirection may have come from the protocol level through HTTP or from an
     * application's entity resolver.</p>
     *
     * <p>
     * The expanded system identifier is an absolute URI which is the result of
     * resolving the literal system identifier against a base URI.</p>
     *
     * @param useLiteralSystemId the preference for using literal system
     * identifiers for catalog resolution
     */
    public final void setUseLiteralSystemId(boolean useLiteralSystemId) {
        fUseLiteralSystemId = useLiteralSystemId;
    }

    /**
     * <p>
     * Resolves an external entity. If the entity cannot be resolved, this
     * method should return <code>null</code>. This method returns an input
     * source if an entry was found in the catalog for the given external
     * identifier. It should be overridden if other behaviour is required.</p>
     *
     * @param publicId the public identifier, or <code>null</code> if none was
     * supplied
     * @param systemId the system identifier
     *
     * @throws SAXException any SAX exception, possibly wrapping another
     * exception
     * @throws IOException thrown if some i/o error occurs
     */
    public InputSource resolveEntity(String publicId, String systemId)
            throws SAXException, IOException {

        String resolvedId = null;
        if (publicId != null && systemId != null) {
            resolvedId = resolvePublic(publicId, systemId);
        } else if (systemId != null) {
            resolvedId = resolveSystem(systemId);
        }
        resolutionMsgs.add(String.format("resolveEntity(%s,%s) -> %s\n", publicId, systemId, resolvedId)); // MODIFIED

        if (resolvedId != null) {
            InputSource source = new InputSource(resolvedId);
            source.setPublicId(publicId);
            return source;
        }
        return null;
    }

    /**
     * <p>
     * Resolves an external entity. If the entity cannot be resolved, this
     * method should return <code>null</code>. This method returns an input
     * source if an entry was found in the catalog for the given external
     * identifier. It should be overridden if other behaviour is required.</p>
     *
     * @param name the identifier of the external entity
     * @param publicId the public identifier, or <code>null</code> if none was
     * supplied
     * @param baseURI the URI with respect to which relative systemIDs are
     * interpreted.
     * @param systemId the system identifier
     *
     * @throws SAXException any SAX exception, possibly wrapping another
     * exception
     * @throws IOException thrown if some i/o error occurs
     */
    public InputSource resolveEntity(String name, String publicId,
            String baseURI, String systemId) throws SAXException, IOException {

        String resolvedId = null;

        if (!getUseLiteralSystemId() && baseURI != null) {
            // Attempt to resolve the system identifier against the base URI.
            try {
                URI uri = new URI(new URI(baseURI), systemId);
                systemId = uri.toString();
            } // Ignore the exception. Fallback to the literal system identifier.
            catch (URI.MalformedURIException ex) {
            }
        }

        if (publicId != null && systemId != null) {
            resolvedId = resolvePublic(publicId, systemId);
        } else if (systemId != null) {
            resolvedId = resolveSystem(systemId);
        }
        resolutionMsgs.add(String.format("resolveEntity(%s,%s,%s) -> %s\n",  //MODIFIED
                publicId, baseURI, systemId, resolvedId));

        if (resolvedId != null) {
            InputSource source = new InputSource(resolvedId);
            source.setPublicId(publicId);
            return source;
        }
        return null;
    }

    /**
     * <p>
     * Locates an external subset for documents which do not explicitly provide
     * one. This method always returns <code>null</code>. It should be overrided
     * if other behaviour is required.</p>
     *
     * @param name the identifier of the document root element
     * @param baseURI the document's base URI
     *
     * @throws SAXException any SAX exception, possibly wrapping another
     * exception
     * @throws IOException thrown if some i/o error occurs
     */
    public InputSource getExternalSubset(String name, String baseURI)
            throws SAXException, IOException {
        return null;
    }

    /**
     * <p>
     * Resolves a resource using the catalog. This method interprets that the
     * namespace URI corresponds to uri entries in the catalog. Where both a
     * namespace and an external identifier exist, the namespace takes
     * precedence.</p>
     *
     * @param type the type of the resource being resolved
     * @param namespaceURI the namespace of the resource being resolved, or
     * <code>null</code> if none was supplied
     * @param publicId the public identifier of the resource being resolved, or
     * <code>null</code> if none was supplied
     * @param systemId the system identifier of the resource being resolved, or
     * <code>null</code> if none was supplied
     * @param baseURI the absolute base URI of the resource being parsed, or
     * <code>null</code> if there is no base URI
     */
    public LSInput resolveResource(String type, String namespaceURI,
            String publicId, String systemId, String baseURI) {

        String resolvedId = null;

        try {
            // The namespace is useful for resolving namespace aware
            // grammars such as XML schema. Let it take precedence over
            // the external identifier if one exists.
            if (namespaceURI != null) {
                resolvedId = resolveURI(namespaceURI);

            }

            if (!getUseLiteralSystemId() && baseURI != null) {
                // Attempt to resolve the system identifier against the base URI.
                try {
                    URI uri = new URI(new URI(baseURI), systemId);
                    systemId = uri.toString();
                } // Ignore the exception. Fallback to the literal system identifier.
                catch (URI.MalformedURIException ex) {
                }
            }

            // Resolve against an external identifier if one exists. This
            // is useful for resolving DTD external subsets and other 
            // external entities. For XML schemas if there was no namespace 
            // mapping we might be able to resolve a system identifier 
            // specified as a location hint.
            if (resolvedId == null) {
                if (publicId != null && systemId != null) {
                    resolvedId = resolvePublic(publicId, systemId);
                } else if (systemId != null) {
                    resolvedId = resolveSystem(systemId);
                }
            }
        } // Ignore IOException. It cannot be thrown from this method.
        catch (IOException ex) {
        }       
        
        if (resolvedId != null) {
            return new DOMInputImpl(publicId, resolvedId, baseURI);
        }
        return null;
    }

    /**
     * <p>
     * Resolves an external entity. If the entity cannot be resolved, this
     * method should return <code>null</code>. This method only calls
     * <code>resolveIdentifier</code> and returns an input source if an entry
     * was found in the catalog. It should be overridden if other behaviour is
     * required.</p>
     *
     * @param resourceIdentifier location of the XML resource to resolve
     *
     * @throws XNIException thrown on general error
     * @throws IOException thrown if some i/o error occurs
     */
    public XMLInputSource resolveEntity(XMLResourceIdentifier resourceIdentifier)
            throws XNIException, IOException {

        String resolvedId = resolveIdentifier(resourceIdentifier);
        if (resolvedId != null) {
            return new XMLInputSource(resourceIdentifier.getPublicId(),
                    resolvedId,
                    resourceIdentifier.getBaseSystemId());
        }
        return null;
    }

    /**
     * <p>
     * Resolves an identifier using the catalog.This method interprets that the
     * namespace of the identifier corresponds to uri entries in the catalog.
     * Where both a namespace and an external identifier exist, the namespace
     * takes precedence.</p>
     *
     * @param resourceIdentifier the identifier to resolve
     * @return resolution result URI
     *
     * @throws XNIException thrown on general error
     * @throws IOException thrown if some i/o error occurs
     */
    public String resolveIdentifier(XMLResourceIdentifier resourceIdentifier)
            throws IOException, XNIException {

        String resolvedId = null;

        // The namespace is useful for resolving namespace aware
        // grammars such as XML schema. Let it take precedence over
        // the external identifier if one exists.
        String namespace = resourceIdentifier.getNamespace();
        if (namespace != null) {
            resolvedId = resolveURI(namespace);
         }

        // Resolve against an external identifier if one exists. This
        // is useful for resolving DTD external subsets and other 
        // external entities. For XML schemas if there was no namespace 
        // mapping we might be able to resolve a system identifier 
        // specified as a location hint.
        if (resolvedId == null) {
            String publicId = resourceIdentifier.getPublicId();
            String systemId = getUseLiteralSystemId()
                    ? resourceIdentifier.getLiteralSystemId()
                    : resourceIdentifier.getExpandedSystemId();
            if (publicId != null && systemId != null) {
                resolvedId = resolvePublic(publicId, systemId);
            } else if (systemId != null) {
                resolvedId = resolveSystem(systemId);              
            }
        }

        return resolvedId;
    }

    /**
     * <p>
     * Returns the URI mapping in the catalog for the given external identifier
     * or <code>null</code> if no mapping exists. If the system identifier is an
     * URN in the <code>publicid</code> namespace it is converted into a public
     * identifier by URN "unwrapping" as specified in the XML Catalogs
     * specification.</p>
     *
     * @param systemId the system identifier to locate in the catalog
     *
     * @return the mapped URI or <code>null</code> if no mapping was found in
     * the catalog
     *
     * @throws IOException if an i/o error occurred while reading the catalog
     */
    public final synchronized String resolveSystem(String systemId)
            throws IOException {

        if (fCatalogsChanged) {
            parseCatalogs();
            fCatalogsChanged = false;
        }
        String res = (fCatalog != null) ? fCatalog.resolveSystem(systemId) : null;  // MODIFIED
        resolutionMsgs.add(String.format("resolveSystem(%s) -> %s\n", systemId, res));
        return res;
    }

    /**
     * <p>
     * Returns the URI mapping in the catalog for the given external identifier
     * or <code>null</code> if no mapping exists. Public identifiers are
     * normalized before comparison.</p>
     *
     * @param publicId the public identifier to locate in the catalog
     * @param systemId the system identifier to locate in the catalog
     *
     * @return the mapped URI or <code>null</code> if no mapping was found in
     * the catalog
     *
     * @throws IOException if an i/o error occurred while reading the catalog
     */
    public final synchronized String resolvePublic(String publicId, String systemId)
            throws IOException {

        if (fCatalogsChanged) {
            parseCatalogs();
            fCatalogsChanged = false;
        }
        String res = (fCatalog != null) ? fCatalog.resolvePublic(publicId, systemId) : null;    // MODIFIED
        resolutionMsgs.add(String.format("resolvePublic(%s,%s) -> %s\n", publicId, systemId, res));
        return res;
    }

    /**
     * <p>
     * Returns the URI mapping in the catalog for the given URI reference or
     * <code>null</code> if no mapping exists. URI comparison is case sensitive.
     * If the URI reference is an URN in the <code>publicid</code> namespace it
     * is converted into a public identifier by URN "unwrapping" as specified in
     * the XML Catalogs specification and then resolution is performed following
     * the semantics of external identifier resolution.</p>
     *
     * @param uri the URI to locate in the catalog
     *
     * @return the mapped URI or <code>null</code> if no mapping was found in
     * the catalog
     *
     * @throws IOException if an i/o error occurred while reading the catalog
     */
    public final synchronized String resolveURI(String uri)
            throws IOException {

        if (fCatalogsChanged) {
            parseCatalogs();
            fCatalogsChanged = false;
        }
        String res = (fCatalog != null) ? fCatalog.resolveURI(uri) : null; // MODIFIED
        if (uri != null) {
            resolutionMsgs.add(String.format("resolveURI(%s) -> %s\n", uri, res));
        }
        return res;
    }

    /**
     * Initialization. Create a CatalogManager and set all the properties
     * upfront. This prevents JVM wide system properties or a property file
     * somewhere in the environment from affecting the behaviour of this catalog
     * resolver.
     */
    private void init(String[] catalogs, boolean preferPublic) {
        fCatalogsList = (catalogs != null) ? (String[]) catalogs.clone() : null;
        fPreferPublic = preferPublic;
        fResolverCatalogManager = new CatalogManager();
        fResolverCatalogManager.setAllowOasisXMLCatalogPI(false);
        fResolverCatalogManager.setCatalogClassName("niemtran.NTCatalogResolver"); // MODIFIED, was org.apache.xml.resolver.Catalog
        fResolverCatalogManager.setCatalogFiles("");
        fResolverCatalogManager.setIgnoreMissingProperties(true);
        fResolverCatalogManager.setPreferPublic(fPreferPublic);
        fResolverCatalogManager.setRelativeCatalogs(false);
        fResolverCatalogManager.setUseStaticCatalog(false);
        fResolverCatalogManager.setVerbosity(0);
    }

    /**
     * Instruct the <code>Catalog</code> to parse each of the catalogs in the
     * list. All subordinate catalogs will be validated and parsed now. This is
     * not the same as than the lazy behavior in the base class, where only the
     * first catalog will actually be parsed immediately, and the others queued
     * and read only if they are needed later.
     */
    private void parseCatalogs() throws IOException {
        if (fCatalogsList != null) {
            fCatalog = new NTCatalog(fResolverCatalogManager);  // MODIFIED: was Catalog
            fCatalog.clearCatalogList();                        // MODIFIED: reset the static list of catalog files
            attachReaderToCatalog(fCatalog);
            for (int i = 0; i < fCatalogsList.length; ++i) {
                String catalog = fCatalogsList[i];
                if (catalog != null && catalog.length() > 0) {
                    fCatalog.parseCatalog(catalog);
                }
            }
            fCatalog.parseAllCatalogs();    // MODIFIED: parse all subordinate catalogs now
        } else {
            fCatalog = null;
        }
    }

    /**
     * Attaches the reader to the catalog.
     */
    private void attachReaderToCatalog(Catalog catalog) {

        SAXParserFactory spf = new SAXParserFactoryImpl();
        spf.setNamespaceAware(true);
        spf.setValidating(false);

        SAXCatalogReader saxReader = new SAXCatalogReader(spf);
        saxReader.setCatalogParser(OASISXMLCatalogReader.namespaceName, "catalog",
                "org.apache.xml.resolver.readers.OASISXMLCatalogReader");
        catalog.addReader("application/xml", saxReader);
    }
}
