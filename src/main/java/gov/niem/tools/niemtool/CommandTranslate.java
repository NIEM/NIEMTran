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
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
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
        Translate trans = new Translate(model);
        JsonObject data = new JsonObject();
        JsonObject cxt  = new JsonObject();
        int rflag = -1;
        try {
            rflag = trans.xml2json(inST, data, cxt);
        } catch (IOException ex) {
            Logger.getLogger(CommandTranslate.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SAXException ex) {
            Logger.getLogger(CommandTranslate.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(CommandTranslate.class.getName()).log(Level.SEVERE, null, ex);
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println("translate returns "+rflag);
        System.out.println("data:");
        System.out.println(gson.toJson(data));
        System.out.println("context:");
        System.out.println(gson.toJson(cxt));
        
    }
}

