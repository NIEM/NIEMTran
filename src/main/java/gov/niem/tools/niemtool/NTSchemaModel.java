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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * A class to represent the aspects of a NIEM schema needed to drive translation
 * between NIEM XML, NIEM JSON, and NIEM RDF.
 * 
 * @author Scott Renner <sar@mitre.org>
 */
public class NTSchemaModel {
    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();
       
    private final String modelVersion = "1.0";
    
    private HashMap<String,String> attributes;
    private HashMap<String,String> simpleElements;
    private HashMap<String,String> namespacePrefix;
    private HashMap<String,String> externalNSHandler;
    
    public NTSchemaModel () {     
        this.attributes       = new HashMap<>();
        this.simpleElements   = new HashMap<>();
        this.namespacePrefix  = new HashMap<>();
        this.externalNSHandler = new HashMap<>();
    }
    
    public NTSchemaModel(String jsonString) throws FormatException {
        NTSchemaModel m = null;
        try {
            m = gson.fromJson(jsonString, NTSchemaModel.class);
            this.attributes       = m.attributes;
            this.simpleElements   = m.simpleElements;
            this.namespacePrefix  = m.namespacePrefix;
            this.externalNSHandler = m.externalNSHandler;
        } catch (RuntimeException ex) {
            throw (new FormatException("Can't initialize NTSchemaModel", ex)); 
        }
    }
    
    public NTSchemaModel(Reader r) throws FormatException {
        NTSchemaModel m = null;
        try {
            m = gson.fromJson(r, NTSchemaModel.class);
            this.attributes       = m.attributes;
            this.simpleElements   = m.simpleElements;
            this.namespacePrefix  = m.namespacePrefix;
            this.externalNSHandler = m.externalNSHandler;
        } catch (RuntimeException ex) {
            throw (new FormatException("Can't initialize NTSchemaModel", ex));            
        }
    }
    
    public String attributeType(String uri) {
        return attributes.get(uri);
    }
    
    public String simpleElementType(String uri) {
        return simpleElements.get(uri);
    }
    
    public Map<String,String> attributes () {
        return attributes;
    }
    
    public Map<String,String> simpleElements () {
        return simpleElements;
    }
    
    public Map<String,String> namespacePrefix () {
        return namespacePrefix;
    }
    
    public Map<String,String> externalNSHandler () {
        return externalNSHandler;
    }
    
    public void addAttribute(String uri, String type) {
        attributes.put(uri, type);
    }
    
    public void addSimpleElement(String uri, String type) {
        simpleElements.put(uri, type);    
    }
    
    public void addNamespacePrefix (String namespace, String prefix) {
        namespacePrefix.put(namespace, prefix);
    }
    
    public void addExternalNS (String ns) {
        externalNSHandler.put(ns, "");
    }
    
    public String toJson () {
        return gson.toJson(this);
    }
}

