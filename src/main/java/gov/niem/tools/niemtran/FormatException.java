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

/**
 * A kind of exception thrown when a resource load fails because of I/O
 * or parsing error.
 * @author Scott Renner
 * <a href="mailto:sar@mitre.org">sar@mitre.org</a>
 */

public class FormatException extends Exception {

    FormatException(String msg) {
        super(msg);
    }

    FormatException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
