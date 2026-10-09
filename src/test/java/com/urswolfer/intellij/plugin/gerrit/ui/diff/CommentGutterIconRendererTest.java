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

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.junit.Assert;
import org.testng.annotations.Test;

public class CommentGutterIconRendererTest {

    @Test
    public void testDraftAndPublishedCommentsHaveTheirOwnStripeColor() {
        Assert.assertSame(CommentGutterIconRenderer.DRAFT_STRIPE_COLOR,
            CommentGutterIconRenderer.getErrorStripeColor(draft("x")));
        Assert.assertSame(CommentGutterIconRenderer.PUBLISHED_STRIPE_COLOR,
            CommentGutterIconRenderer.getErrorStripeColor(published("Jane", "x")));
    }

    @Test
    public void testStripeTooltipEscapesAuthorAndMessage() {
        Assert.assertEquals("<b>J&lt;ane&gt;</b>: a &lt;b&gt; &amp; c",
            CommentGutterIconRenderer.getErrorStripeTooltip(published("J<ane>", "a <b> & c")));
    }

    @Test
    public void testStripeTooltipMarksDraftsAndShowsTheStartOfALongMessage() {
        String tooltip = CommentGutterIconRenderer.getErrorStripeTooltip(
            draft("first line\n\nsecond " + StringUtil.repeat("x", 100)));
        Assert.assertTrue(tooltip, tooltip.startsWith("<b>Myself</b> (draft): first line second xxx"));
        Assert.assertFalse(tooltip, tooltip.contains(StringUtil.repeat("x", 100)));
        Assert.assertEquals(tooltip, 80, tooltip.length() - "<b>Myself</b> (draft): ".length());
    }

    @Test
    public void testStripeTooltipNamesAnAuthorWithoutAFullNameByUsername() {
        CommentInfo comment = published(null, "x");
        comment.author.username = "ci-bot";
        Assert.assertEquals("<b>ci-bot</b>: x", CommentGutterIconRenderer.getErrorStripeTooltip(comment));
    }

    private static CommentInfo draft(String message) {
        CommentInfo comment = new CommentInfo();
        comment.message = message;
        return comment;
    }

    private static CommentInfo published(String author, String message) {
        CommentInfo comment = draft(message);
        comment.author = new AccountInfo(1);
        comment.author.name = author;
        return comment;
    }
}
