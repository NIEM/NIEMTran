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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * A class to represent mappings between namespace URIs and the prefix string
 * to be used in a JSON-LD context.
 * @author Scott Renner <sar@mitre.org>
 */
public class NamespaceMap {
    
    private final HashMap<String,String> nsmap;
    private final HashSet<String> assigned;
    private final HashSet<String> mapUsed;
    
    public NamespaceMap () {
        nsmap    = new HashMap<>();
        assigned = new HashSet<>();
        mapUsed  = new HashSet<>();
    }
    
    public NamespaceMap (NamespaceMap orig) {
        nsmap    = (HashMap)orig.nsmap.clone();
        assigned = (HashSet)orig.assigned.clone();
        mapUsed  = (HashSet)orig.mapUsed.clone();
    }
    
    public NamespaceMap (HashMap<String,String> mappings) {
        nsmap = (HashMap)mappings.clone();
        assigned = new HashSet<>();
        mapUsed  = new HashSet<>();
        nsmap.forEach((u,p) -> {
            assigned.add(p);
        });
    }
    
    String getPrefix (String uri) {
        mapUsed.add(uri);
        return nsmap.get(uri);
    }
    
    public void assignPrefix (String uri, String prefix) {
        if (!prefix.equals(nsmap.getOrDefault(uri, ""))) {
            if (assigned.contains(prefix)) {
                String base = prefix;
                int ct = 1;
                do {
                    prefix = String.format("%s_%d", base, ct++);
                } while (assigned.contains(prefix));
            }
            assigned.add(prefix);
            nsmap.put(uri, prefix);
        }
    }
    
    public void assignPrefix (String uri, List<String> prefixes) {
        for (String ps : prefixes) {
            if (!assigned.contains(ps)) {
                assignPrefix(uri, ps);
                return;
            }
        }
        String ps = prefixes.get(0);
        if (ps != null) {
            assignPrefix(uri, ps);
        }
    }
    
    public Map<String,String> nsmap () {
        return nsmap;
    }
}
