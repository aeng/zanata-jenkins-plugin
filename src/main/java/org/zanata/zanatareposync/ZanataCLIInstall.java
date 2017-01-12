/*
 * Copyright 2012, CloudBees Inc., Synopsys Inc. and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.zanata.zanatareposync;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ZipExtractionInstaller;
import jenkins.model.Jenkins;

/**
 * An arbitrary tool, which can add directories to the build's PATH.
 * @author rcampbell
 * @author Oleg Nenashev
 *
 */
@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID",
        justification = "Actually we do not send the class over the channel. Serial version ID is not required for XStream")
public class ZanataCLIInstall extends ToolInstallation implements
        NodeSpecific<ZanataCLIInstall> {

    /**
     * Label-specific options.
     */
    private final @CheckForNull LabelSpecifics[] labelSpecifics;
    /**
     * A cached value of the home directory.
     */
    private transient @CheckForNull String correctedHome = null;
    /**
     * Optional field, which referenced the {@link ToolVersion} configuration.
     */
//    private final @CheckForNull ToolVersionConfig toolVersion;
    /**
     * Additional variables string.
     * Stores variables expression in *.properties format.
     */
//    private final @CheckForNull String additionalVariables;

    private static final LabelSpecifics[] EMPTY_LABELS = new LabelSpecifics[0];

    @DataBoundConstructor
    public ZanataCLIInstall(@Nonnull String name, @Nonnull String home,
            @CheckForNull List properties, @CheckForNull LabelSpecifics[] labelSpecifics) {
        super(name, home, properties);
        this.labelSpecifics = labelSpecifics != null ? Arrays.copyOf(labelSpecifics, labelSpecifics.length) : null;
    }

    @Override
    @CheckForNull
    public String getHome() {
        return (correctedHome != null) ? correctedHome : super.getHome();
    }

    // We may need to correct the file and path separator for different nodes
//    public void correctHome(@Nonnull PathsList pathList) {
//        correctedHome = pathList.getHomeDir();
//    }

    public @Nonnull LabelSpecifics[] getLabelSpecifics() {
        return (labelSpecifics!=null) ? labelSpecifics : EMPTY_LABELS;
    }

    @Override
    public @Nonnull ZanataCLIInstall forNode(Node node, TaskListener log)
            throws IOException, InterruptedException {
        String home = translateFor(node, log);
        return new ZanataCLIInstall(getName(), home, getProperties().toList(), getLabelSpecifics());
    }

    /**
     * Get list of label specifics, which apply to the specified node.
     * @param node Node to be checked
     * @return List of the specifics to be applied
     * @since 0.3
     */
    public @Nonnull List<LabelSpecifics> getAppliedSpecifics(@Nonnull Node node) {
        List<LabelSpecifics> out = new LinkedList<>();
        if (labelSpecifics != null) {
            for (LabelSpecifics spec : labelSpecifics) {
                if (spec.appliesTo(node)) {
                    out.add(spec);
                }
            }
        }
        return out;
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<ZanataCLIInstall> {

        public DescriptorImpl() {
            load();
        }

        @Override
        public String getDisplayName() {
            return "Zanata CLI";
        }

        @Override
        public void setInstallations(ZanataCLIInstall... installations) {
            super.setInstallations(installations);
            save();
        }

        /**
         * Gets a {@link ZanataCLIInstall} by its name.
         * @param name A name of the tool to be retrieved.
         * @return A {@link ZanataCLIInstall} or null if it has no found
         */
        public @CheckForNull ZanataCLIInstall byName(String name) {
            for (ZanataCLIInstall tool : getInstallations()) {
                if (tool.getName().equals(name)) {
                    return tool;
                }
            }
            return null;
        }

        @Override
        public List<? extends ToolInstaller> getDefaultInstallers() {
            return Collections.singletonList(new ZipExtractionInstaller(null,
                    "https://repo1.maven.org/maven2/org/zanata/zanata-cli/4.0.0/zanata-cli-4.0.0-dist.zip", "zanata-cli-4.0.0"));
        }
    }

    /**
     * Finds the directories to add to the path, for the given node.
     * Uses Ant filesets to expand the patterns in the exportedPaths field.
     *
     * @param node where the tool has been installed
     * @return a list of directories to add to the $PATH
     *
     * @throws IOException
     * @throws InterruptedException Operation has been interrupted
     */
    /*protected @Nonnull PathsList getPaths(@Nonnull Node node) throws IOException, InterruptedException {

        FilePath homePath = new FilePath(node.getChannel(), getHome());
        //FIXME: Why?
        if (exportedPaths == null) {
            return PathsList.EMPTY;
        }
        final List<LabelSpecifics> specs = getAppliedSpecifics(node);

        PathsList pathsFound = homePath.act(new MasterToSlaveFileCallable<PathsList>() {
            private void parseLists(String pathList, List<String> target) {
                String[] items = pathList.split("\\s*,\\s*");
                for (String item : items) {
                    if (item.isEmpty()) {
                        continue;
                    }
                    target.add(item);
                }
            }

            @Override
            public PathsList invoke(File f, VirtualChannel channel)
                    throws IOException, InterruptedException {

                // Construct output paths
                List<String> items = new LinkedList<String>();
                if (exportedPaths != null) {
                    parseLists(exportedPaths, items);
                }
                for (LabelSpecifics spec : specs) {
                    final String exportedPathsFromSpec = spec.getExportedPaths();
                    if (exportedPathsFromSpec != null) {
                        parseLists(exportedPathsFromSpec, items);
                    }
                }

                // Resolve exported paths
                List<String> outList = new LinkedList<String>();
                for (String item : items) {
                    File file = new File(item);
                    if (!file.isAbsolute()) {
                        file = new File (getHome(), item);
                    }

                    // Check if directory exists
                    if (!file.isDirectory() || !file.exists()) {
                        throw new AbortException("Wrong EXPORTED_PATHS configuration. Can't find "+file.getPath());
                    }
                    outList.add(file.getAbsolutePath());
                }

                // Resolve home dir
                final String toolHome = getHome();
                if (toolHome == null) {
                    throw new IOException("Cannot retrieve Tool home directory. Should never happen ant this stage, please file a bug");
                }
                final File homeDir = new File(toolHome);
                return new PathsList(outList, homeDir.getAbsolutePath());
            };
        });

        return pathsFound;
    }*/

}
