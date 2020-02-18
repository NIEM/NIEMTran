/*
 * Copyright 2020 The MITRE Corporation. All rights reserved.
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.PrintWriter;
import static java.lang.Math.max;
import static java.lang.Math.min;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author SAR
 */
public class NJ2Graphviz {
    private JsonObject data;    
    private JsonObject cxt = null;
    private String baseURI = null;
    private String mprefix = "message";
    
    final private Map<JsonObject,Integer> nodeNum = new HashMap<>();
    private int nodeCt = 0;
    private int nodeDigits;
    
    NJ2Graphviz (JsonObject obj) {
        this(obj, null, null);
    }
    
    NJ2Graphviz (JsonObject obj, String b) {
        this(obj, null, b);
    }
    
    NJ2Graphviz (JsonObject data, JsonObject cxt) {
        this(data, cxt, null);
    }
    
    NJ2Graphviz (JsonObject d, JsonObject c, String b) {
        cxt     = c;
        data    = d;
        baseURI = b;
    }
    
    /**
     * Write the graphviz syntax to the provided writer.
     */
    public void graphviz (PrintWriter pw) {
    
        // Extract context object if not provided separately
        if (cxt == null) {
            cxt = data.get("@context").getAsJsonObject();
        }
        // Override default resource URI if assigned in data
        if (data.has("@id")) {
            baseURI = data.get("@id").getAsString();
        }
        // Establish prefix for resource URI
        if (cxt.has(mprefix)) {
            int ctr = 1;
            while (cxt.has(mprefix)) {
                mprefix = String.format("message%02d", ctr++);
            }
        }
        // Walk the tree depth-first and assign node identifiers
        assignID(data);
        nodeDigits = (nodeCt < 10 ? 1 : (nodeCt < 100 ? 2 : (nodeCt < 1000 ? 3 : 4)));
        
        // Walk the tree again and write the node tuples
        pw.print("digraph G {\n");
        writeNode(pw, data);
        pw.print("}\n");
    }
    
    private void assignID (JsonObject obj) {
        if (!obj.has("@id")) {
            nodeNum.put(obj,++nodeCt);
        }
        for (Map.Entry<String,JsonElement>pair: obj.entrySet()) {
            String key = pair.getKey();
            JsonElement e = pair.getValue();
            if (!key.startsWith("@")) {
                if (e.isJsonArray()) {
                    JsonArray ea = e.getAsJsonArray();
                    for (int i = 0; i < ea.size(); i++) {
                        if (ea.get(i).isJsonObject()) {
                            JsonObject eo = ea.get(i).getAsJsonObject();
                            assignID(eo);
                        }
                    }
                }
                else if (e.isJsonObject()) {
                    assignID(e.getAsJsonObject());
                }
            }
        }        
    }
    
    private void writeNode (PrintWriter pw, JsonObject node) {
        // Any predicates in this node?
        boolean skip = true;
        for (Map.Entry<String,JsonElement>pair: node.entrySet()) {
            String pred   = pair.getKey();
            if (!pred.startsWith("@")) {
                skip = false;
                break;
            }
        }
        if (skip) { return; }
               
        for (Map.Entry<String,JsonElement>pair: node.entrySet()) {
            String pred   = pair.getKey();
            JsonElement e = pair.getValue();
            if (!pred.startsWith("@")) {
                writePredicate(pw, node, pred, e);
             }
        }
        for (Map.Entry<String,JsonElement>pair: node.entrySet()) {    
            String pred   = pair.getKey();
            JsonElement e = pair.getValue();
            if (!pred.startsWith("@")) {
                if (e.isJsonArray()) {
                    JsonArray ea = e.getAsJsonArray();
                    for (int i = 0; i < ea.size(); i++) {
                        if (ea.get(i).isJsonObject()) {
                            JsonObject eo = ea.get(i).getAsJsonObject();
                            writeNode(pw, eo);
                        }
                    }
                }
                else if (e.isJsonObject()) {
                    writeNode(pw, e.getAsJsonObject());
                }
            }
        }  
    }
    
    private void writePredicate (PrintWriter pw, JsonObject node, String pred, JsonElement e) {
        if (e.isJsonArray()) {
            JsonArray ea = e.getAsJsonArray();
            for (int i = 0; i < ea.size(); i++) {
                JsonElement ee = ea.get(i);
                writeValue(pw, node, pred, ee);
            }
        }
        else {
            writeValue(pw, node, pred, e);
        }
    }
    
    private void writeValue (PrintWriter pw, JsonObject node, String pred, JsonElement e) {
        String nodeID = genNodeID(node);
        if (e.isJsonObject()) {
            String objID = genNodeID(e.getAsJsonObject()) ;
            pw.printf(" \"%s\" -> \"%s\" [label=\" %s\"];\n", nodeID, objID, pred);           
        }
        else {
            pw.printf(" \"%s\" -> \"%s\" [label=\" %s\"];\n", nodeID, e.getAsString(), pred);
        }
    }
    
    private String genNodeID (JsonObject obj) {
        String nodeID;
        if (obj == data) {
            nodeID = mprefix+":";
        }
        else if (obj.has("@id")) {
            nodeID = String.format("%s:%s", mprefix, obj.get("@id").getAsString());
        }
        else {
            nodeID = String.format("_:n%0"+nodeDigits+"d", nodeNum.get(obj));
        }
        return nodeID;
    }    
}
