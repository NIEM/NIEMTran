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
package gov.niem.tools.niemtran;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.PrintWriter;
import static java.lang.Math.max;
import static java.lang.Math.min;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author SAR
 */
public class NJ2Graphviz {
    private JsonObject data;    
    private JsonObject cxt = null;
    
    private Map<JsonObject,Integer> nodeNum = null;
    private Set<String> nodesDone = null;
    private int nodeCt = 0;
    private int nodeDigits;
    
    NJ2Graphviz (JsonObject obj) {
        this(obj, null);
    }
        
    NJ2Graphviz (JsonObject data, JsonObject cxt) {
        this.cxt     = cxt;
        this.data    = data;
    }
    
    /**
     * Write the graphviz syntax to the provided writer.
     */
    public void graphviz (PrintWriter pw) {
    
        // Extract context object if not provided separately
        if (cxt == null) {
            cxt = data.get("@context").getAsJsonObject();
        }
        // Write the graphviz header
        pw.print("digraph G {\n" +
                 "node  [fontname=\"Helvetica\", fontsize=\"9\", margin=0, shape=circle, label=\"\"];\n" +
                 "edge  [fontname=\"Helvetica\", fontsize=\"9\"];\n" +
                 "graph [fontname=\"Helvetica\", fontsize=\"9\"];\n");
        
        nodeNum   = new HashMap<>();
        nodesDone = new HashSet<>();
        
        // Walk the tree depth-first and assign node identifiers
        assignID(data);
        nodeDigits = (nodeCt < 10 ? 1 : (nodeCt < 100 ? 2 : (nodeCt < 1000 ? 3 : 4)));

        // Walk the tree again, writing the nodes and links, and we're done.
        writeObj(pw, data);
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
    
    private void writeObj (PrintWriter pw, JsonObject obj) {
        
        // Objects with an @id, and objects that have pairs with a primitive value,
        // are depicted by a table. Other nodes by a circle with the node number.
        // Pairs with primitive values appear as a two-column (key,value) row.
        // Primitive array values appear in multiple columns. There is a maximum 
        // column count to keep things from getting too crazy.
        // There is also a maximum row count.
        // Object values always appear as a link.
        
        String nodeID = genNodeID(obj);
        if (nodesDone.contains(nodeID)) {
            return;
        }

        // Remember the table rows.  Determine the maximum # of columns.
        List<String> dataRowKeys = new ArrayList<>();
        int tableCols = 1;
        boolean skip = true;
        
        // Root node gets an @id table entry even if root object doesn't have that key.
        // The @id row is always first.
        if (obj == data || obj.has("@id")) {
             dataRowKeys.add("@id");      
        }
        // Remember keys with primitive value, or an array with a primitive.
        // These are the data rows.
        for (Map.Entry<String,JsonElement>pair : obj.entrySet()) {
            String key = pair.getKey();
            JsonElement val = pair.getValue();
            if (!key.startsWith("@")) {     // already handled @id
                skip = false;
                if (val.isJsonArray()) {
                    JsonArray ea = val.getAsJsonArray();
                    int ncols = countPrimitiveCells(ea);
                    if (ncols > 0) { 
                        dataRowKeys.add(key); 
                        tableCols = max(tableCols, ncols);
                    }
                }
                else if (!val.isJsonObject()) {
                    dataRowKeys.add(key);
                }
            }
        }
        if (skip) { return; }   // nothing but non-@id "@" keys in this object
        
        final int maxDataRows = 10;     // constants
        final int maxDataCols = 5;      // maybe parameters some day?
        
        // Handle nodes with no @id and no primitive values         
        if (dataRowKeys.isEmpty()) {
            pw.printf("\"%s\" [label=\"%s\"];\n", nodeID, nodeID);
        }
        // Generate an HTML table for the other nodes
        else {
            int colMax = min(tableCols, maxDataCols);
            int rowMax = min(dataRowKeys.size(), maxDataRows);
            String colspanStr = "";
            if (colMax > 1) {
              colspanStr = String.format(" colspan=\"%d\"", colMax-1);
            }
            pw.printf("\"%s\" [shape=plain, label=<\n", nodeID);
            pw.print("<table border=\"0\" cellborder=\"1\" cellspacing=\"0\">\n");
            
            // Generate table rows
            for (int row = 0; row < rowMax; row++) {
                String key = dataRowKeys.get(row);
                JsonElement val = obj.get(key);
                if (val == null) {
                    val = new JsonPrimitive("message");
                }
                pw.print(" <tr>\n");
                
                // Print the node ID in a table-spanning column 0
                if (row == 0 && !"@id".equals(key)) {
                    pw.printf("  <td rowspan=\"%d\">%s</td>\n", rowMax, nodeID);
                }
                
                // Print the key in the first column
                pw.printf("  <td align=\"right\">%s</td>\n", key);
                
                // Primitive value? Print in column #2 (spanning rest of row)
                if (val.isJsonPrimitive()) {
                    pw.printf("  <td%s>%s</td>\n", colspanStr, val.getAsString());
                }
                // An array? Print primitive values in separate columns
                else {
                    JsonArray ea = val.getAsJsonArray();
                    int ncols = countPrimitiveCells(ea);
                    int col = 1;
                    for (int i = 0; i < ea.size(); i++) {
                        JsonElement ce = ea.get(i);
                        if (ce.isJsonPrimitive()) {
                            // Last cell is ellipsis if too many columns
                            if (col >= colMax && ncols > maxDataCols) {
                                pw.print("  <td>...</td>\n");
                            }
                            // Last cell otherwise spans rest of table
                            else if (col >= ncols && col < colMax) {
                                pw.printf("  <td colspan=\"%d\">%s</td>\n", colMax-col, ce.getAsString());
                            }
                            else {
                                pw.printf("  <td>%s</td>\n", ce.getAsString());
                            }
                            col++;
                        }
                    }
                }
                // Objects are handled elsewhere; ignore them here
                pw.print(" </tr>\n");
            }
            pw.print("</table>\n");
            pw.print(">];\n");
        }
        nodesDone.add(nodeID);
        
        // Handled the node, now write the graphviz links to object nodes
        List<JsonObject>childObjs = new ArrayList<>();
        for (Map.Entry<String,JsonElement>pair : obj.entrySet()) {
            String pred   = pair.getKey();
            JsonElement e = pair.getValue();
            if (!pred.startsWith("@")) {
                if (e.isJsonArray()) {
                    JsonArray ea = e.getAsJsonArray();
                    for (int i = 0; i < ea.size(); i++) {
                        JsonElement ee = ea.get(i);
                        if (ee.isJsonObject()) {
                            JsonObject eo = ee.getAsJsonObject();
                            childObjs.add(eo);
                            String objID = genNodeID(eo);
                            pw.printf("\"%s\" -> \"%s\" [label=\"%s\"];\n", nodeID, objID, pred);                         
                        }
                    }
                }
                else if (e.isJsonObject()) {
                    JsonObject eo = e.getAsJsonObject();
                    childObjs.add(eo);
                    String objID = genNodeID(eo);
                    pw.printf("\"%s\" -> \"%s\" [label=\"%s\"];\n", nodeID, objID, pred);
                }
            }
        }
        // Now process the linked nodes (depth-first)
        for (JsonObject eo : childObjs) {
             writeObj(pw, eo);
         }     
    }
    
    private static int countPrimitiveCells (JsonArray ea) {
        int ct = 0;
        for (int i = 0; i < ea.size(); i++) {
            if (ea.get(i).isJsonPrimitive()) { ct++; }
        }
        return ct;
    }
    
    private String genNodeID (JsonObject obj) {
        String nodeID;
        if (obj == data) {
            nodeID = "message";
        }
        else if (obj.has("@id")) {
            nodeID = obj.get("@id").getAsString();
        }
        else {
            nodeID = String.format("%0"+nodeDigits+"d", nodeNum.get(obj));
        }
        return nodeID;
    }    
}
