/*
 * Copyright 2013 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit.rest;

import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.FetchInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.urswolfer.gerrit.client.rest.http.HttpStatusException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * @author Urs Wolfer
 */
public class GerritUtilTest {

    @Test
    public void testFilePathsIncludeOldPathOfRenameOnly() {
        // as Gerrit 3.14 answers, but for the copy, which Gerrit lists the same way
        JsonObject files = JsonParser.parseString("{"
            + "\"/COMMIT_MSG\":{\"lines_inserted\":1,\"lines_deleted\":1,\"size_delta\":0,\"size\":269},"
            + "\"c2.txt\":{\"status\":\"R\",\"old_path\":\"c.txt\",\"size_delta\":0,\"size\":51},"
            + "\"dir/b.txt\":{\"status\":\"D\",\"lines_deleted\":1,\"size_delta\":-3,\"size\":0},"
            + "\"copy.txt\":{\"status\":\"C\",\"old_path\":\"source.txt\",\"size_delta\":5,\"size\":5}"
            + "}").getAsJsonObject();
        Assert.assertEquals(GerritUtil.filePaths(files),
            new HashSet<>(Arrays.asList("/COMMIT_MSG", "c2.txt", "c.txt", "dir/b.txt", "copy.txt")));
    }

    @Test
    public void testFilePathsSkipUnexpectedEntries() {
        JsonObject files = JsonParser.parseString("{"
            + "\"a.txt\":null,"
            + "\"b.txt\":{\"status\":null,\"old_path\":\"x.txt\"},"
            + "\"c.txt\":{\"status\":\"R\",\"old_path\":{}}"
            + "}").getAsJsonObject();
        Assert.assertEquals(GerritUtil.filePaths(files), new HashSet<>(Arrays.asList("a.txt", "b.txt", "c.txt")));
    }

    @Test
    public void testFetchInfoFromGerrit() {
        RevisionInfo revisionInfo = new RevisionInfo("refs/changes/34/1234/2");
        FetchInfo ssh = new FetchInfo("ssh://gerrit.server:29418/project", "refs/changes/34/1234/2");
        revisionInfo.fetch = new LinkedHashMap<>();
        revisionInfo.fetch.put("ssh", ssh);
        revisionInfo.fetch.put("http", new FetchInfo("https://gerrit.server/project", "refs/changes/34/1234/2"));

        Assert.assertSame(GerritUtil.getFirstFetchInfo(revisionInfo, () -> {
            throw new AssertionError("the fetch information of Gerrit is used as is");
        }), ssh);
    }

    @Test
    public void testFetchInfoWithoutDownloadSchemes() {
        RevisionInfo revisionInfo = new RevisionInfo("refs/changes/34/1234/2");
        revisionInfo.fetch = Collections.emptyMap();

        FetchInfo fetchInfo = GerritUtil.getFirstFetchInfo(revisionInfo, () -> "https://gerrit.server");

        Assert.assertEquals(fetchInfo.url, "https://gerrit.server");
        Assert.assertEquals(fetchInfo.ref, "refs/changes/34/1234/2");

        revisionInfo.fetch = null;
        Assert.assertEquals(GerritUtil.getFirstFetchInfo(revisionInfo, () -> "https://gerrit.server").ref,
                "refs/changes/34/1234/2");
    }

    @Test
    public void testFetchInfoWithoutRef() {
        RevisionInfo revisionInfo = new RevisionInfo();

        Assert.assertNull(GerritUtil.getFirstFetchInfo(revisionInfo, () -> "https://gerrit.server"));
        Assert.assertNull(GerritUtil.getFirstFetchInfo(null, () -> "https://gerrit.server"));
    }

    @Test
    public void testOptionGerritRejectsIsDropped() {
        Changes.QueryRequest request = query().withOptions(EnumSet.of(ListChangesOption.LABELS,
            ListChangesOption.CHANGE_ACTIONS, ListChangesOption.CURRENT_ACTIONS, ListChangesOption.SUBMITTABLE));

        Assert.assertTrue(GerritUtil.withoutUnsupportedOption(request, rejected("SUBMITTABLE")));
        Assert.assertEquals(request.getOptions(), EnumSet.of(ListChangesOption.LABELS,
            ListChangesOption.CHANGE_ACTIONS, ListChangesOption.CURRENT_ACTIONS));

        // Gerrit 2.9 names the next one on the retry
        Assert.assertTrue(GerritUtil.withoutUnsupportedOption(request, rejected("CHANGE_ACTIONS")));
        Assert.assertEquals(request.getOptions(),
            EnumSet.of(ListChangesOption.LABELS, ListChangesOption.CURRENT_ACTIONS));
    }

