package org.jenkinsci.plugins.zanata.zanatareposync;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Handler;
import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jenkinsci.plugins.zanata.cli.SyncJobDetail;
import org.jenkinsci.plugins.zanata.cli.service.impl.ZanataSyncServiceImpl;
import org.jenkinsci.plugins.zanata.git.GitSyncService;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.PasswordCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.common.base.Strings;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Queue;
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

    private String zanataURL;
    private String syncOption;
    private String zanataProjectConfigs;
    private String zanataLocaleIds;
    private boolean pushToZanata;
    private boolean pullFromZanata;
    private String zanataCredentialsId;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public ZanataBuilder(String zanataCredentialsId) {
        this.zanataURL = null;
        this.zanataCredentialsId = zanataCredentialsId;
        this.syncOption = "source";
        this.zanataProjectConfigs = null;
        this.zanataLocaleIds = null;
        this.pushToZanata = true;
        this.pullFromZanata = true;
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

    public boolean isPushToZanata() {
        return pushToZanata;
    }

    public boolean isPullFromZanata() {
        return pullFromZanata;
    }

    public String getZanataCredentialsId() {
        return zanataCredentialsId;
    }

    @DataBoundSetter
    public void setZanataURL(String zanataURL) {
        this.zanataURL = zanataURL;
    }

    @DataBoundSetter
    public void setSyncOption(String syncOption) {
        this.syncOption = syncOption;
    }

    @DataBoundSetter
    public void setZanataProjectConfigs(String zanataProjectConfigs) {
        this.zanataProjectConfigs = zanataProjectConfigs;
    }

    @DataBoundSetter
    public void setZanataLocaleIds(String zanataLocaleIds) {
        this.zanataLocaleIds = zanataLocaleIds;
    }

    @DataBoundSetter
    public void setPushToZanata(boolean pushToZanata) {
        this.pushToZanata = pushToZanata;
    }

    @DataBoundSetter
    public void setPullFromZanata(boolean pullFromZanata) {
        this.pullFromZanata = pullFromZanata;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
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

    @SuppressFBWarnings("LG_LOST_LOGGER_DUE_TO_WEAK_REFERENCE")
    private static Handler configLogger(PrintStream printStream) {
        ZanataCLILoggerHandler loggerHandler =
                new ZanataCLILoggerHandler(printStream);

        java.util.logging.Logger.getLogger("org.zanata").addHandler(loggerHandler);
        return loggerHandler;
    }

    private static void removeLogger(Handler appender) {
        java.util.logging.Logger.getLogger("org.zanata").removeHandler(appender);
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
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        // ========== FORM validation ===========================================
        // ========== https://wiki.jenkins-ci.org/display/JENKINS/Form+Validation

        @SuppressWarnings("unused")
        public ListBoxModel doFillZanataCredentialsIdItems(@AncestorInPath
                Job context,
                @QueryParameter String remote,
                @QueryParameter String credentialsId) {
            if (context == null || !context.hasPermission(Item.CONFIGURE)) {
                // previously it was recommended to just return an empty ListBoxModel
                // now recommended to return a model with just the current value
                return new StandardUsernameListBoxModel().includeCurrentValue(credentialsId);
            }
            // previously it was recommended to use the withXXX methods providing the credentials instances directly
            // now recommended to populate the model using the includeXXX methods which call through to
            // CredentialsProvider.listCredentials and to ensure that the current value is always present using
            // includeCurrentValue
            return new StandardUsernameListBoxModel()
                    .includeEmptyValue()
                    .includeAs(Tasks.getAuthenticationOf((Queue.Task) context), context, StandardUsernameCredentials.class,
                            URIRequirementBuilder.fromUri(remote).build())
                    .includeCurrentValue(credentialsId);
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillSyncOptionItems(
                @QueryParameter String selection) {
            return new ListBoxModel(new ListBoxModel.Option("source", "source",
                    "source".equals(selection)),
                    new ListBoxModel.Option("both", "both",
                            "both".equals(selection)),
                    new ListBoxModel.Option("trans", "trans",
                            "trans".equals(selection)));

        }

        @SuppressWarnings("unused")
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
        @SuppressWarnings("unused")
        public FormValidation doCheckZanataURL(@QueryParameter String value)
                throws IOException, ServletException {
            if (!Strings.isNullOrEmpty(value) && value.trim().length() > 0) {
                try {
                    URL url = new URL(value);
                    log.trace("url from UI: {}", url);
                } catch (MalformedURLException e) {
                    return FormValidation.error("Not a valid URL");
                }
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

    }
}

