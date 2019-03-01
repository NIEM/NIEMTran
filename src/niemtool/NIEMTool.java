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
package niemtool;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Scott Renner <sar@mitre.org>
 */
public class NIEMTool {
    
    public static void main (String[] args) {
        NIEMTool obj = new NIEMTool();
        obj.run(args);
    }
    
    private void run (String[] args) {
        args = Arrays.asList(
                "check", "-v", "-i", "xml-catalog.xml", "extension/CrashDriver.xsd"
                
                //"help"
                //"check", "-s;", "cat1.xml;cat2.xml", "s1.xsd;s2.xsd"
                //"check", "-w", "-i", "xml-catalog.xml", "extension/CrashDriver.xsd"
                //"compile", "xml-catalog.xml", "http://example.com/CrashDriver/1.0/"
                //"translate", "CrashDriver.no", "../iep-samples/iep2.xml"
                ).toArray(new String[0]);
         
        JCommander jc = new JCommander();
        NTUsageFormatter uf = new NTUsageFormatter(jc); 
        jc.setUsageFormatter(uf);
        jc.setProgramName("niemtool");
        
        CommandCheck checkCmd         = new CommandCheck(jc);
        CommandCompile compileCmd     = new CommandCompile(jc);
        CommandTranslate translateCmd = new CommandTranslate(jc);
        CommandHelp helpCmd           = new CommandHelp(jc);        
        jc.addCommand("check", checkCmd);
        jc.addCommand("compile", compileCmd);
        jc.addCommand("translate", translateCmd);
        jc.addCommand("help", helpCmd, "usage");
        
        if (args.length < 1) {
            jc.usage();
            System.exit(2);
        }
        try {
            jc.parse(args);
        }
        catch (Exception ex) {
            jc.usage();
            System.exit(2);
        }
        String command = jc.getParsedCommand();      
        Map<String,JCommander> cmdMap = jc.getCommands();
        JCommander cob = cmdMap.get(command);
        List<Object> objs = cob.getObjects();
        JCCommand cmd = (JCCommand)objs.get(0);
        NTUsageFormatter cobuf = new NTUsageFormatter(cob);        
        cob.setUsageFormatter(cobuf);
        cmd.runCommand(cob);               
    }
    
    @Parameters(commandDescription = "list of niemtool commands")
    private class CommandHelp implements JCCommand {
        
        @Parameter(description = "display help for this command")
        List<String> helpArgs;
        
        private final JCommander jc;
        
        CommandHelp (JCommander jc) {
            this.jc = jc;
        }
        
        @Override
        public void runMain (String[] args) {
            
        }

        @Override
        public void runCommand(JCommander helpOb) {
            if (helpArgs != null && helpArgs.size() > 0) {
                String cmdName = helpArgs.get(0);
                Map<String, JCommander> cmdMap = jc.getCommands();
                JCommander cob = cmdMap.get(cmdName);
                List<Object> objs = cob.getObjects();
                JCCommand cmd = (JCCommand) objs.get(0);  
                NTUsageFormatter cobuf = new NTUsageFormatter(cob);        
                cob.setUsageFormatter(cobuf);    
                String[] ha = {"--help"};
                cmd.runMain(ha);
            }
            else {
                jc.usage();
                System.exit(0);
            }
        }
    }
}
