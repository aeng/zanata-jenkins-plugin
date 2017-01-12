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
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Run.RunnerAbortedException;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

/**
 * Installs tools selected by the user. Exports configured paths and a home variable for each tool.
 *
 * @author rcampbell
 * @author Oleg Nenashev
 *
 */
public class ZanataCLIInstallWrapper extends BuildWrapper {

    /**
     * Ceremony needed to satisfy NoStaplerConstructionException:
     * "There's no @DataBoundConstructor on any constructor of class java.lang.String"
     * @author rcampbell
     *
     */
    public static class SelectedTool {
        private final String name;

        @DataBoundConstructor
        public SelectedTool(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }

        public @CheckForNull ZanataCLIInstall toCustomTool() {
            return ((ZanataCLIInstall.DescriptorImpl) Jenkins.getActiveInstance().getDescriptor(ZanataCLIInstall.class)).byName(name);
        }

        public @Nonnull ZanataCLIInstall toCustomToolValidated() {
            ZanataCLIInstall tool = toCustomTool();
            if (tool == null) {
                throw new RuntimeException("Can not find Zanata CLI. Has it been deleted in global configuration?");
            }
            return tool;
        }
    }

    private @Nonnull SelectedTool[] selectedTools = new SelectedTool[0];
    private final boolean convertHomesToUppercase;

    @DataBoundConstructor
    public ZanataCLIInstallWrapper(SelectedTool[] selectedTools, boolean convertHomesToUppercase) {
        this.selectedTools = (selectedTools != null) ? selectedTools : new SelectedTool[0];
        this.convertHomesToUppercase = convertHomesToUppercase;
    }

    public boolean isConvertHomesToUppercase() {
        return convertHomesToUppercase;
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher,
            BuildListener listener) throws IOException, InterruptedException {

        final EnvVars buildEnv = build.getEnvironment(listener);
        final Node node = build.getBuiltOn();

        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {

                // TODO: Inject Home dirs as well
                for (SelectedTool selectedTool : selectedTools) {
                    ZanataCLIInstall tool = selectedTool.toCustomTool();
                    if (tool != null /*&& tool.hasVersions()*/) {
//                        ToolVersion version = ToolVersion.getEffectiveToolVersion(tool, buildEnv, node);
//                        if (version != null && !env.containsKey(version.getVariableName())) {
//                            env.put(version.getVariableName(), version.getDefaultVersion());
//                        }
                    }
                }
            }
        };
    }

    public @Nonnull SelectedTool[] getSelectedTools() {
        return selectedTools.clone();
    }

    /**
     * The heart of the beast. Installs selected tools and exports their paths to the
     * PATH and their HOMEs as environment variables.
     * @return A decorated launcher
     */
    @Override
    public Launcher decorateLauncher(AbstractBuild build, final Launcher launcher,
            BuildListener listener) throws IOException, InterruptedException,
            RunnerAbortedException {

        EnvVars buildEnv = build.getEnvironment(listener);
        final EnvVars homes = new EnvVars();
        final EnvVars versions = new EnvVars();

//        final PathsList paths = new PathsList();
//        final List<EnvVariablesInjector> additionalVarInjectors = new LinkedList<EnvVariablesInjector>();

        // Each tool can export zero or many directories to the PATH
        final Node node =  Computer.currentComputer().getNode();
        if (node == null) {
            throw new RuntimeException("Cannot install tools on the deleted node");
        }

        for (SelectedTool selectedToolName : selectedTools) {
            ZanataCLIInstall tool = selectedToolName.toCustomToolValidated();
            logMessage(listener, "Starting installation");

            // Check versioning
//            checkVersions(tool, listener, buildEnv, node, versions);

            // This installs the tool if necessary
            ZanataCLIInstall installed = tool
                    .forNode(node, listener);

            // Handle global options of the tool
            //TODO: convert to label specifics?
//            final PathsList installedPaths = installed.getPaths(node);
//            installed.correctHome(installedPaths);
//            paths.add(installedPaths);
//            final String additionalVars = installed.getAdditionalVariables();
//            if (additionalVars != null) {
//                additionalVarInjectors.add(EnvVariablesInjector.create(additionalVars));
//            }

            // Handle label-specific options of the tool
            for (LabelSpecifics spec : installed.getLabelSpecifics()) {
                if (!spec.appliesTo(node)) {
                    continue;
                }
                logMessage(listener, "Label specifics from '" + spec.getLabel() + "' will be applied");

//                final String additionalLabelSpecificVars = spec.getAdditionalVars();
//                if (additionalLabelSpecificVars != null) {
//                    additionalVarInjectors.add(EnvVariablesInjector.create(additionalLabelSpecificVars));
//                }
            }

            logMessage(listener, "Tool is installed at "+ installed.getHome());
            String homeDirVarName = (convertHomesToUppercase ? installed.getName().toUpperCase(Locale.ENGLISH) : installed.getName()) +"_HOME";
            logMessage(listener,
                    "Setting " + homeDirVarName + "=" + installed.getHome());
            homes.put(homeDirVarName, installed.getHome());
        }

        return new Launcher.DecoratedLauncher(launcher) {
            @Override
            public Proc launch(Launcher.ProcStarter starter) throws IOException {
                EnvVars vars;
                try { // Dirty hack, which allows to avoid NPEs in Launcher::envs()
                    vars = toEnvVars(starter.envs());
                } catch (NullPointerException npe) {
                    vars = new EnvVars();
                } catch (InterruptedException x) {
                    throw new IOException(x);
                }

                // Inject paths
//                final String injectedPaths = paths.toListString();
//                if (injectedPaths != null) {
//                    vars.override("PATH+", injectedPaths);
//                }

                // Inject additional variables
                vars.putAll(homes);
                vars.putAll(versions);
//                for (EnvVariablesInjector injector : additionalVarInjectors) {
//                    injector.Inject(vars);
//                }

                // Override paths to prevent JENKINS-20560
                if (vars.containsKey("PATH")) {
                    final String overallPaths=vars.get("PATH");
                    vars.remove("PATH");
                    vars.put("PATH+", overallPaths);
                }

                return getInner().launch(starter.envs(vars));
            }

            private EnvVars toEnvVars(String[] envs) throws IOException, InterruptedException {
                Computer computer = node.toComputer();
                EnvVars vars = computer != null ? computer.getEnvironment() : new EnvVars();
                for (String line : envs) {
                    vars.addLine(line);
                }
                return vars;
            }
        };
    }

    private static void logMessage(BuildListener listener, String message) {
        listener.getLogger().println("[ZANATA-CLI Install] " + message);
    }

    /**
     * @deprecated The method is deprecated. It will be removed in future versions.
     */
    @SuppressFBWarnings(value = "NM_METHOD_NAMING_CONVENTION", justification = "Deprecated, will be removed later")
    public void CheckVersions (ZanataCLIInstall tool, BuildListener listener, EnvVars buildEnv, Node node, EnvVars target) {
//        checkVersions(tool, listener, buildEnv, node, target);
    }

    /**
     * Checks versions and modify build environment if required.
     * @since 0.4
     */
