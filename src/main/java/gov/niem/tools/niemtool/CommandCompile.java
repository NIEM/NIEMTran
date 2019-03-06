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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static gov.niem.tools.niemtool.NTSchema.uriToFile;
import org.apache.commons.io.FilenameUtils;
import org.apache.xerces.xs.XSModel;

/**
 *
 * @author Scott Renner <sar@mitre.org>
 */    

@Parameters(commandDescription = "compile a NIEM schema for the NIEM translator")
public class CommandCompile implements JCCommand {
    
    @Parameter(names = "-o", description = "file for compiler output")
    private String objFile = "";
   
    @Parameter(names = "-s", description = "filename separator character")
    private String fsep = ",";   
    
    @Parameter(names = "-q", description = "no output, exit status only")
    private boolean quiet = false;
   
    @Parameter(names = {"-h","--help"}, description = "display this usage message", help = true)
    boolean help = false;
        
    @Parameter(description = "[xmlCatalog[,...]] schemaOrNamespace[,...]")
    private List<String> mainArgs;
    
    CommandCompile () {
    }
  
    CommandCompile (JCommander jc) {
    }

    public static void main (String[] args) {       
        CommandCompile obj = new CommandCompile();
        obj.runMain(args);
    }
    
    @Override
    public void runMain (String[] args) {
        JCommander jc = new JCommander(this);
        NTUsageFormatter uf = new NTUsageFormatter(jc); 
        jc.setUsageFormatter(uf);
        jc.setProgramName("compile");
        jc.parse(args);
        run(jc);
    }
    
    @Override
    public void runCommand (JCommander cob) {
        cob.setProgramName("niemtool compile");
        run(cob);
    }
    
    private void run (JCommander cob) {
        
        ArrayList<String> catalogs = new ArrayList<>();
        ArrayList<String> schemas  = new ArrayList<>();
        
        if (help) {
            cob.usage();
            System.exit(0);
        }
        if (mainArgs == null || mainArgs.size() < 1) {
            cob.usage();
            System.exit(1);
        }
        if (mainArgs.get(0).startsWith("-s") && mainArgs.get(0).length() == 3) {
            String s = mainArgs.remove(0);
            fsep = s.substring(2);
        }
        if (mainArgs.size() < 0) {
            cob.usage();
            System.exit(1);            
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
        if (mainArgs.size() > 1) {
            String catstr = mainArgs.remove(0);
            catalogs.addAll(Arrays.asList(catstr.split(fsep)));
        }
        for (String s : mainArgs) {
            schemas.addAll(Arrays.asList(s.split(fsep)));
        }
        
        // Initialize schema object; creates list of all initial schema documents
        NTCompiledSchema sc = null;
        try {
            sc = new NTCompiledSchema(catalogs, schemas);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            System.exit(2);
        }
        if (sc.initializationErrorMessages().size() > 0) {
            System.out.println("initialization error");
            for (String s : sc.initializationErrorMessages()) {
                System.out.print("  "+s);
            }
            System.exit(1);
        }
        
        // Arguments processed; determine file for compiled output, if not specified
        if ("".equals(objFile)) {
            List<String> schemaURIs = sc.getAllInitialSchemaURIs();
            if (schemaURIs.size() > 0) {
                File sf  = uriToFile(schemaURIs.get(0));
                Path sfp = sf.toPath().getFileName();
                String sfbase = FilenameUtils.getBaseName(sfp.toString());
                objFile = sfbase + ".no";
            }
            else {
                objFile = "NIEM.no";
            }
        }

        // Make sure output file is writable
        File of = new File(objFile);
        FileWriter ofw = null;
        try {
            ofw = new FileWriter(of);
        } catch (IOException ex) {
            if (!quiet) {
                System.out.println("can't write to compiled object file ");
            }
            System.exit(1);
        }
        
        // Construct the XML schema object
        XSModel xs = sc.xsmodel();
        if (xs == null) {
            if (!quiet) {
                System.out.println("Schema construction: FAILED");
                System.out.print(sc.xsConstructionMessages());
            }
            System.exit(1);
        }
        
        // Compile the schema into the NTSchemaModel object
        NTSchemaModel model = sc.ntmodel();
        model.namespaceVersion().forEach((ns,ver) -> {
            if ("".equals(ver)) {
                System.out.println("[warn] schema includes external namespace " + ns);
            }
        });
        model.namespaceVersion().forEach((ns,ver) -> {
            if (!"".equals(ver) && "4.0".compareTo(ver) > 0) {
                System.out.println(String.format("namespace %s has NIEM version %s (below 4.0)", ns, ver));
            }
        });
        
         // Write compiled object to file
        try {
            ofw.write(model.toJson());
            ofw.close();
        } catch (IOException ex) {
            System.out.println("could not write compiled schema to " + objFile + " : " + ex.getMessage());
            System.exit(1);
        }
               
        System.exit(0);
    }
};
