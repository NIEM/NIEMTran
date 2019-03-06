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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import junit.framework.Assert;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.rules.TemporaryFolder;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;

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
    
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /**
     * Test of addCatalogFile method, of class NTSchema.
     */
    @Test
    @DisplayName("Correct schema")
    public void testCorrectSchema() throws ParserConfigurationException {
        URL durl = this.getClass().getResource("/correct");
        File dir = new File(durl.getFile());
        File expected = new File(dir, "NTSchema.out");
        File out = null;
        try {
            out = folder.newFile("NTSchema.out");
            NTSchema s = genSchema(dir);
            s.testOutput(out);
            assertEquals(FileUtils.readLines(expected, "utf-8"), FileUtils.readLines(out, "utf-8"));
        } catch (IOException ex) {
            Logger.getLogger(NTSchemaTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private NTSchema genSchema (File dir) {
        File catalog = new File(dir,"xml-catalog.xml");
        File schema  = new File(dir,"extension/CrashDriver.xsd");
        NTSchema s = null;
        try {
            s = new NTSchema();
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(NTSchemaTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        s.addCatalogFile(catalog.getPath());
        s.addSchemaFile(schema.getPath());
        return s;
    }
}
