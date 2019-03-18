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
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author Scott Renner <sar@mitre.org>
 */
public class NIEMContext {
    
    private static final String NIEM_CONTEXT_RESOURCE = "/NIEM4.0context.json";
    private static HashMap<String,String> niemContextPrefix = null; // map ns URI -> usual prefix   

    public static String stdPrefix (String uri) {
        if (niemContextPrefix == null) {
            try {
                URL niemContext = NIEMContext.class.getResource(NIEM_CONTEXT_RESOURCE);
                File contextFile = FileUtils.toFile(niemContext);
                String contextData = FileUtils.readFileToString(contextFile, "utf-8");
                Gson gson = new Gson();
                StringReader r = new StringReader(contextData);
                JsonReader jr = new JsonReader(r);
                jr.beginObject();
                niemContextPrefix = new HashMap<>();
                while (jr.hasNext()) {
                    String key = jr.nextName();
                    String val = jr.nextString();
                    String nss = removeNamespaceVersion(val);
                    niemContextPrefix.put(nss, key);
                }  
            } catch (IOException ex) {
                Logger.getLogger(NIEMContext.class.getName()).log(Level.SEVERE, "Can't read NIEM context resource", ex);
            }
        }   
        String key = removeNamespaceVersion(uri);
        String res = niemContextPrefix.get(key);
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
