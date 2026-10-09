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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.intellij.openapi.util.text.StringUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.util.Whitespace;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The reviewers and CCs typed into the dialog which creates a feature merge change. Gerrit's change creation takes
 * none, so they are added once the change exists.
 */
final class MergeChangeReviewers {
    static final String SEPARATOR = ",";

    private MergeChangeReviewers() {}

    /**
     * The accounts of a comma separated list, as the push dialog splits it: a name can hold blanks, so only the comma
     * separates. A name which stands in both lists stays a reviewer.
     */
    static List<ReviewerInput> inputs(String reviewers, String ccs) {
        Set<String> reviewerNames = names(reviewers);
        Set<String> ccNames = names(ccs);
        ccNames.removeAll(reviewerNames);
        List<ReviewerInput> inputs = new ArrayList<>();
        for (String name : reviewerNames) {
            inputs.add(input(name, ReviewerState.REVIEWER));
        }
        for (String name : ccNames) {
            inputs.add(input(name, ReviewerState.CC));
        }
        return inputs;
    }

    static Set<String> names(String text) {
        Set<String> names = new LinkedHashSet<>();
        for (String part : text.split(SEPARATOR)) {
            // not String#trim(): a pasted name can be surrounded by whitespace which it does not remove
            String name = Whitespace.trim(part);
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    private static ReviewerInput input(String name, ReviewerState state) {
        ReviewerInput input = new ReviewerInput();
        input.reviewer = name;
        input.state = state;
        return input;
    }

    /**
     * The text of the notification of a created change, with the accounts which could not be added when there are
     * any, each with the reason Gerrit gave.
     *
     * @param failures the reason by account name
     */
    static String createdText(String number, String subject, Map<String, String> failures) {
        String created = GerritBundle.message("merge.created.text", number, StringUtil.escapeXmlEntities(subject));
        if (failures.isEmpty()) {
            return created;
        }
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, String> failure : failures.entrySet()) {
            entries.add(GerritBundle.message("merge.reviewers.failure",
                StringUtil.escapeXmlEntities(failure.getKey()), StringUtil.escapeXmlEntities(reason(failure.getValue()))));
        }
        return created + "<br>" + GerritBundle.message("merge.reviewers.failed") + "<br>" + String.join("<br>", entries);
    }

    /**
     * Gerrit answers an account it cannot resolve with a 400 and a JSON body, which the client puts in the message of
     * its exception: the error of that body is what the user needs to read.
     */
    static String reason(String text) {
        int start = text.indexOf('{');
        if (start != -1) {
            try {
                // a reader reads the one object and leaves what follows it, such as the full stop of the message
                JsonObject body = new Gson().fromJson(new JsonReader(new StringReader(text.substring(start))), JsonObject.class);
                JsonElement error = body == null ? null : body.get("error");
                if (error != null && error.isJsonPrimitive()) {
                    return Whitespace.trim(error.getAsString()).replaceAll("\\.?\\s*\\R\\s*", ". ");
                }
            } catch (RuntimeException ignored) {
                // not the body of an error: the text is shown as it is
            }
        }
        return text;
    }
}
