/*
 * Copyright 2013 Oleg Nenashev, Synopsys Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zanata.zanatareposync;

import java.io.Serializable;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import jenkins.model.Jenkins;

/**
 * Contains label-specific options.
 * @author Oleg Nenashev
 * @since 0.3
 */
public class LabelSpecifics extends AbstractDescribableImpl<LabelSpecifics>
        implements Serializable {

    private final @CheckForNull String label;

    @DataBoundConstructor
    public LabelSpecifics(@CheckForNull String label) {
        this.label = Util.fixEmptyAndTrim(label);
    }

    public @CheckForNull String getLabel() {
        return label;
    }

    /**
     * Check if specifics is applicable to node
     * @param node Node to be checked
     * @return True if specifics is applicable to node
     */
    public boolean appliesTo(@Nonnull Node node) {
        String correctedLabel = Util.fixEmptyAndTrim(label);
        if (correctedLabel == null) {
            return true;
        }

        Label l = Jenkins.getActiveInstance().getLabel(label);
        return l == null || l.contains(node);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<LabelSpecifics> {
        @Override
        public String getDisplayName() {
            return "Label specifics";
        }
    }

    public @Nonnull LabelSpecifics substitute(@Nonnull Node node) {
        return new LabelSpecifics(label);
    }

    public static @Nonnull LabelSpecifics[] substitute (LabelSpecifics[] specifics, Node node) {
        LabelSpecifics[] out = new LabelSpecifics[specifics.length];
        for (int i=0; i<specifics.length; i++) {
            out[i] = specifics[i].substitute(node);
        }
        return out;
    }
}
