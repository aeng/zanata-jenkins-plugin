package org.zanata.zanatareposync;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Handler;
import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.cli.SyncJobDetail;
import org.zanata.cli.service.impl.ZanataSyncServiceImpl;
import org.zanata.git.GitSyncService;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.PasswordCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.Tasks;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
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
    private static final Logger log =
            LoggerFactory.getLogger(ZanataBuilder.class);

    private final String zanataURL;
    private final String syncOption;
    private final String zanataProjectConfigs;
    private final String zanataLocaleIds;
    private final String zanataProjectId;
    private final boolean pushToZanata;
    private final boolean pullFromZanata;
    private final String zanataCredentialsId;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public ZanataBuilder(String zanataURL,
            String zanataCredentialsId, String syncOption,
            String zanataProjectConfigs, String zanataLocaleIds,
            String zanataProjectId,
            boolean pushToZanata, boolean pullFromZanata) {
        this.zanataURL = zanataURL;
        this.zanataCredentialsId = zanataCredentialsId;
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

    public String getZanataCredentialsId() {
        return zanataCredentialsId;
    }

    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException {
        // This is where you 'build' the project.
        Handler logHandler = configLogger(listener.getLogger());

        // TODO pahuang check credential plugin
//        Plugin credentialsPlugin = Jenkins.getInstance().getPlugin("credentials-uploader");

        IdCredentials cred = CredentialsProvider.findCredentialById(zanataCredentialsId, IdCredentials.class, build);
        if (cred == null) {
            throw new AbortException("Zanata credential with ID [" + zanataCredentialsId + "] can not be found.");
        }
        CredentialsProvider.track(build, cred);
        StandardUsernameCredentials usernameCredentials = (StandardUsernameCredentials) cred;
        String apiKey =
                ((PasswordCredentials) usernameCredentials).getPassword()
                        .getPlainText();
        logger(listener).println("Running Zanata sync for "+ zanataURL +"!");
        SyncJobDetail syncJobDetail = SyncJobDetail.Builder.builder()
                .setZanataUrl(zanataURL)
                .setZanataUsername(usernameCredentials.getUsername())
                .setZanataSecret(apiKey)
                .setSyncToZanataOption(syncOption)
                .setProjectConfigs(zanataProjectConfigs)
                .setLocaleId(zanataLocaleIds)
                .setProject(zanataProjectId)
                .build();

        logger(listener).println("Job config: " + syncJobDetail.toString());

        ZanataSyncServiceImpl service =
                new ZanataSyncServiceImpl(syncJobDetail);


        try {
            if (pushToZanata) {
                pushToZanata(workspace, service);
            }
            if (pullFromZanata) {
                Git git =
                        Git.with(listener, new EnvVars(EnvVars.masterEnvVars));
                GitSyncService gitSyncService = new GitSyncService(syncJobDetail, git);
                pullFromZanata(workspace, service, gitSyncService);
            }
        } catch (IOException | InterruptedException e) {
            logger(listener).println("Zanata Sync failed:" + e.getMessage());
            throw new RuntimeException(e);
        } finally {
            removeLogger(logHandler);
        }
    }

    private static Handler configLogger(PrintStream logger) {
        java.util.logging.Logger zLogger =
                java.util.logging.Logger.getLogger("org.zanata");
        ZanataCLILoggerHandler appender =
                new ZanataCLILoggerHandler(logger);
        zLogger.addHandler(appender);
        return appender;
    }

    private static void removeLogger(Handler appender) {
        java.util.logging.Logger zLogger =
                java.util.logging.Logger.getLogger("org.zanata");
        zLogger.removeHandler(appender);
    }

    private static void pullFromZanata(FilePath workspace,
            final ZanataSyncServiceImpl service, GitSyncService gitSyncService)
            throws IOException, InterruptedException {
        workspace.act(new FilePath.FileCallable<Void>() {

            @Override
            public Void invoke(File f, VirtualChannel channel)
                    throws IOException, InterruptedException {
                service.pullFromZanata(f.toPath());
                gitSyncService.syncTranslationToRepo(f.toPath());
                return null;
            }

            @Override
            public void checkRoles(RoleChecker roleChecker)
                    throws SecurityException {
            }
        });
    }

    private static void pushToZanata(FilePath workspace,
            final ZanataSyncServiceImpl service)
            throws IOException, InterruptedException {
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

        // TODO I don't really understand how below method works. It's taken from git-plugin jenkins.plugins.git.GitSCMSource
        public ListBoxModel doFillZanataCredentialsIdItems(@AncestorInPath
                AbstractProject context,
                @QueryParameter String remote,
                @QueryParameter String credentialsId) {
            if (context == null && !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER) ||
                    context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }
            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(
                            Tasks.getAuthenticationOf(context),
                            context,
                            StandardUsernameCredentials.class,
                            URIRequirementBuilder.fromUri(remote).build(),
                            CredentialsMatchers
                                    .instanceOf(StandardUsernamePasswordCredentials.class))
                    .includeCurrentValue(credentialsId);
        }

        public FormValidation doCheckZanataCredentialsId(@AncestorInPath AbstractProject context,
                @QueryParameter String url,
                @QueryParameter String value) {
            if (context == null && !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER) ||
                    context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                return FormValidation.ok();
            }

            value = Util.fixEmptyAndTrim(value);
            if (value == null) {
                return FormValidation.ok();
            }

            url = Util.fixEmptyAndTrim(url);
            if (url == null) {
                // not set, can't check
                return FormValidation.ok();
            }

            for (ListBoxModel.Option o : CredentialsProvider.listCredentials(
                    StandardUsernameCredentials.class,
                    context,
                    Tasks.getAuthenticationOf(context),
                    URIRequirementBuilder.fromUri(url).build(),
                    CredentialsMatchers
                            .instanceOf(StandardUsernamePasswordCredentials.class))) {
                if (StringUtils.equals(value, o.value)) {
                    return FormValidation.ok();
                }
            }
            // no credentials available, can't check
            return FormValidation.warning("Cannot find any credentials with id " + value);
        }

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

