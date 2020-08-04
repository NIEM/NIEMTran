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

package org.mitre.niem.niemtran;

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

public class Translator {
    public static final int X2J_OK = 0;          // all XML handled
    public static final int X2J_OMITTED = 1;     // some XML elements omitted from output
    public static final int X2J_EXTENDED = 2;    // context includes namespaces not found in schema
    public static final int X2J_EXT_OMIT = 3;    // both omitted and extended
    
    private final NTSchemaModel model;    
    
    /**
     * Construct from a schema model object
     * @param m
     */
    Translator (NTSchemaModel m) {
        model = m;
    }
    
    /**
     * Construct by loading a serialized schema model object
     * @param r model object input stream
     * @throws FormatException 
     */
    Translator (Reader r) throws FormatException {
        model = new NTSchemaModel(r);
    }
    
    // NIEM XML to NIEM JSON translation routines
    // You can have the JSON output as text or a JsonObject.
    // You can have the context object included in the json data or as a separate object.
    // All return status flags:
    //   X2J_EXTENDED -- Components with an unexpected namespace found in the input XML;
    //                   the schema model's context object is extended with these namespaces.
    //                   With valid NIEM XML, this can only happen via schema wildcards.
    //   X2J_OMITTED  -- Input XML contained elements from unhandled external or unexpected 
    //                   namespaces; this data was omitted from the output json.
    //   X2J_OK       -- All input XML translated; the context is same as the schema model's context.
    
    /**
     * Translates a NIEM XML input stream into a JSON character stream.
     * The output stream contains JSON-LD data plus context object.
     * @param is XML input stream
     * @param json JSON output stream
     */
    public int xml2json (InputStream is, Writer json) 
            throws IOException, SAXException, ParserConfigurationException {        
        return 0; //FIXME
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
        return 0; //FIXME
    }
    
    /**
     * Translates a NIEM XML input stream into a single JSON object containing
     * data plus the JSON-LD context
     * @param is XML input
     * @param json JSON object output
     */
    public int xml2json_dataAndContext (InputStream is, JsonObject json) {
        return 0; //FIXME
    }
    
    public int xml2json_dataOnly () {
        return 0; //FIXME
    }
    
    /**
     * Translates a NIEM XML input stream into one JsonObject containing the
     * converted data, and into a second JsonObject containing the context pairs.
     * If wildcard components from unknown namespaces are found, they are treated
     * as NIEM-conforming, the unknown namespaces included in the output context,
     * and X2J_EXTENDED set.
     * @param xmldat 
     * @param data 
     * @param context 
     * @return conversion result flags
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     * @throws javax.xml.parsers.ParserConfigurationException
     */
    public int xml2json_dataAndContext (InputStream xmldat, JsonObject data, JsonObject context) 
            throws IOException, SAXException, ParserConfigurationException {
        model.generateContext(context);
        JsonObject ecxt = new JsonObject();
        int rv = xml2json(xmldat, data, ecxt);
        if ((rv & X2J_EXTENDED) != 0) {
            ecxt.entrySet().forEach((e) -> {
                context.add(e.getKey(), e.getValue());
            });
        }
        return 0;
    }
    
    /**
     * Translates a NIEM XML input stream into NIEM JSON.  Caller provides
     * one JSON object to hold the converted data. Caller provides a second
     * JSON object to hold extensions to the schema model context resulting from 
     * wildcard elements or attributes in the input stream. The caller must 
     * construct the complete extended context if one is needed.
     * @param xmldat -- XML input stream
     * @param data -- empty JsonObject to contain the converted data
     * @param ecxt -- empty JsonObject to contain the extended context for this
     *                data if it includes namespaces not found in the schema
     *                model context (X2J_EXTENDED is set); will be unchanged otherwise.
     * @return conversion result flags
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     * @throws javax.xml.parsers.ParserConfigurationException
     */
    public int xml2json(InputStream xmldat, JsonObject data, JsonObject ecxt)
            throws IOException, SAXException, ParserConfigurationException {
        
        SAXParser saxp = ParserBootstrap.sax2Parser();
        NTXMLHandler handler = new NTXMLHandler(model, data, ecxt);
        saxp.parse(xmldat, handler);
        return handler.resultFlags();
    }
    
}
