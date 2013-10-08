package com.urswolfer.intellij.plugin.gerrit.rest;

import org.testng.Assert;
import org.testng.annotations.*;

import java.lang.reflect.Method;

/**
 * @author <a href=mailto:uwolfer@tocco.ch>Urs Wolfer</a>
 * @since 5/26/13
 */
public class GerritUtilTest {
    @Test
    public void testGetAvailableRepos() throws Exception {
    }

    @Test
    public void testProjectNames() throws Exception {
        final Method getProjectName = GerritUtil.class.getDeclaredMethod("getProjectName",
                String.class, String.class);
        getProjectName.setAccessible(true);

        // Default set - test trailing / behaviour
        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server",
                        "http://gerrit.server/project"
                ));

        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/",
                        "http://gerrit.server/project"
                ));

        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server",
                        "http://gerrit.server/project/"
                ));

        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/",
                        "http://gerrit.server/project/"
                ));

        // Subdirectory set - test trailing / behaviour
        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/r",
                        "http://gerrit.server/r/project"
                ));

        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/r/",
                        "http://gerrit.server/r/project"
                ));

        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/r",
                        "http://gerrit.server/r/project/"
                ));

        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/r/",
                        "http://gerrit.server/r/project/"
                ));

        // Default set - test named .git
        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server",
                        "http://gerrit.server/project.git"
                ));

        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/",
                        "http://gerrit.server/project.git"
                ));


        // Subdirectory set - test named .git
        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/r",
                        "http://gerrit.server/r/project.git"
                ));

        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/r/",
                        "http://gerrit.server/r/project.git"
                ));

        // Test some project names with / in them
        Assert.assertEquals("project/blah/test",
                getProjectName.invoke(null,
                        "http://gerrit.server/r",
                        "http://gerrit.server/r/project/blah/test"
                ));

        Assert.assertEquals("project/blah/test",
                getProjectName.invoke(null,
                        "http://gerrit.server/r",
                        "http://gerrit.server/r/project/blah/test.git"
                ));
    }

}
