package org.zanata.zanatareposync;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import javax.servlet.ServletException;

import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.zanata.cli.SyncJobDetail;
import org.zanata.cli.service.impl.ZanataSyncServiceImpl;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link ZanataBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #zanataURL})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform} method will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */
public class ZanataBuilder extends Builder implements SimpleBuildStep {

    private final String zanataURL;
    private final String zanataUsername;
    private final String zanataAPI;
    private final String syncOption;
    private final String zanataProjectConfigs;
    private final String zanataLocaleIds;
    private final String zanataProjectId;
    private final boolean pushToZanata;
    private final boolean pullFromZanata;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public ZanataBuilder(String zanataURL, String zanataUsername,
            String zanataAPI, String syncOption, String zanataProjectConfigs,
            String zanataLocaleIds, String zanataProjectId,
            boolean pushToZanata, boolean pullFromZanata) {
        this.zanataURL = zanataURL;
        this.zanataUsername = zanataUsername;
        this.zanataAPI = zanataAPI;
        this.syncOption = syncOption;
        this.zanataProjectConfigs = zanataProjectConfigs;
        this.zanataLocaleIds = zanataLocaleIds;
        this.zanataProjectId = zanataProjectId;
        this.pushToZanata = pushToZanata;
        this.pullFromZanata = pullFromZanata;
    }

    /**
     * We'll use this from the {@code config.jelly}.
     */
    public String getZanataURL() {
        return zanataURL;
    }

    public String getZanataUsername() {
        return zanataUsername;
    }

    public String getZanataAPI() {
        return zanataAPI;
    }

    public String getSyncOption() {
        return syncOption;
    }

    public String getZanataProjectConfigs() {
        return zanataProjectConfigs;
    }

    public String getZanataLocaleIds() {
        return zanataLocaleIds;
    }

    public String getZanataProjectId() {
        return zanataProjectId;
    }

    public boolean isPushToZanata() {
        return pushToZanata;
    }

    public boolean isPullFromZanata() {
        return pullFromZanata;
    }

    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) {
        // This is where you 'build' the project.
        // Since this is a dummy, we just say 'hello world' and call that a build.

        // This also shows how you can consult the global configuration of the builder
//        if (getDescriptor().getUseFrench())
//            listener.getLogger().println("Bonjour, "+ zanataCLIVersion +"!");
//        else
        logger(listener).println("Running Zanata sync for "+ zanataURL +"!");
        SyncJobDetail syncJobDetail = SyncJobDetail.Builder.builder()
                .setZanataUrl(zanataURL)
                .setZanataUsername(zanataUsername)
                .setZanataSecret(zanataAPI)
                .setSyncToZanataOption(syncOption)
                .setProjectConfigs(zanataProjectConfigs)
                .setLocaleId(zanataLocaleIds)
                .setProject(zanataProjectId)
                .build();

        logger(listener).println("Job config: " + syncJobDetail.toString());

        ZanataSyncServiceImpl service =
                new ZanataSyncServiceImpl(syncJobDetail);
        if (pushToZanata) {
            try {
                workspace.act(new FilePath.FileCallable<Void>() {
                    @Override
                    public Void invoke(File f, VirtualChannel channel)
                            throws IOException, InterruptedException {
                        service.pushToZanata(f.toPath());
                        return null;
                    }

                    @Override
                    public void checkRoles(RoleChecker roleChecker)
                            throws SecurityException {
                    }
                });
            } catch (IOException | InterruptedException e) {
                logger(listener).println("push to zanata failed:" + e.getMessage());
                throw new RuntimeException(e);
            }
        }
        if (pullFromZanata) {
            try {
                workspace.act(new FilePath.FileCallable<Void>() {

                    @Override
                    public Void invoke(File f, VirtualChannel channel)
                            throws IOException, InterruptedException {
                        service.pushToZanata(f.toPath());
                        return null;
                    }

                    @Override
                    public void checkRoles(RoleChecker roleChecker)
                            throws SecurityException {
                    }
                });
            } catch (IOException | InterruptedException e) {
                logger(listener).println("pull from zanata failed:" + e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    private static PrintStream logger(TaskListener listener) {
        return listener.getLogger();
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link ZanataBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See {@code src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly}
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use {@code transient}.
         */
//        private boolean useFrench;

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        // ========== FORM validation ===========================================
        // ========== https://wiki.jenkins-ci.org/display/JENKINS/Form+Validation

        /**
         * Performs on-the-fly validation of the form field 'zanataURL'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
        public FormValidation doCheckZanataURL(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please set a zanata URL");
            }
            // TODO check URL
            if (value.length() < 4) {
                return FormValidation.warning("Isn't the zanataCLIVersion too short?");
            }
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable zanataCLIVersion is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Zanata Sync";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
//            useFrench = formData.getBoolean("useFrench");
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req,formData);
        }

        /**
         * This method returns true if the global configuration says we should speak French.
         *
         * The method zanataCLIVersion is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         */
//        public boolean getUseFrench() {
//            return useFrench;
//        }
    }
}

