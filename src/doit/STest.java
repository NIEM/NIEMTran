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

import java.io.FileNotFoundException;
import java.io.IOException;
import org.xml.sax.SAXException;

/**
 *
 * @author Scott Renner 
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */
public class STest {
       
    public static void main (String[] args) throws FileNotFoundException, SAXException, IOException {
        STest obj = new STest();
        obj.run(args);
    }
    
    void run (String[] args) throws FileNotFoundException, SAXException, IOException {
        
        String s = "";
        s = s.trim();
        String[] vals = s.split("\\s+");
        System.out.println("length= " + vals.length);
        for (int i = 0; i < vals.length; i++) {
            System.out.println(String.format("v[%s] = '%s'", i, vals[i]));
        }
    }
}
