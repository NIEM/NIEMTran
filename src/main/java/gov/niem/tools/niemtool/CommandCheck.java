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

@Parameters(commandDescription = "check XML schema for assembly errors")
public class CommandCheck implements JCCommand {
   
    @Parameter(names = "-s", description = "filename separator character")
    private String fsep = ",";
    
    @Parameter(names = "-i", description = "continue checking after initialization or assembly warnings")
    private boolean ignore = false;
    
    @Parameter(names = "-n", description = "don't report NIEM-specific warnings")
    private boolean noNIEM = false;
   
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
        NTCheckedSchema sc = null;
        try {
            sc = new NTCheckedSchema(catalogs, schemas);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            System.exit(2);
        }
        
        // Initialization checking
        boolean schemasFound = (sc.getAllInitialSchemas().size() > 0);
        if (verbose) {
            System.out.println("== Schema initialization ==");
            printMessages("Catalog validation results:", sc.resolver().validationResults());
            printItems("Initial schema documents:", sc.getAllInitialSchemas());
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
                printMessages("Schema initialization errors:", initErrs);
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
            printMessages("Schema assembly log messages:", sc.assemblyLogMessages());
        }
        if (!sc.assemblyWarningMessages().isEmpty()) {
            if (!quiet && !verbose) {
                System.out.println("Schema root directory: " + schemaRoot);                
                printMessages("Schema assembly messages:", sc.assemblyWarningMessages());
            }
            if (!ignore) {
                System.exit(1);
            }
        }
        // Schema construction
        XSModel xs = sc.xsmodel();
        if (verbose) {
            System.out.println("== Schema construction ==");
            System.out.println(xs == null ? "Schema contruction: FAILED" : "Schema construction: OK");
            printMessages("Schema construction messages:", sc.xsConstructionMessages());
            printMessages("Schema warnings:", sc.xsWarningMessages());
            if (!noNIEM) { printMessages("Schema NIEM warnings:", sc.xsNIEMWarningMessages()); }
            printMessages("Namespaces constructed:", sc.xsNamespaceList());
            printMessages("Catalog resolutions:", sc.xsResolutionMessages());
        }
        else if (!quiet) {
            if (sc.assemblyWarningMessages().isEmpty()) {
                System.out.println("Schema root directory: " + schemaRoot);                
            }
            System.out.println(xs == null ? "Schema contruction: FAILED" : "Schema construction: OK");
            printMessages("Schema construction messages:", sc.xsConstructionMessages());
            printMessages("Schema warnings:", sc.xsWarningMessages());
            if (!noNIEM) { printMessages("Schema NIEM warnings:", sc.xsNIEMWarningMessages()); }            
        }
        if (xs == null) {
            System.exit(1);
        }
     }
       
    private void printMessages (String header, List<String> msgs) {
        if (msgs.isEmpty() && !verbose) {
            return;
        }
        System.out.println(header);
        for (String s : msgs) {
            System.out.print("  " + s);
        }
    }
 
    private void printItems (String header, List<String> msgs) {
        if (msgs.isEmpty() && !verbose) {
            return;
        }
        System.out.println(header);
        for (String s : msgs) {
            System.out.println("  " + s);
        }
    }
}    


