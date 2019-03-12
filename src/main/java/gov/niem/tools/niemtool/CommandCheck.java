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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.xerces.xs.XSModel;

/**
 *
 * @author Scott Renner <sar@mitre.org>
 */    

@Parameters(commandDescription = "check NIEM schema for assembly errors")
public class CommandCheck implements JCCommand {
   
    @Parameter(names = "-s", description = "filename separator character")
    private String fsep = ",";
    
    @Parameter(names = "-i", description = "continue checking after initialization or assembly warnings")
    private boolean ignore = false;
   
    @Parameter(names = {"-v"}, description = "verbose output")
    private boolean verbose = false;
    
    @Parameter(names = "-q", description = "no output, exit status only")
    private boolean quiet = false;
    
    @Parameter(names = {"-h","--help"}, description = "display this usage message", help = true)
    boolean help = false;
     
    @Parameter(description = "[xmlCatalog[,...]] schemaOrNamespace[,...]")
    private List<String> mainArgs;
    
    CommandCheck () {
    }
        
    CommandCheck (JCommander jc) {
    }
    
    public static void main (String[] args) {
        
        CommandCheck obj = new CommandCheck();
        obj.runMain(args);
    }
    
    @Override
    public void runMain (String[] args) {
        JCommander jc = new JCommander(this);
        NTUsageFormatter uf = new NTUsageFormatter(jc); 
        jc.setUsageFormatter(uf);
        jc.setProgramName("check");
        jc.parse(args);
        run(jc);
    }
    
    @Override
    public void runCommand (JCommander cob) {
        cob.setProgramName("niemtool check");
        run(cob);
    }
    
    private void run (JCommander cob) {
        
        ArrayList<String> catalogs = new ArrayList<>();
        ArrayList<String> schemas  = new ArrayList<>();
        
        if (help) {
            cob.usage();
            System.exit(0);
        }
        if (verbose && quiet) {
            System.out.println("Error: -v and -q options are incompatible");
            cob.usage();
            System.exit(1);
        }
        if (mainArgs == null || mainArgs.size() < 1) {
            cob.usage();
            System.exit(1);
        }
        if (mainArgs.get(0).startsWith("-s") && mainArgs.get(0).length() == 3) {
            String s = mainArgs.remove(0);
            fsep = s.substring(2);
        }
        String na = mainArgs.get(0);
        if (na.startsWith("-")) {
            if (na.length() == 1) {
                mainArgs.remove(0);
            } else {
                System.out.println("unknown option: " + na);
                cob.usage();
                System.exit(1);
            }
        }        
        if (mainArgs.size() < 0) {
            cob.usage();
            System.exit(1);            
        }      
        if (mainArgs.size() > 1) {
            String catstr = mainArgs.remove(0);
            catalogs.addAll(Arrays.asList(catstr.split(fsep)));
        }
        for (String s : mainArgs) {
            schemas.addAll(Arrays.asList(s.split(fsep)));
        }
        NTSchema sc = null;
        try {
            sc = new NTSchema(catalogs, schemas);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            System.exit(2);
        }
        
        // Initialization checking
        boolean schemasFound = (sc.getAllInitialSchemaURIs().size() > 0);
        if (verbose) {
            System.out.println("== Schema initialization ==\nCatalog validation results:");
            for (String s : sc.resolver().validationResults()) {
                System.out.print("  " + s);
            }
            if (sc.getAllInitialSchemaURIs().size() > 0) {
                System.out.println("Initial schema documents:");
                for (String s : sc.getAllInitialSchemaURIs()) {
                    System.out.println("  " + s);
                }
            }
        }
        if (!schemasFound) {
            System.out.println("No initial schema documents provided");   
            if (!ignore) {
                System.exit(1);
            }
        }
        List<String> initErrs = sc.initializationErrorMessages();
        if (initErrs.size() > 0) {
            if (!quiet) {
                System.out.println("Schema initialization errors:");
                for (String s : initErrs) {
                    System.out.print("  "+s);
                }
            }
            if (!ignore) {
                System.exit(1);
            }
        }
        // Schema assembly checking
        String schemaRoot = sc.schemaRootDirectory();
        int schemaRootLength = schemaRoot.length();
        if (verbose) {
            System.out.println("== Schema assembly checking ==");
            System.out.println("Schema root directory: " + schemaRoot);
            System.out.println("Schema documents assembled:");
            List<String> dl = sc.assembledSchemaDocuments();
            Collections.sort(dl);
            for (String s : dl) {
                System.out.println("  " + s.substring(schemaRootLength));
            }
            System.out.println("Schema assembly log messages:");
            for (String s : sc.assemblyLogMessages()) {
                System.out.print("  " + s);
            }
        }
        if (sc.assemblyWarningMessages().size() > 0) {
            if (!quiet && !verbose) {
                System.out.println("Schema root directory: " + schemaRoot);                
                System.out.println("Schema assembly messages:");
                for (String s : sc.assemblyWarningMessages()) {
                    System.out.print("  " + s);
                }
            }
            if (!ignore) {
                System.exit(1);
            }
        }
        // Schema construction
        XSModel xs = sc.xsmodel();
        List<String> xsmsgs = sc.xsConstructionMessages();
        if (verbose) {
            System.out.println("== Schema construction ==");
            System.out.println(xs == null ? "Schema contruction: FAILED" : "Schema construction: OK");
            if (xsmsgs.size() > 0) {
                System.out.println("Schema construction messages:");
                for (String s : xsmsgs) {
                    System.out.print("  " + s);
                }
                System.out.print(xsmsgs);
            }
            if (sc.xsNamespaceList().size() > 0) {
                System.out.println("Schema namespaces constructed:");
                for (String s : sc.xsNamespaceList()) {
                    System.out.print("  " + s);
                }
            }
            if (sc.xsResolutionMessages().size() > 0) {
                System.out.println("Catalog resolutions:");
                for (String s : sc.xsResolutionMessages()) {
                    System.out.print("  " + s);
                }
            }
        }
        if (xs == null || xsmsgs.size() > 0) {
            if (!quiet && !verbose) {
                if (sc.assemblyWarningMessages().size() < 1) {
                    System.out.println("Schema root directory= " + schemaRoot);
                }
                if (xs == null) {
                    System.out.println("Schema construction: FAILED");
                }
                System.out.println("Schema construction messages:");
                System.out.print(xsmsgs);
            }
            System.exit(1);
        }
        if (!quiet && !verbose) {
            System.out.println("Schema construction: OK");
        }
     }
}    


