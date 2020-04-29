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

package gov.niem.tools.niemtran;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The "niemtran" command-line program.
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class NIEMTran {
    
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