    @Test
    public void testQueryIsRetriedWithoutEachOptionGerritRejects() {
        List<Set<ListChangesOption>> sent = new ArrayList<>();
        Changes.QueryRequest request = new Changes.QueryRequest() {
            @Override
            public List<ChangeInfo> get() throws RestApiException {
                Set<ListChangesOption> options = EnumSet.copyOf(getOptions());
                sent.add(options);
                for (ListChangesOption unknown
                        : EnumSet.of(ListChangesOption.CHANGE_ACTIONS, ListChangesOption.SUBMITTABLE)) {
                    if (options.contains(unknown)) {
                        throw new HttpStatusException(400, "Bad Request", rejected(unknown.name()));
                    }
                }
                return Collections.emptyList();
            }
        }.withOptions(EnumSet.of(ListChangesOption.LABELS,
            ListChangesOption.CHANGE_ACTIONS, ListChangesOption.CURRENT_ACTIONS, ListChangesOption.SUBMITTABLE));

        Assert.assertEquals(new GerritUtil().queryChanges(request, null), Collections.emptyList());
        Assert.assertEquals(sent.size(), 3);
        Assert.assertEquals(sent.get(2), EnumSet.of(ListChangesOption.LABELS, ListChangesOption.CURRENT_ACTIONS));
    }

    @Test
    public void testOptionWhichIsGoneAlreadyIsNotRetried() {
        Changes.QueryRequest request = query().withOptions(EnumSet.of(ListChangesOption.LABELS));

        Assert.assertFalse(GerritUtil.withoutUnsupportedOption(request, rejected("SUBMITTABLE")));
    }

    @Test
    public void testOtherBadRequestIsNotRetried() {
        Changes.QueryRequest request = query().withOptions(EnumSet.of(ListChangesOption.SUBMITTABLE));

        Assert.assertFalse(GerritUtil.withoutUnsupportedOption(request,
            "Request not successful. Message: Bad Request. Status-Code: 400. Content:\n"
                + "line 1:5 no viable alternative at input 'foo'."));
        Assert.assertEquals(request.getOptions(), EnumSet.of(ListChangesOption.SUBMITTABLE));
    }

    private static Changes.QueryRequest query() {
        return new Changes.QueryRequest() {
            @Override
            public List<ChangeInfo> get() {
                throw new AssertionError("not sent");
            }
        };
    }

    // what gerrit-rest-java-client's HttpStatusException carries for the 400 of an option Gerrit does not know
    private static String rejected(String option) {
        return String.format("Request not successful. Message: Bad Request. Status-Code: 400. Content:%n"
            + "\"%s\" is not a valid value for \"-o\".", option);
    }

    @Test
    public void testProjectQueryForEachProjectOnce() {
        Assert.assertEquals(
            GerritUtil.appendProjectQueryParts("is:open", Arrays.asList("a", "b/c", "a"), 4000),
            Collections.singletonList("is:open+(project:a+OR+project:b%2Fc)"));
    }

    @Test
    public void testProjectQueryWithoutProjects() {
        Assert.assertEquals(GerritUtil.appendProjectQueryParts("is:open", Collections.emptyList(), 4000),
            Collections.singletonList("is:open"));
        Assert.assertEquals(GerritUtil.appendProjectQueryParts("", Collections.singletonList("a"), 4000),
            Collections.singletonList("(project:a)"));
    }

    @Test
    public void testLongProjectQueryIsSplit() {
        // "is:open+(project:a+OR+project:b)" is 32 characters
        Assert.assertEquals(
            GerritUtil.appendProjectQueryParts("is:open", Arrays.asList("a", "b", "c", "d", "e"), 32),
            Arrays.asList(
                "is:open+(project:a+OR+project:b)",
                "is:open+(project:c+OR+project:d)",
                "is:open+(project:e)"));
    }
}
