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

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.apache.xerces.xs.XSImplementation;
import org.apache.xerces.xs.XSLoader;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.xml.sax.SAXException;

/**
 *
 * A class for bootstrapping the Xerces XML Schema parser and the
 * default SAX parser.  Useful if you want to bootstrap once, perhaps
 * at the start of execution, and handle the possible exceptions then
 * and there.
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class ParserBootstrap {
    public static final int BOOTSTRAP_XERCES_XS = 1;
    public static final int BOOTSTRAP_SAX2 = 2;
    public static final int BOOTSTRAP_ALL = 3;
    
    private static SAXParserFactory sfact = null;
    private static XSImplementation xsimpl = null;          // Xerces XSImplementation, for creating XSLoader object

    ParserBootstrap () throws ParserConfigurationException {
        this(BOOTSTRAP_ALL);
    }
    
    ParserBootstrap(int which) throws ParserConfigurationException {
        if (0 != (which | BOOTSTRAP_SAX2) && sfact == null) {
            try {
                sfact = SAXParserFactory.newInstance();
                sfact.setNamespaceAware(true);
                sfact.setValidating(false);
                SAXParser saxp = sfact.newSAXParser();
            } catch (Exception ex) {
                throw (new ParserConfigurationException("Can't initialize suitable SAX2 parser" + ex.getMessage()));
            }
        }
        if (0 != (which | BOOTSTRAP_XERCES_XS) && xsimpl == null) {
            System.setProperty(DOMImplementationRegistry.PROPERTY, "org.apache.xerces.dom.DOMXSImplementationSourceImpl");
            DOMImplementationRegistry direg;
            try {
                direg = DOMImplementationRegistry.newInstance();
                xsimpl = (XSImplementation) direg.getDOMImplementation("XS-Loader");
            } catch (Exception ex) {
                throw (new ParserConfigurationException("Can't initializte Xerces XML Schema parser implementation" + ex.getMessage()));
            }
        }
    }
    
    /**
     * Returns a SAXParser object.  OK to reuse these.
     */
    SAXParser sax2Parser () throws ParserConfigurationException, SAXException {
        return sfact.newSAXParser();
    }
    
    /**
     * Returns a new XSLoader object.  Don't reuse these if you need to control
     * where the schema documents come from -- the loader object remembers and
     * happily reuses any document it has already seen.
     */
    
    XSLoader xsLoader () {
        return xsimpl.createXSLoader(null);
    }
}
