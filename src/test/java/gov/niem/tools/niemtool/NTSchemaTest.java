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
import java.net.URL;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.Test;
import static org.junit.Assert.*;

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

    /**
     * Test of addCatalogFile method, of class NTSchema.
     */
    @Test
    @DisplayName("Correct schema")
    public void testCorrectSchema() throws ParserConfigurationException {
        URL dir = this.getClass().getResource("/correct");
        File df = new File(dir.getFile());
        File catalog = new File(df,"xml-catalog.xml");
        File schema  = new File(df,"extension/CrashDriver.xsd");
        NTSchema s = new NTSchema();
        s.addCatalogFile(catalog.getPath());
        s.addSchemaFile(schema.getPath());
        assert(s.initializationErrorMessages().isEmpty());
        assert(s.assemblyMessages().isEmpty());
        assert(s.xsConstructionMessages().isEmpty());
        assertNotNull(s.xsmodel());
    }

    
}
