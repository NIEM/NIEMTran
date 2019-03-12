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
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.rules.TemporaryFolder;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 *
 * @author Scott Renner <sar@mitre.org>
 */
public class NTSchemaTest {
    
    public NTSchemaTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void testCatalogSchemalocMismatch () {
        testSchemaLoad("/catalog-schemaloc-mismatch");
    }
   
    @Test
    public void testCorrect () {
        testSchemaLoad("/correct");
    }

    @Test
    public void testInclude () {
        testSchemaLoad("/include");
    }
        
    @Test
    public void testInvalidCatalog () {
        testSchemaLoad("/invalid-catalog");
    }
        
    @Test
    public void testInvalidSchema1 () {
        testSchemaLoad("/invalid-schema-1");
    }
    
    @Test
    public void testInvalidSchema2 () {
        testSchemaLoad("/invalid-schema-2");
    }
    
    @Test
    public void testMissingCatalog () {
        testSchemaLoad("/missing-catalog");
    }
    
    @Test
    public void testMissingCatalogEntry () {
        testSchemaLoad("/missing-catalog-entry");
    }
        
    @Test
    public void testMissingSchema () {
        testSchemaLoad("/missing-schema");
    }  
    
    @Test
    public void testNonlocalCatalogEntry () {
        testSchemaLoad("/nonlocal-catalog-entry");
    }

    public void testSchemaLoad (String resource) {
        URL durl  = this.getClass().getResource(resource);
        File dir  = new File(durl.getFile());
        File edir = new File(dir, "extension");
        IOFileFilter dfilter = new RegexFileFilter("^.*catalog.xml$");
        IOFileFilter efilter = new SuffixFileFilter(".xsd");        
        File expected = new File(dir, "NTSchema-ex.txt");
        try {
            File out = new File(dir, "NTSchema-out.txt");
            NTSchema s = new NTSchema();
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
            s.testOutput(out);            
            assertTrue(FileUtils.contentEquals(expected, out));
            out.delete();
        } catch (IOException ex) {
            Logger.getLogger(NTSchemaTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(NTSchemaTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
