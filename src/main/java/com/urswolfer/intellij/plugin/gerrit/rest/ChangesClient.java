/*
 * Copyright 2013-2014 Urs Wolfer
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


import com.urswolfer.intellij.plugin.gerrit.rest.bean.*;

import java.util.List;
import java.util.TreeMap;


/**
 * @author Urs Wolfer
 */
public interface ChangesClient {

    List<ChangeInfo> getChanges() throws GerritClientException;

    List<ChangeInfo> getChanges(String query) throws GerritClientException;

    ChangeInfo getChangeDetails(String changeId) throws GerritClientException;

    TreeMap<String, List<CommentInfo>> getComments(String changeId, String revision) throws GerritClientException;

    void postReview(String changeId, String revision, ReviewInput reviewInput) throws GerritClientException;

    void postSubmit(String changeId, SubmitInput submitInput) throws GerritClientException;

    void postAbandon(String changeId, AbandonInput abandonInput) throws GerritClientException;

    void changeReviewed(String changeId, String revision, String filePath, boolean reviewed) throws GerritClientException;
}
