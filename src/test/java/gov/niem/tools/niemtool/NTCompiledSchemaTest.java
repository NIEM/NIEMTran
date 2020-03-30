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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Scott Renner <sar@mitre.org>
 */
public class NTCompiledSchemaTest {
    
    public NTCompiledSchemaTest() {
    }
    
   // @ParameterizedTest would be cool, but it's not working

/*
    
    @Test
    public void testBogusRdf () {
        testModelCreate("/bogus-rdf");
    }
    
    @Test
    public void testCatalogSchemalocMismatch () {
        testModelCreate("/catalog-schemaloc-mismatch");
    }
   
    @Test
    public void testCorrect () {
        testModelCreate("/correct");
    }

    @Test
    public void testNstdPrefix () {
        testModelCreate("/nstd-prefix");
        
    }
    
    @Test
    public void testPrefixMismatch () {
        testModelCreate("/prefix-mismatch");
    }
    
    @Test
    public void testWildcard () {
        testModelCreate("/wildcard");
    }

    public void testModelCreate (String resource) {
        URL durl  = this.getClass().getResource(resource);
        File dir  = new File(durl.getFile());
        File edir = new File(dir, "extension");
        IOFileFilter dfilter = new RegexFileFilter("^.*catalog.xml$");
        IOFileFilter efilter = new SuffixFileFilter(".xsd");        
        File expected = new File(dir, "NTCompiledSchema-ex.txt");
        try {
            File out = new File(dir, "NTCompiledSchema-out.txt");
            NTCompiledSchema s = new NTCompiledSchema();
            Iterator<File> dfiles = FileUtils.iterateFiles(dir, dfilter, null);
            while (dfiles.hasNext()) {
                File f = dfiles.next();
                s.addCatalogFile(f.getPath());
            }
            Iterator<File> efiles = FileUtils.iterateFiles(edir, efilter, null);
            while (efiles.hasNext()) {
                File f = efiles.next();
                s.addSchemaFile(f.getPath());
            }
            NTSchemaModel m = s.ntmodel();
            FileWriter ofw = new FileWriter(out);
            String js = m.toJson();
            ofw.write(m.toJson());
            ofw.close();
            assertTrue(FileUtils.contentEquals(expected, out));
        } catch (IOException ex) {
            Logger.getLogger(NTCheckedSchemaTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(NTCheckedSchemaTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }   
*/    
}
