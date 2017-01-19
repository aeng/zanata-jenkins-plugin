package org.jenkinsci.plugins.zanata.git;

/**
 * @author Patrick Huang <a href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
public class RepoSyncException extends RuntimeException {

    public RepoSyncException(String message, Throwable e) {
        super(message, e);
    }

    public RepoSyncException(String message) {
        super(message);
    }
}
