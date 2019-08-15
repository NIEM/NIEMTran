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

package gov.niem.tools.niemtool;

import static gov.niem.tools.niemtool.ParserBootstrap.BOOTSTRAP_SAX2;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import org.xml.sax.SAXException;

/**
 * A class for translating NIEM XML to NIEM JSON.  Each object knows how to
 * translate all of the NIEM message formats defined by a single schema 
 * document pile.  An object can be reused to translate any number of NIEM 
 * messages, one at a time.   Any number of objects can be translating
 * messages in parallel.
 * 
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class Translate {
    public static final int X2J_OK = 0;          // all XML handled
    public static final int X2J_OMITTED = 1;     // some XML elements omitted from output
    public static final int X2J_EXTENDED = 2;    // context includes namespaces not found in schema
    public static final int X2J_EXT_OMIT = 3;    // both omitted and extended
    
    private static ParserBootstrap parser = null;
    private final NTSchemaModel model;
    
    /**
     * Construct from a schema model object
     * @param m
     */
    Translate (NTSchemaModel m) {
        model = m;
    }
    
    /**
     * Construct by loading a serialized schema model object
     * @param r model object input stream
     * @throws FormatException 
     */
    Translate (Reader r) throws FormatException {
        model = new NTSchemaModel(r);
    }
    
    // NIEM XML to NIEM JSON translation routines
    // You can have the JSON input as text or a JsonObject.
    // You can have the context object included or separate.
    // All return a status value:
    //   X2J_EXTENDED -- Components with an unexpected namespace in the input XML;
    //                   the schema model's context object is extended with these namespaces.
    //                   With valid NIEM XML, this can only happen via schema wildcards.
    //   X2J_OMITTED  -- Input XML contains elements from unhandled external or unexpected 
    //                   namespaces; this data is omitted from the output json.
    //   X2J_OK       -- All input XML translated; the context is the schema model's context.
    
    /**
     * Translates a NIEM XML input stream into a JSON character stream.
     * The output stream contains JSON-LD data plus context object.
     * @param is XML input stream
     * @param json JSON output stream
     */
    public int xml2json (InputStream is, Writer json) {
        return 0;
    }
    
    /**
     * Translates a NIEM XML input stream into a character stream with the
     * JSON-LD data, and a second optional character stream with the 
     * JSON-LD context.
     * @param is XML input stream
     * @param jsonData JSON data output stream
     * @param jsonContext JSON-LD context output stream (pass in null if not wanted)
     */
    public int xml2json (InputStream is, Writer jsonData, Writer jsonContext) {
        return 0;
    }
    
    /**
     * Translates a NIEM XML input stream into a JSON character stream.
     * The output stream contains JSON-LD data plus context object.
     * @param is XML input
     * @param json JSON object output
     */
    public int xml2json (InputStream is, JsonObject json) {
        return 0;
    }
    
    /**
     * Translates a NIEM XML input stream into a character stream with the
     * JSON-LD data, and a second optional character stream with the 
     * JSON-LD context.
     * @param is XML input
     * @param data -- new JsonObject to hold data output 
     * @param context -- new JsonObject to hold context object
     * @return 
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     * @throws javax.xml.parsers.ParserConfigurationException
     */
    public int xml2json(InputStream is, JsonObject data, JsonObject context)
            throws IOException, SAXException, ParserConfigurationException {

        if (parser == null) {
            parser = new ParserBootstrap(BOOTSTRAP_SAX2);
        }
        SAXParser saxp = parser.sax2Parser();
        NTXMLHandler handler = new NTXMLHandler(model, data, context);
        saxp.parse(is, handler);
        return handler.resultFlags();
    }
    
}
