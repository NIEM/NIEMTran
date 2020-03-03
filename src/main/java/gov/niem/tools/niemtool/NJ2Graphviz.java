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
        
        // count the data rows and columns
        List<Map.Entry<String,JsonElement>> dataRow = new ArrayList<>();
        int dataColCt = 0;
        boolean skip = true;
        for (Map.Entry<String,JsonElement>pair: node.entrySet()) {
            String pred   = pair.getKey();
            JsonElement e = pair.getValue();
            if (!pred.startsWith("@")) {
                skip = false;
                if (e.isJsonArray()) {
                    JsonArray ea = e.getAsJsonArray();
                    int ncols = countDataCells(ea);
                    if (ncols > 0) { 
                        dataRow.add(pair); 
                        dataColCt = max(dataColCt, ncols);
                    }
                }
                else {
                    if (!e.isJsonObject()) {
                        dataRow.add(pair);
                        dataColCt = max(1, dataColCt);
                    }
                }
            }
        }
        if (skip) { return; }
        
        int maxDataRows = 3;
        int maxDataCols = 3;
        
        // write the graphviz for the node
        String nodeID = genNodeID(node);        
        if (dataRow.size() < 1) {
            pw.printf("\"%s\";\n", nodeID);
        }
        else {
            int colMax = min(dataColCt, maxDataCols);
            int rowMax = min(dataRow.size(), maxDataRows);
            pw.printf("\"%s\" [shape=\"plaintext\",label=<\n", nodeID);
            pw.print("<font point-size=\"12\">\n");
            pw.print("<table border=\"0\" cellborder=\"1\" cellspacing=\"0\">\n");
            int row = 0;
            while (row < rowMax) {
                Map.Entry<String, JsonElement> pair = dataRow.get(row);
                String pred = pair.getKey();
                JsonElement e = pair.getValue();
                pw.print(" <tr>\n");
                
                // last row is ellipsis row if too many rows
                if (row != rowMax-1 && dataRow.size() > maxDataRows) {
                    pw.printf("  <td>...</td><td colspan=\"%d\">...</td>\n", dataColCt+1);
                } 
                else {
                    if (row == 0) {
                        pw.printf("  <td rowspan=\"%d\"><b>%s</b></td>\n", rowMax, nodeID);
                    }
                    pw.printf("  <td align=\"right\">%s</td>\n", pred);
                    if (e.isJsonArray()) {
                        JsonArray ea = e.getAsJsonArray();
                        int col = 0;
                        int ncols = countDataCells(ea);
                        for (int i = 0; i < ea.size(); i++) {
                            JsonElement ee = ea.get(i);
                            if (ee.isJsonPrimitive()) {
                                // last cell is ellipsis if too many columns
                                if (col == colMax-1 && ncols > maxDataCols) {
                                    pw.print("  <td>...</td>");
                                }
                                else {
                                    pw.printf("  <td align=\"left\">%s</td>\n", ee.getAsString());
                                }
                            }
                        }

                    } 
                    else {
                        pw.printf("  <td align=\"left\" colspan=\"%d\">%s</td>\n", colMax, e.getAsString());
                    }
                }
                pw.print(" </tr>\n");
                row++;
            }
            pw.print("</table></font>\n");
            pw.print(">];\n");
        }
        // write the graphviz links
        List<JsonObject>obj = new ArrayList<>();
        for (Map.Entry<String,JsonElement>pair: node.entrySet()) {
            String pred   = pair.getKey();
            JsonElement e = pair.getValue();
            if (!pred.startsWith("@")) {
                if (e.isJsonArray()) {
                    JsonArray ea = e.getAsJsonArray();
                    for (int i = 0; i < ea.size(); i++) {
                        JsonElement ee = ea.get(i);
                        if (ee.isJsonObject()) {
                            JsonObject eo = ee.getAsJsonObject();
                            obj.add(eo);
                            String objID = genNodeID(eo);
                            pw.printf("\"%s\" -> \"%s\" [label=\"%s\"];\n", nodeID, objID, pred);                         
                        }
                    }
                }
                else if (e.isJsonObject()) {
                    JsonObject eo = e.getAsJsonObject();
                    obj.add(eo);
                    String objID = genNodeID(eo);
                    pw.printf("\"%s\" -> \"%s\" [label=\"%s\"];\n", nodeID, objID, pred);
                }
            }
        }
        // write the linked nodes
        for (JsonObject eo : obj) {
            writeNode(pw, eo);
        }     
    }
    
    private int countDataCells (JsonArray ea) {
        int ct = 0;
        for (int i = 0; i < ea.size(); i++) {
            if (ea.get(i).isJsonPrimitive()) { ct++; }
        }
        return ct;
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
