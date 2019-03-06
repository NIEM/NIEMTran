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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/**
 *
 * @author Scott Renner <sar@mitre.org>
 */
@Parameters(commandDescription = "translate NIEM data from one serialization to another")
public class CommandTranslate implements JCCommand {
    @Parameter(names = "-o", description = "file for translator output")
    private String outputFN = "";
    
    @Parameter(names = {"--x2j"}, description = "translate NIEM XML to NIEM JSON (default)")
    boolean nx2j = true;
    
    @Parameter(names = {"-h","--help"}, description = "display this usage message", help = true)
    boolean help = false;
    
    @Parameter(description = "objectFile inputFile")
    private List<String> mainArgs;
    
    CommandTranslate () {
    }
  
    CommandTranslate (JCommander jc) {
    }

    public static void main (String[] args) {
        
        args = Arrays.asList(
                "CrashDriver.no", "../iep-samples/iep1.xml"
                //"--help"
                //"-j2x"
                ).toArray(new String[0]);
        
        CommandTranslate obj = new CommandTranslate();
        obj.runMain(args);
    }
    
    public void runMain (String[] args) {
        JCommander jc = new JCommander(this);
        NTUsageFormatter uf = new NTUsageFormatter(jc); 
        jc.setUsageFormatter(uf);
        jc.setProgramName("trznslate");
        jc.parse(args);
        run(jc);
    }
    
    @Override
    public void runCommand (JCommander cob) {
        cob.setProgramName("niemtool translate");
        run(cob);
    }
    
    private void run (JCommander cob) { 
        if (help) {
            cob.usage();
            System.exit(0);
        }
        if (mainArgs == null || mainArgs.size() < 1) {
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
        if (mainArgs.size() != 2) {
            cob.usage();
            System.exit(1);
        }

        // Set up input stream for SAX parser
        String inputFN = mainArgs.get(1);      
        BufferedInputStream inST = null;
        try {
            FileInputStream xx = new FileInputStream(inputFN);
            inST = new BufferedInputStream(xx);
        } catch (FileNotFoundException ex) {
            System.err.print(String.format("can't read input file %s: %s\n",
                    inputFN, ex.getMessage()));
            System.exit(1);
        }
        
        // Read the compiled schema model
        NTSchemaModel model = null;        
        String objFN   = mainArgs.get(0);
        try {
            FileReader objRD = new FileReader(objFN);
            model = new NTSchemaModel(objRD);
        } catch (FileNotFoundException ex) {
            System.err.print(String.format("can't read schema model from %s: %s\n",
                    objFN, ex.getMessage()));
            System.exit(1);
        } catch (FormatException ex) {
            System.err.print(String.format("can't read schema model from %s: invalid format\n", objFN));
            System.exit(1);
        }
        
        // Set up output to outputFile or stdout
        PrintWriter outPW = null;
        if ("".equals(outputFN)) {
            outPW = new PrintWriter(System.out);
        }
        else {
            try {
                outPW = new PrintWriter(outputFN);
            } catch (FileNotFoundException ex) {
                System.err.print(String.format("can't write output file %s: %s\n", 
                        outputFN, ex.getMessage()));
                System.exit(1);
            }
        }
        
        // Set up the translator object, collect results, write to output
        NTXMLtoJSON trans = new NTXMLtoJSON(model);
        JsonObject json = null;
        try {
            json = trans.xml2jsonObject(inST);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(CommandTranslate.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SAXException ex) {
            Logger.getLogger(CommandTranslate.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(CommandTranslate.class.getName()).log(Level.SEVERE, null, ex);
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        outPW.println("\noutput!");
        outPW.print(gson.toJson(json));
        outPW.close();
    }
}
