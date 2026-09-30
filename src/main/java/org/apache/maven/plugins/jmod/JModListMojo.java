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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.shared.utils.cli.Commandline;

/**
 * This goal is to support the usage of <code>jmod list</code> to show the content of a <code>jmod</code> file.
 *
 * @author Karl Heinz Marbaise <a href="mailto:khmarbaise@apache.org">khmarbaise@apache.org</a>
 */
@Mojo(name = "list")
public class JModListMojo extends AbstractJModMojo {

    @Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
    private Path outputDirectory;

    /**
     * The name of the jmod file which is examined via <code>jmod list jmodFile</code>.
     */
    // @formatter:off
    @Parameter(
            defaultValue = "${project.build.directory}/jmods/${project.artifactId}.jmod",
            property = "jmodfile",
            required = true)
    // @formatter:on
    private Path jmodFile;

    @Override
    public void execute() throws MojoException {
        try {
            String jModExecutable = getJModExecutable();
            log.debug("Toolchain in maven-jmod-plugin: jmod [ " + jModExecutable + " ]");

            Commandline cmd = createJModListCommandLine();
            cmd.setExecutable(jModExecutable);

            log.info("The following files are contained in the module file " + jmodFile.toAbsolutePath());
            executeCommand(cmd, outputDirectory);
        } catch (IOException e) {
            throw new MojoException("Unable to find jmod command: " + e.getMessage(), e);
        }
    }

    private Commandline createJModListCommandLine() throws MojoException {
        if (!Files.isRegularFile(jmodFile)) {
            throw new MojoException("Unable to find " + jmodFile.toAbsolutePath());
        }

        Commandline commandLine = new Commandline();
        commandLine.createArg().setValue("list");
        commandLine.createArg().setValue(jmodFile.toAbsolutePath().toString());
        return commandLine;
    }
}
