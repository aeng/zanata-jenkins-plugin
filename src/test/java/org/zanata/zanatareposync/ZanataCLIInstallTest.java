package org.zanata.zanatareposync;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

import com.google.common.collect.Lists;

public class ZanataCLIInstallTest {

    @Test
    public void canConvertVersionToName() {
        ZanataCLIInstall zanataCLIInstall =
                new ZanataCLIInstall("", "4.0.0-RC1", Lists.newArrayList());
        String name = zanataCLIInstall.getName();
        assertThat(name, equalTo("zanata_cli_4_0_0_RC1"));
    }

}
