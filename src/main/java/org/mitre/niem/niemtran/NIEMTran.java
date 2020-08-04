/* 
 * NOTICE
 *
 * This software was produced for the U. S. Government
 * under Basic Contract No. W56KGU-18-D-0004, and is
 * subject to the Rights in Noncommercial Computer Software
 * and Noncommercial Computer Software Documentation
 * Clause 252.227-7014 (FEB 2012)
 * 
 * Copyright 2020 The MITRE Corporation.
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

package org.mitre.niem.niemtran;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import java.util.List;
import java.util.Map;

/**
 * The "niemtran" command-line program.
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class NIEMTran {
    
    public static final String VERSION_ID = "niemtran 1.0.0 (4 August 2020)";
    
    public static void main (String[] args) {
        NIEMTran obj = new NIEMTran();
        obj.run(args);
    }
    
    private void run (String[] args) {
         
        JCommander jc = new JCommander();
        NTUsageFormatter uf = new NTUsageFormatter(jc); 
        jc.setUsageFormatter(uf);
        jc.setProgramName("niemtran");
        
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
    
    @Parameters(commandDescription = "list of niemtran commands")
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
            System.out.println("Version: " + VERSION_ID);
            if (helpArgs != null && !helpArgs.isEmpty()) {
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
