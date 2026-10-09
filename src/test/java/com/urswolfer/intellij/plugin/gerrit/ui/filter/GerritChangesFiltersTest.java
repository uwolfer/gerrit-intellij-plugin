/*
 * Copyright 2026 Urs Wolfer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.urswolfer.intellij.plugin.gerrit.ui.filter;

import com.intellij.util.xmlb.SkipDefaultsSerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GerritChangesFiltersTest {
    private static final String DEFAULT_QUERY = "is:open";

    private static FilterEnvironment environment(boolean loggedIn) {
        return new FilterEnvironment() {
            @Override
            public boolean isLoggedIn() {
                return loggedIn;
            }

            @NotNull
            @Override
            public Map<String, Set<String>> getRemoteBranches() {
                return Map.of("my/project", Set.of("master", "release"));
            }
        };
    }

    @Test
    public void testDefaultStateIsEmptyAndQueryIsDefault() {
        GerritChangesFilters filters = new GerritChangesFilters();

        Assert.assertTrue(filters.saveState().isEmpty());
        Assert.assertEquals(filters.getQuery(), DEFAULT_QUERY);
    }

    @Test
    public void testRoundTrip() {
        Map<String, String> state = new HashMap<>();
        state.put("status", "all");
        state.put("owner", "self");
        state.put("reviewer", "self");
        state.put("assignee", "jane@example.com");
        state.put("attention", "self");
        state.put("branch.project", "my/project");
        state.put("branch.name", "release");
        state.put("starred", "true");
        state.put("showWip", "false");
        state.put("text", "message:fix");
        GerritChangesFilters restored = new GerritChangesFilters();
        restored.restoreState(state, environment(true));

        Assert.assertEquals(restored.saveState(), state);
        GerritChangesFilters other = new GerritChangesFilters();
        other.restoreState(restored.saveState(), environment(true));
        Assert.assertEquals(other.getQuery(), restored.getQuery());
        Assert.assertTrue(restored.getQuery().contains("owner:self"));
        Assert.assertTrue(restored.getQuery().contains("(project:my/project+branch:release)"));
        Assert.assertTrue(restored.getQuery().contains("-is:wip"));
        Assert.assertTrue(restored.getQuery().contains("(message:fix)"));
    }

    @Test
    public void testBranchOfAProjectWithoutBranchSelection() {
        GerritChangesFilters filters = new GerritChangesFilters();
        filters.restoreState(Map.of("branch.project", "my/project"), environment(true));

        Assert.assertEquals(filters.saveState(), Map.of("branch.project", "my/project"));
        Assert.assertTrue(filters.getQuery().contains("project:my/project"));
    }

    @Test
    public void testUnknownValuesFallBackToTheDefault() {
        Map<String, String> state = new HashMap<>();
        state.put("status", "closed");
        state.put("branch.project", "my/project");
        state.put("branch.name", "deleted");
        state.put("starred", "maybe");
        state.put("showWip", "nope");
        state.put("owner", "  ");
        state.put("somethingFromTheFuture", "x");
        GerritChangesFilters filters = new GerritChangesFilters();
        filters.restoreState(state, environment(true));

        Assert.assertTrue(filters.saveState().isEmpty());
        Assert.assertEquals(filters.getQuery(), DEFAULT_QUERY);
    }

    @Test
    public void testUnknownProjectFallsBackToTheDefault() {
        GerritChangesFilters filters = new GerritChangesFilters();
        filters.restoreState(Map.of("branch.project", "gone", "branch.name", "master"), environment(true));

        Assert.assertTrue(filters.saveState().isEmpty());
    }

    @Test
    public void testMeNeedsALogin() {
        Map<String, String> state = Map.of("owner", "self", "reviewer", "other@example.com");
        GerritChangesFilters filters = new GerritChangesFilters();
        filters.restoreState(state, environment(false));

        // not applied, but kept for when there is a login
        Assert.assertEquals(filters.saveState(), state);
        Assert.assertEquals(filters.getQuery(), "is:open+reviewer:other@example.com");
    }

    @Test
    public void testMeKeptWithoutLoginIsAppliedOnceThereIsOne() {
        GerritChangesFilters filters = new GerritChangesFilters();
        filters.restoreState(Map.of("owner", "self"), environment(false));
        filters.restoreState(Map.of("owner", "self"), environment(true));

        Assert.assertEquals(filters.saveState(), Map.of("owner", "self"));
        Assert.assertTrue(filters.getQuery().contains("owner:self"));
    }

    @Test
    public void testBranchWhichCannotBeCheckedYetIsKept() {
        FilterEnvironment noRepositories = new FilterEnvironment() {
            @Override
            public boolean isLoggedIn() {
                return true;
            }

            @NotNull
            @Override
            public Map<String, Set<String>> getRemoteBranches() {
                return Map.of();
            }
        };
        Map<String, String> state = Map.of("branch.project", "my/project", "branch.name", "release");
        GerritChangesFilters filters = new GerritChangesFilters();
        filters.restoreState(state, noRepositories);

        Assert.assertEquals(filters.saveState(), state);
        Assert.assertEquals(filters.getQuery(), DEFAULT_QUERY);
    }

    @Test
    public void testRestoringAgainReplacesWhatWasThere() {
        GerritChangesFilters filters = new GerritChangesFilters();
        filters.restoreState(Map.of("starred", "true", "text", "abc"), environment(true));
        filters.restoreState(Map.of(), environment(true));

        Assert.assertTrue(filters.saveState().isEmpty());
    }

    @Test
    public void testResetGoesBackToTheDefaultsWithoutNotifying() {
        GerritChangesFilters filters = new GerritChangesFilters();
        filters.restoreState(Map.of("starred", "true", "status", "merged", "owner", "self"), environment(false));
        int[] notified = {0};
        filters.addListener(() -> notified[0]++);
        filters.reset();

        Assert.assertEquals(notified[0], 0);
        Assert.assertTrue(filters.saveState().isEmpty());
    }

    @Test
    public void testGeneratedLookupIsNotSaved() {
        GerritChangesFilters filters = new GerritChangesFilters();
        filters.restoreState(Map.of("text", "mine"), environment(true));
        filters.showLookup("commit:abc");

        Assert.assertEquals(filters.saveState(), Map.of("text", "mine"));
    }

    @Test
    public void testRestoringDoesNotNotify() {
        GerritChangesFilters filters = new GerritChangesFilters();
        int[] notified = {0};
        filters.addListener(() -> notified[0]++);
        filters.restoreState(Map.of("starred", "true"), environment(true));

        Assert.assertEquals(notified[0], 0);
    }

    @Test
    public void testSettingsSurviveSerialization() throws Exception {
        GerritProjectSettings settings = new GerritProjectSettings();
        settings.setFilters(Map.of("status", "all", "branch.name", "release"));
        settings.setEnabled(false); // must not drop the filters

        Element written = XmlSerializer.serialize(settings.getState(), new SkipDefaultsSerializationFilter());
        GerritProjectSettings.ProjectState read =
            XmlSerializer.deserialize(written, GerritProjectSettings.ProjectState.class);

        Assert.assertEquals(read.filters, Map.of("status", "all", "branch.name", "release"));
        Assert.assertFalse(read.enabled);
    }

    @Test
    public void testSettingsWithoutFiltersAreDefault() throws Exception {
        Element written = XmlSerializer.serialize(new GerritProjectSettings().getState(), new SkipDefaultsSerializationFilter());
        Assert.assertNull(written.getChild("filters"));
        // state written by a version which did not know the filters
        Element old = new Element("component");
        old.setAttribute("enabled", "true");
        Assert.assertTrue(XmlSerializer.deserialize(old, GerritProjectSettings.ProjectState.class).filters.isEmpty());
    }
}
