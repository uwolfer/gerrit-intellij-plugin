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

package com.urswolfer.intellij.plugin.gerrit.ui.filter;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SearchTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * @author Thomas Forrer
 */
public class FulltextFilter extends AbstractChangesFilter {

    private String value = "";

    @Override
    public AnAction getAction(final Project project) {
        return new SearchFieldAction();
    }

    @Override
    @Nullable
    public String getSearchQueryPart() {
        return !value.isEmpty() ? "(" + specialEncodeFulltextQuery(value) + ")" : null;
    }

    /**
     * Queries have some special encoding. {@code URLEncoder.encode(query, "UTF-8")} does not
     * produce correct encoding for the query. It (falsely) encodes brackets, which are expected
     * to remain in the query string as is... This implementation aims to encode only the most
     * commonly used character is the query.
     * @param query a query string to encode
     * @return an encoded version of the passed {@code query}
     */
    public static String specialEncodeFulltextQuery(String query) {
        return query
                // has to be encoded first, it would otherwise encode the percent signs introduced below again
                .replace("%", "%25")
                .replace("{", "%7B")
                .replace("}", "%7D")
                .replace("+", "%2B")
                .replace(' ', '+')
                .replace("\"", "%22")
                .replace("\\", "%5C")
                .replace("<", "%3C")
                .replace(">", "%3E")
                .replace("^", "%5E");
    }

    /** Toolbar widget hosting the filter text field; the field drives the updates, the action itself does nothing. */
    public final class SearchFieldAction extends AnAction implements CustomComponentAction {
        private final SearchTextField field;
        private final JPanel component;

        public SearchFieldAction() {
            super("Filter");
            field = new SearchTextField(true) {
                @Override
                protected boolean preprocessEventForTextField(KeyEvent e) {
                    if (KeyEvent.VK_ENTER == e.getKeyCode() || '\n' == e.getKeyChar()) {
                        e.consume();
                        addCurrentTextToHistory();
                        apply();
                    }
                    return super.preprocessEventForTextField(e);
                }

                @Override
                protected void onFocusLost() {
                    super.onFocusLost();
                    apply();
                }

                @Override
                protected void onFieldCleared() {
                    apply();
                }
            };
            JLabel label = new JLabel("Filter: ");
            label.setForeground(UIUtil.getInactiveTextColor());
            label.setBorder(JBUI.Borders.emptyLeft(3));
            component = new JPanel();
            component.setLayout(new BoxLayout(component, BoxLayout.X_AXIS));
            component.add(label);
            component.add(field);
        }

        private void apply() {
            String newValue = field.getText().trim();
            if (!newValue.equals(value)) {
                value = newValue;
                fireFilterChanged();
            }
        }

        @NotNull
        @Override
        public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
            return component;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
        }
    }
}
