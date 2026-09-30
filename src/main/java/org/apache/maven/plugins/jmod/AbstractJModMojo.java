/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.jmod;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.Toolchain;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.api.services.ToolchainManager;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.apache.maven.shared.utils.cli.CommandLineUtils;
import org.apache.maven.shared.utils.cli.Commandline;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.StringUtils;

/**
 * This contains the code to handle toolchains and the execution of the command which is similar to code in
 * maven-jlink-plugin (maven-jdeps-plugin?). Later we need to think about a way to combine that code to reduce
 * duplication.
 *
 * @author Karl Heinz Marbaise <a href="mailto:khmarbaise@apache.org">khmarbaise@apache.org</a>
 */
public abstract class AbstractJModMojo implements org.apache.maven.api.plugin.Mojo {

    @Inject
    protected Project project;

    @Inject
    protected Session session;

    @Inject
    protected ToolchainManager toolchainManager;

    @Inject
    protected Log log;

    /**
     * <p>
     * Specify the requirements for this JDK toolchain. This overrules the toolchain selected by the
     * maven-toolchain-plugin.
     * </p>
     */
    @Parameter
    private Map<String, String> jdkToolchain;

    // TODO: Check how to prevent code duplication in maven-jlink, maven-jmod and maven-jdeps plugin?
    protected String getJModExecutable() throws IOException {
        Toolchain tc = getToolchain();

        String jModExecutable = null;
        if (tc != null) {
            jModExecutable = tc.findTool("jmod");
        }

        String jModCommand = "jmod" + (Os.isFamily(Os.FAMILY_WINDOWS) ? ".exe" : "");

        File jModExe;

        if (StringUtils.isNotEmpty(jModExecutable)) {
            jModExe = new File(jModExecutable);

            if (jModExe.isDirectory()) {
                jModExe = new File(jModExe, jModCommand);
            }

            if (Os.isFamily(Os.FAMILY_WINDOWS) && jModExe.getName().indexOf('.') < 0) {
                jModExe = new File(jModExe.getPath() + ".exe");
            }

            if (!jModExe.isFile()) {
                throw new IOException("The jmod executable '" + jModExe + "' doesn't exist or is not a file.");
            }
            return jModExe.getAbsolutePath();
        }

        // ----------------------------------------------------------------------
        // Try to find jmod from System.getProperty( "java.home" )
        // By default, System.getProperty( "java.home" ) = JRE_HOME and JRE_HOME
        // should be in the JDK_HOME
        // ----------------------------------------------------------------------
        jModExe =
                new File(System.getProperty("java.home") + File.separator + ".." + File.separator + "bin", jModCommand);

        // ----------------------------------------------------------------------
        // Try to find jmod from JAVA_HOME environment variable
        // ----------------------------------------------------------------------
        if (!jModExe.exists() || !jModExe.isFile()) {
            Map<String, String> env = System.getenv();
            String javaHome = env.get("JAVA_HOME");
            if (StringUtils.isBlank(javaHome)) {
                throw new IOException("The environment variable JAVA_HOME is not correctly set.");
            }
            if ((!new File(javaHome).getCanonicalFile().exists())
                    || (new File(javaHome).getCanonicalFile().isFile())) {
                throw new IOException("The environment variable JAVA_HOME=" + javaHome
                        + " doesn't exist or is not a valid directory.");
            }

            jModExe = new File(javaHome + File.separator + "bin", jModCommand);
        }

        if (!jModExe.getCanonicalFile().exists() || !jModExe.getCanonicalFile().isFile()) {
            throw new IOException("The jmod executable '" + jModExe
                    + "' doesn't exist or is not a file. Verify the JAVA_HOME environment variable.");
        }

        return jModExe.getAbsolutePath();
    }

    protected boolean projectHasAlreadySetAnArtifact() {
        return session.getService(ProjectManager.class)
                .getPath(project)
                .filter(Files::isRegularFile)
                .isPresent();
    }

    protected void executeCommand(Commandline cmd, Path outputDirectory) throws MojoException {
        if (log.isDebugEnabled()) {
            // no quoted arguments ???
            log.debug(CommandLineUtils.toString(cmd.getCommandline()).replaceAll("'", ""));
        }

        CommandLineUtils.StringStreamConsumer err = new CommandLineUtils.StringStreamConsumer();
        CommandLineUtils.StringStreamConsumer out = new CommandLineUtils.StringStreamConsumer();
        try {
            int exitCode = CommandLineUtils.executeCommandLine(cmd, out, err);

            String output = (StringUtils.isBlank(out.getOutput())
                    ? null
                    : '\n' + out.getOutput().trim());

            if (exitCode != 0) {
                if (StringUtils.isNotEmpty(output)) {
                    // Reconsider to use WARN / ERROR ?
                    log.info(output);
                }

                StringBuilder msg = new StringBuilder("\nExit code: ");
                msg.append(exitCode);
                if (StringUtils.isNotEmpty(err.getOutput())) {
                    msg.append(" - ").append(err.getOutput());
                }
                msg.append('\n');
                msg.append("Command line was: ").append(cmd).append('\n').append('\n');

                throw new MojoException(msg.toString());
            }

            if (StringUtils.isNotEmpty(output)) {
                String[] splitLines = StringUtils.split(output, "\n");
                for (String outputLine : splitLines) {
                    log.info(outputLine);
                }
            }
        } catch (CommandLineException e) {
            throw new MojoException("Unable to execute jmod command: " + e.getMessage(), e);
        }
    }

    /**
     * Convert a list into a.
     *
     * @param modules the list of modules
     * @return the string with the module list which is separated by {@code ,}
     */
    protected String getCommaSeparatedList(List<String> modules) {
        StringBuilder sb = new StringBuilder();
        for (String module : modules) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(module);
        }
        return sb.toString();
    }

    protected Toolchain getToolchain() {
        Toolchain tc = null;

        if (jdkToolchain != null) {
            List<Toolchain> tcs = toolchainManager.getToolchains(session, "jdk", jdkToolchain);
            if (tcs != null && !tcs.isEmpty()) {
                tc = tcs.get(0);
            }
        }

        if (tc == null) {
            // TODO: Check if we should make the type configurable?
            tc = toolchainManager.getToolchainFromBuildContext(session, "jdk").orElse(null);
        }

        return tc;
    }

    public Project getProject() {
        return project;
    }

    public Session getSession() {
        return session;
    }
}
