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

import com.google.gson.stream.JsonReader;
import java.io.File;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;

/**
 * A class for all of the JSON-LD context mappings supplied as library "share"
 * files, providing a mapping from a namespace URI to its common prefix string.
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class ContextMap {
    
    private Map<String,String> contextPrefix; // map namespace URI -> common prefix   

    private ContextMap() {
        // Locate directory of context files.  They are in one place with
        // the IDE, another when deployed.
        contextPrefix = new HashMap<>();
        File contextDir;
        String appdir = ContextMap.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (appdir.endsWith(".jar")) {
            contextDir = FileUtils.getFile(appdir, "..", "..", "share", "contexts");
        } else {
            contextDir = FileUtils.getFile(appdir, "..", "..", "src", "main", "share", "contexts");
        }
        File[] files = null;
        if (contextDir == null || (files = contextDir.listFiles()) == null) {
            Logger.getLogger(ContextMap.class.getName()).log(
                    Level.SEVERE, String.format("Can't read context resource directory"));
            return;
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
                } else {
                    nsURI = jr.nextString();
                    nsURI = nsURI.substring(0, nsURI.length() - 1);
                    contextPrefix.put(nsURI, prefixKey);
                }
                while (jr.hasNext()) {
                    prefixKey = jr.nextName();
                    nsURI = jr.nextString();
                    String cp = contextPrefix.get(nsURI);
                    if (cp != null && !cp.equals(prefixKey)) {
                        Logger.getLogger(ContextMap.class.getName()).log(Level.WARNING,
                                String.format("Conflicting context resources in %s for %s",
                                        contextDir.getPath(), nsURI));
                    } else {
                        nsURI = nsURI.substring(0, nsURI.length() - 1);
                        contextPrefix.put(nsURI, prefixKey);
                    }
                }
            } catch (Exception ex) {
                Logger.getLogger(ContextMap.class.getName()).log(Level.SEVERE,
                        String.format("Can't process context resource file %s", f.getPath()), ex);
            }
        }
    }  
    
    private static class Holder {
        private static final ContextMap instance = new ContextMap();
    }
    
    public static ContextMap getInstance () {
        return Holder.instance;
    }
    
    /**
     * Returns the well-known prefix string for the specified namespace URI. 
     * The URI to prefix mapping is created by processing every file
     * in the "share/context" directory as a JSON-LD context. Comments are allowed, 
     * and the outer "@context" key is optional.
     * @param uri namespace
     * @return common prefix string for namespace
     */
    public String wellKnownPrefix (String uri) {
        return contextPrefix.get(uri);
    }
    
}
