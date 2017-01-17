package org.zanata.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;

/**
 * TODO not used. But we can use it to invoke zanata cli programmatically.
 * @author Patrick Huang <a href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
public class RunAsCommand {
    private static final Logger log =
            LoggerFactory.getLogger(RunAsCommand.class);

    public boolean run(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener, ArgumentListBuilder args)
            throws InterruptedException {

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            EnvVars env = build.getEnvironment(listener);
            env.putAll(build.getCharacteristicEnvVars());

            log.debug("Environment variables: {}", env.entrySet());
            log.debug("Command line: {}", args.toStringWithQuote());

            StreamBuildListener streamBuildListener = new StreamBuildListener(baos);

            final Proc child = workspace.createLauncher(listener)
                    .launch()
                    .cmds(args).envs(env).stdout(streamBuildListener)
                    .pwd(workspace)
                    .start();

            try {
                while (child.isAlive()) {
                    listener.getLogger().print(baos.toString("UTF-8"));
                    baos.reset();

                    Thread.sleep(5);
                }
            } catch (InterruptedException intEx) {
                child.kill();
                listener.getLogger().println("Aborted by User. Terminated");
                throw(new InterruptedException("User Aborted"));
            }

            baos.flush();
            listener.getLogger().print(baos.toString());
            listener.getLogger().flush();
            return child.join() == 0;
        } catch (IOException e) {
            log.error("failed in error", e);
            return false;
        }
    }
}
