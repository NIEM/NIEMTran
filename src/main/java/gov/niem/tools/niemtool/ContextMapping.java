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
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.MalformedJsonException;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;

/**
 * A class for all of the JSON-LD context mappings supplied as resource
 * files, providing a mapping from a namespace URI to its common prefix string.
 * @author Scott Renner <sar@mitre.org>
 */
public class ContextMapping {
    
    private static final String CONTEXT_RESOURCE_DIR = "/contexts";
    private static HashMap<String,String> contextPrefix = null; // map namespace URI -> common prefix   

    /**
     * Returns the common prefix string for the specified namespace URI. 
     * The URI to prefix mapping is created by processing every file
     * in the context resource directory (OONTEXT_RESOURCE_DIR) as containing
     * a JSON-LD context. Comments are allowed, and the outer "@context" key is 
     * optional.
     * @param namespase uri
     * @return common prefix string for namespace
     */
    public static String commonPrefix (String uri) {
        if (contextPrefix == null) {
            contextPrefix = new HashMap<>();
            URL contextDir = ContextMapping.class.getResource(CONTEXT_RESOURCE_DIR);
            File dir = FileUtils.toFile(contextDir);
            File[] files = null;
            if (dir == null || (files = dir.listFiles()) == null) {
                Logger.getLogger(ContextMapping.class.getName()).log(
                        Level.SEVERE, String.format("Can't read context resource directory %s", CONTEXT_RESOURCE_DIR));
                return "";
            }
            for (File f : files) {
                try {
                    String data = FileUtils.readFileToString(f, "utf-8");
                    StringReader r = new StringReader(data);
                    JsonReader jr = new JsonReader(r);
                    jr.setLenient(true);
                    jr.beginObject();
                    String prefixKey = jr.nextName();
                    String nsURI;
                    if ("@context".equals(prefixKey)) {
                        jr.beginObject();
                    }
                    else {
                        nsURI = jr.nextString();
                        contextPrefix.put(nsURI, prefixKey);
                    }
                    while (jr.hasNext()) {
                        prefixKey = jr.nextName();
                        nsURI = jr.nextString();
                        contextPrefix.put(nsURI, prefixKey);
                    }
                } catch (Exception ex) {
                    Logger.getLogger(ContextMapping.class.getName()).log(Level.SEVERE, 
                            String.format("Can't process context resource file %s", f.getPath()), ex);
                } 
            } 
        }   
        String res = contextPrefix.get(uri);
        if (res == null) { return ""; }
        return res;
    }
    
    
    /**
     * Removes the version from a NIEM namespace URI or context value
     * For example, 
     *     http://release.niem.gov/niem/codes/hl7/4.0/# becomes
     *     http://release.niem.gov/niem/codes/hl7/
     * @param ns
     * @return 
     */
    private static String removeNamespaceVersion (String ns) {
        return ns.replaceFirst("\\d+\\.\\d+/#?$", "");
    }    
}
