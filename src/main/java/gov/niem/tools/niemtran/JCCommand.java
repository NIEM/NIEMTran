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
 */

package gov.niem.tools.niemtran;

import com.beust.jcommander.JCommander;

/**
 * An interface for a command-line sub-program using JCommander to parse its
 * arguments. Implemented by CommandCheck, CommandCompile, etc.
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */
public interface JCCommand {
       
    public void runMain (String[] args);
    public void runCommand (JCommander cob);

}

