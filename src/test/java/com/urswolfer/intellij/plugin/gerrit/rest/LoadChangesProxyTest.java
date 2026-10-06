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

package com.urswolfer.intellij.plugin.gerrit.rest;

import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.common.ChangeInfo;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LoadChangesProxyTest {

    @Test
    public void testSingleQueryIsOneRequestPerPage() {
        FakeQuery query = new FakeQuery(change(1, 50), change(2, 40), change(3, 30));
        LoadChangesProxy proxy = new LoadChangesProxy(Arrays.asList(query), new GerritUtil(), null);

        Assert.assertEquals(numbers(proxy.next(2)), Arrays.asList(1, 2));
        Assert.assertEquals(query.requests, Arrays.asList("0+2"));
        Assert.assertTrue(proxy.hasMore());

        Assert.assertEquals(numbers(proxy.next(2)), Arrays.asList(3));
        Assert.assertEquals(query.requests, Arrays.asList("0+2", "2+2"));
        Assert.assertFalse(proxy.hasMore());
    }

    @Test
    public void testQueriesAreMergedNewestFirstAcrossPages() {
        FakeQuery first = new FakeQuery(change(1, 100), change(2, 80), change(3, 60), change(4, 40));
        FakeQuery second = new FakeQuery(change(5, 90), change(6, 70), change(7, 50));
        LoadChangesProxy proxy = new LoadChangesProxy(Arrays.asList(first, second), new GerritUtil(), null);

        Assert.assertEquals(numbers(proxy.next(3)), Arrays.asList(1, 5, 2));
        Assert.assertEquals(numbers(proxy.next(3)), Arrays.asList(6, 3, 7));
        Assert.assertTrue(proxy.hasMore());
        Assert.assertEquals(numbers(proxy.next(3)), Arrays.asList(4));
        Assert.assertFalse(proxy.hasMore());
    }

    @Test
    public void testSameUpdateListsHigherNumberFirst() {
        FakeQuery first = new FakeQuery(change(3, 10));
        FakeQuery second = new FakeQuery(change(8, 10));
        LoadChangesProxy proxy = new LoadChangesProxy(Arrays.asList(first, second), new GerritUtil(), null);

        Assert.assertEquals(numbers(proxy.next(25)), Arrays.asList(8, 3));
    }

    @Test
    public void testChangeIsListedOnce() {
        FakeQuery first = new FakeQuery(change(1, 100), change(2, 80), change(3, 60));
        FakeQuery second = new FakeQuery(change(2, 80), change(4, 70), change(3, 60));
        LoadChangesProxy proxy = new LoadChangesProxy(Arrays.asList(first, second), new GerritUtil(), null);

        Assert.assertEquals(numbers(proxy.next(2)), Arrays.asList(1, 2));
        Assert.assertEquals(numbers(proxy.next(25)), Arrays.asList(4, 3));
        Assert.assertFalse(proxy.hasMore());
    }

    @Test
    public void testFailedQueryStopsAllQueries() {
        FakeQuery first = new FakeQuery(change(1, 100), change(2, 80));
        FakeQuery failing = new FakeQuery(change(3, 90));
        failing.failing = true;
        FakeQuery last = new FakeQuery(change(4, 70));
        LoadChangesProxy proxy = new LoadChangesProxy(Arrays.asList(first, failing, last), new GerritUtil(), null);

        Assert.assertTrue(proxy.next(25).isEmpty());
        Assert.assertTrue(last.requests.isEmpty());
        Assert.assertFalse(proxy.hasMore());
    }

    @Test
    public void testSeveralQueriesAreAskedForAPageEach() {
        FakeQuery first = new FakeQuery(change(1, 100));
        FakeQuery second = new FakeQuery(change(2, 90));
        LoadChangesProxy proxy = new LoadChangesProxy(Arrays.asList(first, second), new GerritUtil(), null);

        proxy.next(100);
        Assert.assertEquals(first.requests, Arrays.asList("0+25"));
        Assert.assertEquals(second.requests, Arrays.asList("0+25"));
    }

    private static ChangeInfo change(int number, long updated) {
        ChangeInfo change = new ChangeInfo();
        change._number = number;
        change.updated = new Timestamp(updated);
        return change;
    }

    private static List<Integer> numbers(List<ChangeInfo> changes) {
        return changes.stream().map(change -> change._number).collect(Collectors.toList());
    }

    /**
     * Answers a page of its changes like Gerrit does, flagging the last one when more follow.
     */
    private static final class FakeQuery extends Changes.QueryRequest {
        private final List<ChangeInfo> changes;
        private final List<String> requests = new ArrayList<>();
        private boolean failing;

        private FakeQuery(ChangeInfo... changes) {
            this.changes = Arrays.asList(changes);
        }

        @Override
        public List<ChangeInfo> get() {
            requests.add(getStart() + "+" + getLimit());
            if (failing) {
                // what GerritUtil.queryChanges gives for a failed request, once it has reported it
                return null;
            }
            int end = Math.min(changes.size(), getStart() + getLimit());
            List<ChangeInfo> page = new ArrayList<>();
            for (ChangeInfo change : changes.subList(getStart(), end)) {
                ChangeInfo copy = change(change._number, change.updated.getTime());
                page.add(copy);
            }
            if (!page.isEmpty()) {
                page.get(page.size() - 1)._moreChanges = end < changes.size() ? true : null;
            }
            return page;
        }
    }
}
