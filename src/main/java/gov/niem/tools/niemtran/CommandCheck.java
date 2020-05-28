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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;

/**
 *
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */    

@Parameters(commandDescription = "check XML schema for assembly errors")
public class CommandCheck implements JCCommand {
   
    @Parameter(names = "-s", description = "filename separator character (e.g. \"-s,\" or \"-s ,\")")
    private String fsep = ",";
    
    @Parameter(names = "-i", description = "continue checking after initialization errors")
    private boolean ignore = false;
   
    @Parameter(names = {"-v"}, description = "verbose output")
    private boolean verbose = false;
    
    @Parameter(names = {"-h","--help"}, description = "display this usage message", help = true)
    boolean help = false;
     
    @Parameter(description = "[xmlCatalog[,...]] schemaOrNamespace[,...]")
    private List<String> mainArgs;
    
    String schemaRoot = "";
    int schemaRootLength = 0;
    
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
        cob.setProgramName("niemtran check");
        run(cob);
    }
    
    private void run (JCommander cob) {
        // A schema is defined by a list of catalog files plus a list of
        // schema documents or namespaces.  Process the arguments for those lists
        ArrayList<String> catalogs = new ArrayList<>();
        ArrayList<String> schemas  = new ArrayList<>();
        
        if (help) {
            cob.usage();
            System.exit(0);
        }
        if (mainArgs == null || mainArgs.isEmpty()) {
            cob.usage();
            System.exit(1);
        }
        // Argument "-s ," will have been processed by JCommander
        // Argument "-s," will be unhandled -- handle it now
        if (mainArgs.get(0).startsWith("-s") && mainArgs.get(0).length() == 3) {
            String s = mainArgs.remove(0);
            fsep = s.substring(2);
        }
        // Any other argument beginning with "-" is an unknown option
        // Except for "--", which is ignored and allows filenames starting with "-"
        String na = mainArgs.get(0);
        if (na.startsWith("-")) {
            if ("--".equals(na)) {
                mainArgs.remove(0);
            } else {
                System.out.println("unknown option: " + na);
                cob.usage();
                System.exit(1);
            }
        } 
        // Must be at least one schema or nsmespsce argument
        if (mainArgs.isEmpty()) {
            cob.usage();
            System.exit(1);            
        }
        // Two or more arguments? Then the first one is XML Catalog(s)
        if (mainArgs.size() > 1) {
            String catstr = mainArgs.remove(0);
            catalogs.addAll(Arrays.asList(catstr.split(fsep)));
        }
        // Everything else is a schema document (list)
        for (String s : mainArgs) {
            schemas.addAll(Arrays.asList(s.split(fsep)));
        }
        // Arguments all handled, time to construct the schema object       
        NTCheckedSchema sc = null;
        try {
            sc = new NTCheckedSchema(catalogs, schemas);
        } catch (ParserConfigurationException ex) {
            System.out.println(ex.getMessage());
            System.exit(2);
        }
        
        // Get the schema root directory. This executes the schema assembly checking, but
        // I want it now so we can print the root directory first thing.
        schemaRoot = sc.schemaRootDirectory();
        schemaRootLength = schemaRoot.length();
        System.out.println("Schema root directory: " + schemaRoot);
        
        // Initialization checking -- tell us about catalog parsing and resolved namespaces
        boolean schemasFound = (sc.getAllInitialSchemas().size() > 0);
        if (verbose) {
            printRelativeMessages("Catalog validation results:", sc.resolver().validationResults());
            printRelativeMessages("Initial schema documents:", sc.getAllInitialSchemas());
        }
        if (!schemasFound) {
            System.out.println("Schema initialization error: no initial schema documents");
            System.exit(1);
        }
        if (!sc.initializationErrorMessages().isEmpty()) {
            printRelativeMessages("Schema initialization errors:", sc.initializationErrorMessages());
            if (!ignore) System.exit(1);
        }
        else System.out.println("Schema initialization: OK");

        // And now tell us about schema assembly checking
        if (verbose) {
            List<String> dl = new ArrayList<>();
            for (String s : sc.assembledSchemaDocuments()) {
                dl.add(s + "\n");
            }
            Collections.sort(dl);
            printRelativeMessages("Schema documents assembled:", dl);
            printMessages("Schema assembly log messages:", sc.assemblyLogMessages());
        }
        else { 
            printMessages("Schema assembly warnings:", sc.assemblyWarningMessages());
        }
        if (sc.assemblyWarnings()) System.out.println("Schema assembly: WARNINGS");
        else System.out.println("Schema assembly: OK");

        // Schema construction
        List<String> xsm = sc.xsConstructionMessages();
        printRelativeMessages("Schema construction messages:", xsm);
        if (verbose) {
            printRelativeMessages("Namespaces constructed by Xerces:", sc.xsNamespaceDocuments());
            printRelativeMessages("Catalog resolutions performed by Xerces:", sc.xsResolutionMessages());
        }
        System.out.println("Schema construction: " + sc.xsConstructionResult());
     }
       
    // If message list not empty, print header and indented list of messages.
    // Don't mess with absolute file URLs relative to schemaRoot.
    private void printMessages (String header, List<String> msgs) {
        if (msgs.isEmpty() && !verbose) {
            return;
        }
        System.out.println(header);
        for (String s : msgs) {
            System.out.print("  " + s);
        }
    }
    
    // If message list not empty, print header and indented list of messages.
    // First occurance of absolute file URL made relevant to schemaRoot in each message.
    private void printRelativeMessages (String header, List<String> msgs) {
        if (msgs.isEmpty() && !verbose) {
            return;
        }
        System.out.println(header);
        for (String s : msgs) {
            System.out.print("  " + s.replace(schemaRoot, ""));
        }
    }
 
}    


