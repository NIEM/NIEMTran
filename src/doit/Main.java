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
package doit;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import niemtool.NTSchema;
import org.apache.xerces.xs.XSModel;


/**
 *
 * @author Scott Renner 
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */
public class Main {
    
    public static void main (String[] args) {
        
               
        List<String> catalogs = Arrays.asList("xml-catalog.xml");
        List<String> schemas  = Arrays.asList("extension/CrashDriver.xsd");        
        //List<String> schemas  = Arrays.asList("http://example.com/CrashDriver/1.0/");            
        
        NTSchema sc = null;
        try {
            sc = new NTSchema(catalogs,schemas);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(2);
        }
        
        System.out.println("initial catalogs:");
        for (String s : sc.getCatalogFiles()) {
            System.out.println("  "+s);
        }
        System.out.println("\nall catalog files:");
        for (String s : sc.getAllCatalogFiles()) {
            System.out.println("  "+s);
        }
        System.out.println("\nall valid catalog files:");
        for (String s : sc.getAllValidCatalogFiles()) {
            System.out.println("  "+s);
        }
        System.out.println("\ncatalog validation results:");
        for (String s : sc.resolver().validationResults()) {
            System.out.print("  "+s);
        }
        System.out.println("\ninitial schemas:");
        for (String s : sc.getSchemaFiles()) {
            System.out.println("  "+s);
        }
        System.out.println("\ninitial namespaces:");
        for (String s : sc.getSchemaNSURIs()) {
            System.out.println("  "+s);
        }   
        if (!"".equals(sc.initializationErrorMessages())) {
            System.out.println("\ninitializtion errors");
            System.out.print(sc.initializationErrorMessages());
        }
           
        System.out.println("\nschema root directory:" + sc.schemaRootDirectory());
        
        System.out.println("\nschema assembly load records:");
        for (NTSchema.LoadRec r : sc.assemblyList())  {
            System.out.println(r.toString());
        }
             
        System.out.println("\nschema documents assembled:");
        List<String> dl = sc.assembledSchemaDocuments();
        Collections.sort(dl);
        for (String s : dl) {
            System.out.println("  "+s);
        }
        System.out.println("\nschema assembly load messages:");
        System.out.print(sc.assemblyMessages());
        System.out.println("\nschema assembly error messages:");
        System.out.print(sc.assemblyErrorMessages());
        
        XSModel xs = sc.xsmodel();
        System.out.println("\nschema construction " + (xs == null ? "FAILED" : "OK"));
        System.out.println("schema construction messages:");
        System.out.print(sc.xsErrorMessages());
        
        System.out.println("\nCatalog resolver calls during schema construction:");
        System.out.print(sc.resolver().resolutionMessages());
    }
}
