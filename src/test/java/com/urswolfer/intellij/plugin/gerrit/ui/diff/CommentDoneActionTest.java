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

package com.urswolfer.intellij.plugin.gerrit.ui.diff;

import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import org.junit.Assert;
import org.testng.annotations.Test;

public class CommentDoneActionTest {

    @Test
    public void testDoneReplyResolvesTheThread() {
        CommentInfo unresolved = new CommentInfo();
        unresolved.id = "abc";
        unresolved.path = "src/Main.java";
        unresolved.line = 12;
        unresolved.side = Side.PARENT;
        unresolved.unresolved = true;

        DraftInput reply = CommentDoneAction.createDoneReply(unresolved);

        Assert.assertEquals(Boolean.FALSE, reply.unresolved);
        Assert.assertEquals("abc", reply.inReplyTo);
        Assert.assertEquals("Done", reply.message);
        Assert.assertEquals("src/Main.java", reply.path);
        Assert.assertEquals(Integer.valueOf(12), reply.line);
        Assert.assertEquals(Side.PARENT, reply.side);
    }
}
