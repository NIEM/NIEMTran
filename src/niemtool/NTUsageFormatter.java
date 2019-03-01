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

import static com.beust.jcommander.DefaultUsageFormatter.s;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterDescription;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.Strings;
import com.beust.jcommander.UnixStyleUsageFormatter;
import com.beust.jcommander.WrappedParameter;
import static java.lang.Math.max;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Scott Renner <sar@mitre.org>
 */
public class NTUsageFormatter extends UnixStyleUsageFormatter {
    
    private JCommander commander = null;
    
    public NTUsageFormatter(JCommander commander) {
        super(commander);
        this.commander = commander;
    }
    
    /**
     * Appends the details of all commands to the argument string builder, indenting every line with
     * <tt>indentCount</tt>-many <tt>indent</tt>. The commands are obtained from calling
     * {@link JCommander#getRawCommands()} and the commands are resolved using
     * {@link JCommander#findCommandByAlias(String)} on the underlying commander instance.
     *
     * @param out the builder to append to
     * @param indentCount the amount of indentation to apply
     * @param descriptionIndent the indentation for the description
     * @param indent the indentation
     */
    @Override
    public void appendCommands(StringBuilder out, int indentCount, int descriptionIndent, String indent) {
        out.append(indent).append("Commands:\n");

        int longest = 0;
        for (Map.Entry<JCommander.ProgramName, JCommander> commands : commander.getRawCommands().entrySet()) {
            Object arg = commands.getValue().getObjects().get(0);
            Parameters p = arg.getClass().getAnnotation(Parameters.class);

            if (p == null || !p.hidden()) {
                JCommander.ProgramName progName = commands.getKey();
                String dispName = progName.getDisplayName();
                longest = max(longest, dispName.length());
            }
        }
        
        // The magic value 3 is the number of spaces between the name of the option and its description
        for (Map.Entry<JCommander.ProgramName, JCommander> commands : commander.getRawCommands().entrySet()) {
            Object arg = commands.getValue().getObjects().get(0);
            Parameters p = arg.getClass().getAnnotation(Parameters.class);

            if (p == null || !p.hidden()) {
                JCommander.ProgramName progName = commands.getKey();
                String dispName = progName.getDisplayName();
                String description = indent + s(4) + dispName + s(longest+2-dispName.length()) + getCommandDescription(progName.getName());
                wrapDescription(out, indentCount + descriptionIndent, description);
                out.append("\n");

                // Options for this command
                //JCommander jc = commander.findCommandByAlias(progName.getName());
                //jc.getUsageFormatter().usage(out, indent + s(6));
                //out.append("\n");
            }
        }
    }    
    /**
     * Appends the details of all parameters in the given order to the argument string builder, indenting every
     * line with <tt>indentCount</tt>-many <tt>indent</tt>.
     *
     * @param out the builder to append to
     * @param indentCount the amount of indentation to apply
     * @param indent the indentation
     * @param sortedParameters the parameters to append to the builder
     */
    @Override
    public void appendAllParametersDetails(StringBuilder out, int indentCount, String indent,
            List<ParameterDescription> sortedParameters) {
        if (sortedParameters.size() > 0) {
            out.append(indent).append("Options:\n");
        }

        // Calculate prefix indent
        int prefixIndent = 0;
        boolean parmRequiredFlag = false;
        for (ParameterDescription pd : sortedParameters) {
            WrappedParameter parameter = pd.getParameter();
            if (parameter.required()) {
                parmRequiredFlag = true;
            }
            prefixIndent = max(prefixIndent,pd.getNames().length());
        }
        if (parmRequiredFlag) {
            prefixIndent += 2;
        }

        // Append parameters
        for (ParameterDescription pd : sortedParameters) {
            WrappedParameter parameter = pd.getParameter();
            String preq = (!parmRequiredFlag ? "" : (parameter.required() ? "* " : "  "));
            String prefix = preq + pd.getNames();
            out.append(indent)
                    .append("  ")
                    .append(prefix)
                    .append(s(prefixIndent - prefix.length() +1))
                    .append(" ");
            final int initialLinePrefixLength = indent.length() + prefixIndent + 3;

            // Generate description
            String description = pd.getDescription();
            Object def = pd.getDefault();

            if (pd.isDynamicParameter()) {
                String syntax = "(syntax: " + parameter.names()[0] + "key" + parameter.getAssignment() + "value)";
                description += (description.length() == 0 ? "" : " ") + syntax;
            }

            String dc = def.getClass().getName();
            if (def != null && !"java.lang.Boolean".equals(dc) && !pd.isHelp()) {
                if (!Strings.isStringEmpty(def.toString())) {
                    //String displayedDef = Strings.isStringEmpty(def.toString()) ? "<empty string>" : def.toString();
                    String displayedDef = def.toString();
                    String defaultText = "(default: " + (parameter.password() ? "********" : displayedDef) + ")";
                    description += (description.length() == 0 ? "" : " ") + defaultText;
                }
            }
            Class<?> type = pd.getParameterized().getType();

            if (type.isEnum()) {
                String valueList = EnumSet.allOf((Class<? extends Enum>) type).toString();

                // Prevent duplicate values list, since it is set as 'Options: [values]' if the description
                // of an enum field is empty in ParameterDescription#init(..)
                if (!description.contains("Options: " + valueList)) {
                    String possibleValues = "(values: " + valueList + ")";
                    description += (description.length() == 0 ? "" : " ") + possibleValues;
                }
            }

            // Append description
            // The magic value 3 is the number of spaces between the name of the option and its description
            // in DefaultUsageFormatter#appendCommands(..)
            wrapDescription(out, indentCount + prefixIndent - 3, initialLinePrefixLength, description);
            out.append("\n");
        }
    }
}    
