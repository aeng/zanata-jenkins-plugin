/*
 * Copyright 2015, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.zanata.git;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import org.jenkinsci.remoting.RoleChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.cli.SyncJobDetail;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;

/**
 * Note JGIT doesn't support shallow clone yet. But jenkins has an abstraction
 * to use native git first then fall back to JGIT. see http://stackoverflow.com/questions/11475263/shallow-clone-with-jgit?rq=1#comment38082799_12097883
 * see https://bugs.eclipse.org/bugs/show_bug.cgi?id=475615
 *
 * @author Patrick Huang <a href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
public class GitSyncService implements RepoSyncService {
    private static final Logger log =
            LoggerFactory.getLogger(GitSyncService.class);
    private final SyncJobDetail syncJobDetail;
    private final org.jenkinsci.plugins.gitclient.Git git;

    public GitSyncService(SyncJobDetail syncJobDetail,
            Git git) {
        this.syncJobDetail = syncJobDetail;
        this.git = git;
    }

    @Override
    public void syncTranslationToRepo(Path workingDir) {

        try {
            GitClient gitClient =
                    git.in(workingDir.toFile()).using("jgit").getClient();
            if (!gitClient.hasGitRepo()) {
                log.warn("no git repository found. Skip git commit step");
                return;
            }
            if (log.isDebugEnabled()) {
                gitClient.withRepository(
                        (RepositoryCallback<Void>) (repo, channel) -> {
                            log.debug("current branch: {}", repo.getBranch());
                            return null;
                        });

            }

            gitClient.getWorkTree().act(new FilePath.FileCallable<Void>() {
                @Override
                public Void invoke(File f, VirtualChannel channel)
                        throws IOException, InterruptedException {
                    try (org.eclipse.jgit.api.Git jgit = org.eclipse.jgit.api.Git.open(f)) {
                        StatusCommand statusCommand = jgit.status();
                        Status status = statusCommand.call();
                        Set<String> uncommittedChanges = status.getUncommittedChanges();
                        uncommittedChanges.addAll(status.getUntracked());
                        if (!uncommittedChanges.isEmpty()) {
                            log.info("uncommitted files in git repo: {}",
                                    uncommittedChanges);
                            // ignore zanata cache folder
                            uncommittedChanges.stream()
                                    .filter(file -> !file.startsWith(".zanata-cache/"))
                                    .forEach(file -> {
                                        try {
                                            gitClient.add(file);
                                        } catch (InterruptedException e) {
                                            throw new RepoSyncException("interrupted", e);
                                        }
                                    });

                            log.info("commit changed files");
                            gitClient.setAuthor(commitAuthorName(),
                                    commitAuthorEmail());
                            gitClient.setCommitter(commitAuthorName(), commitAuthorEmail());
                            gitClient.commit(commitMessage(syncJobDetail.getZanataUsername()));

//                            log.info("push to remote the commit: {}");
//                            gitClient.push();
                        } else {
                            log.info("nothing changed so nothing to do");
                        }
                    } catch (GitAPIException | InterruptedException gitException) {
                        throw new RepoSyncException("error committing", gitException);
                    }

                    return null;
                }

                @Override
                public void checkRoles(RoleChecker roleChecker)
                        throws SecurityException {

                }
            });

        } catch (IOException | InterruptedException e) {
            log.error("error dealing with git repo: {}", workingDir);
            throw new RepoSyncException("error dealing with git repo", e);
        }

    }

}
