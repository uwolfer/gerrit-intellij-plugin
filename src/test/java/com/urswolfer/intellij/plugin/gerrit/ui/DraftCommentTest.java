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

package com.urswolfer.intellij.plugin.gerrit.ui;

import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DraftCommentTest {

    @Test
    public void testSortedByPathThenLine() {
        Map<String, List<CommentInfo>> drafts = new HashMap<>();
        drafts.put("src/B.java", Arrays.asList(comment("b5", 5, Side.REVISION)));
        drafts.put("src/A.java", Arrays.asList(
            comment("a65", 65, Side.REVISION),
            comment("a34", 34, Side.REVISION),
            comment("aFile", null, Side.REVISION),
            comment("a34base", 34, Side.PARENT)));
        drafts.put(DraftComment.COMMIT_MSG, Arrays.asList(comment("msg", 7, Side.REVISION)));
        drafts.put(DraftComment.PATCHSET_LEVEL, Arrays.asList(comment("ps", null, Side.REVISION)));

        List<String> ids = DraftComment.sorted(drafts).stream()
            .map(draft -> draft.getComment().id)
            .collect(Collectors.toList());

        Assert.assertEquals(ids, Arrays.asList("ps", "msg", "aFile", "a34base", "a34", "a65", "b5"));
    }

    @Test
    public void testDraftInputKeepsPositionAndPathFromKey() {
        CommentInfo comment = comment("id1", 12, Side.PARENT);
        comment.parent = 1;
        comment.inReplyTo = "other";
        comment.unresolved = true;
        comment.range = new Comment.Range();
        comment.range.startLine = 12;
        comment.range.endLine = 13;
        DraftComment draft = DraftComment.sorted(Map.of("src/A.java", Arrays.asList(comment))).get(0);

        DraftInput input = draft.toDraftInput("new text");

        Assert.assertEquals(input.id, "id1");
        Assert.assertEquals(input.path, "src/A.java");
        Assert.assertEquals(input.side, Side.PARENT);
        Assert.assertEquals(input.parent, Integer.valueOf(1));
        Assert.assertEquals(input.line, Integer.valueOf(12));
        Assert.assertSame(input.range, comment.range);
        Assert.assertEquals(input.inReplyTo, "other");
        Assert.assertEquals(input.unresolved, Boolean.TRUE);
        Assert.assertEquals(input.message, "new text");
    }

    private static CommentInfo comment(String id, Integer line, Side side) {
        CommentInfo comment = new CommentInfo();
        comment.id = id;
        comment.line = line;
        comment.side = side;
        comment.message = id;
        return comment;
    }
}
