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

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.apache.xerces.xs.XSImplementation;
import org.apache.xerces.xs.XSLoader;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;

/**
 *
 * A class for bootstrapping the Xerces XML Schema parser and the
 * default SAX parser.
 * @author Scott Renner, The MITRE Corporation
 */
public class ParserBootstrap {
    private static SAXParserFactory sfact = null;
    private static SAXParser saxp = null;                   // reusable SAX2 parser object, for schema assembly checking
    private static XSImplementation xsimpl = null;          // Xerces XSImplementation, for creating XSLoader object

    ParserBootstrap() throws ParserConfigurationException {
        if (sfact == null) {
            try {
                sfact = SAXParserFactory.newInstance();
                sfact.setNamespaceAware(true);
                sfact.setValidating(false);
                saxp = sfact.newSAXParser();
            } catch (Exception ex) {
                throw (new ParserConfigurationException("Can't initialize suitable SAX parser" + ex.getMessage()));
            }
        }
        if (xsimpl == null) {
            System.setProperty(DOMImplementationRegistry.PROPERTY, "org.apache.xerces.dom.DOMXSImplementationSourceImpl");
            DOMImplementationRegistry direg;
            try {
                direg = DOMImplementationRegistry.newInstance();
                xsimpl = (XSImplementation) direg.getDOMImplementation("XS-Loader");
            } catch (Exception ex) {
                throw (new ParserConfigurationException("Can't initializte XML Schema parser implementation" + ex.getMessage()));
            }
        }
    }
    
    /**
     * Returns a SAXParser object.  OK to reuse these.
     */
    SAXParser sax2Parser () {
        return saxp;
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