//    public void checkVersions (@Nonnull ZanataCLIInstall tool, @Nonnull
//            BuildListener listener,
//            @Nonnull EnvVars buildEnv, @Nonnull Node node, @Nonnull
//            EnvVars target) {
//        // Check version
//        if (tool.hasVersions()) {
//            ToolVersion version = ToolVersion.getEffectiveToolVersion(tool, buildEnv, node);
//            if (version == null) {
//                CustomToolsLogger.logMessage(listener, tool.getName(), "Error: No version has been specified, no default version. Failing the build...");
//                throw new CustomToolException("Version has not been specified for the "+tool.getName());
//            }
//
//            CustomToolsLogger.logMessage(listener, tool.getName(), "Version "+version.getActualVersion()+" has been specified by "+version.getVersionSource());
//
//            // Override default versions
//            final String versionSource = version.getVersionSource();
//            if (versionSource != null && versionSource.equals(ToolVersion.DEFAULTS_SOURCE)) {
//                String envStr = version.getVariableName()+"="+version.getDefaultVersion();
//                target.addLine(envStr);
//                buildEnv.addLine(envStr);
//            }
//        }
//    }

    @Override
    public Descriptor<BuildWrapper> getDescriptor() {
        return DESCRIPTOR;
    }


    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        public DescriptorImpl() {
            super(ZanataCLIInstallWrapper.class);
        }

        @Override
        public String getDisplayName() {
            return "Install Zanata CLI";
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        public ZanataCLIInstall[] getInstallations() {
            return Jenkins.getActiveInstance().getDescriptorByType(ZanataCLIInstall.DescriptorImpl.class).getInstallations();
        }
    }
}

