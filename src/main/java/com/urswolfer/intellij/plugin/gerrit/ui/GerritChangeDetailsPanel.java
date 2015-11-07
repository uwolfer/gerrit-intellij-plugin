/*
 * Copyright 2013-2014 Urs Wolfer
 * Copyright 2000-2011 JetBrains s.r.o.
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


import com.google.common.collect.Lists;
import com.google.gerrit.extensions.common.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.UIVcsUtil;
import com.urswolfer.intellij.plugin.gerrit.util.TextToHtml;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

/**
 * Parts based on:
 * git4idea.history.wholeTree.GitLogDetailsPanel
 *
 * @author Urs Wolfer
 */
public class GerritChangeDetailsPanel {
    private final static String NOTHING_SELECTED = "nothingSelected";
    private final static String LOADING = "loading";
    private final static String DATA = "data";
    private final static String MULTIPLE_SELECTED = "multiple_selected";
    private final static ThreadLocal<DecimalFormat> APPROVAL_VALUE_FORMAT = new ThreadLocal<DecimalFormat>() {
        @Override
        protected DecimalFormat initialValue() {
            return new DecimalFormat("+#;-#");
        }
    };

    private final JPanel panel;

    private final MyPresentationData presentationData;

    private final JEditorPane jEditorPane;

    public GerritChangeDetailsPanel(final Project project) {
        panel = new JPanel(new CardLayout());
        panel.add(UIVcsUtil.errorPanel("Nothing selected", false), NOTHING_SELECTED);
        panel.add(UIVcsUtil.errorPanel("Loading...", false), LOADING);
        panel.add(UIVcsUtil.errorPanel("Several commits selected", false), MULTIPLE_SELECTED);

        presentationData = new MyPresentationData(project);

        final JPanel wrapper = new JPanel(new BorderLayout());

        jEditorPane = new JEditorPane(UIUtil.HTML_MIME, "");
        jEditorPane.setPreferredSize(new Dimension(150, 100));
        jEditorPane.setEditable(false);
        jEditorPane.setBackground(UIUtil.getComboBoxDisabledBackground());

        final JBScrollPane tableScroll = new JBScrollPane(jEditorPane);
        tableScroll.setBorder(null);
        jEditorPane.setBorder(null);
        wrapper.add(tableScroll, SwingConstants.CENTER);
        jEditorPane.setBackground(UIUtil.getTableBackground());
        wrapper.setBackground(UIUtil.getTableBackground());

        panel.add(wrapper, DATA);
        ((CardLayout) panel.getLayout()).show(panel, NOTHING_SELECTED);
    }

    public void severalSelected() {
        ((CardLayout) panel.getLayout()).show(panel, MULTIPLE_SELECTED);
    }

    public void nothingSelected() {
        ((CardLayout) panel.getLayout()).show(panel, NOTHING_SELECTED);
    }


    public void loading() {
        ((CardLayout) panel.getLayout()).show(panel, LOADING);
    }

    public void setData(@NotNull final ChangeInfo changeInfo) {
        presentationData.setCommit(changeInfo);
        ((CardLayout) panel.getLayout()).show(panel, DATA);

        changeDetailsText();
    }

    private void changeDetailsText() {
        if (presentationData.isReady()) {
            jEditorPane.setText(presentationData.getText());
            panel.revalidate();
            panel.repaint();
            jEditorPane.setCaretPosition(0);
        }
    }

    public JPanel getComponent() {
        return panel;
    }

    private static class MyPresentationData {
        private String startPattern;
        private final String endPattern = "</table></body></html>";
        private final Project project;

        private MyPresentationData(final Project project) {
            this.project = project;
        }

        public void setCommit(final ChangeInfo changeInfo) {
            StringBuilder stringBuilder = new StringBuilder();
            addMetaData(changeInfo, stringBuilder);
            addLabels(changeInfo, stringBuilder);
            addMessages(changeInfo, stringBuilder);
            startPattern = stringBuilder.toString();
        }

        private void addMetaData(ChangeInfo changeInfo, StringBuilder sb) {
            final String comment = IssueLinkHtmlRenderer.formatTextWithLinks(project, changeInfo.subject);
            sb.append("<html><head>").append(UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()))
                    .append("</head><body><table>")
                    .append("<tr valign=\"top\"><td><i>Change-Id:</i></td><td><b>").append(changeInfo.changeId).append("</b></td></tr>")
                    .append("<tr valign=\"top\"><td><i>Change #:</i></td><td><b>").append(changeInfo._number).append("</b></td></tr>")
                    .append("<tr valign=\"top\"><td><i>Owner:</i></td><td>").append(changeInfo.owner != null ? changeInfo.owner.name : "").append("</td></tr>")
                    .append("<tr valign=\"top\"><td><i>Project:</i></td><td>").append(changeInfo.project).append("</td></tr>")
                    .append("<tr valign=\"top\"><td><i>Branch:</i></td><td>").append(changeInfo.branch).append("</td></tr>")
                    .append("<tr valign=\"top\"><td><i>Topic:</i></td><td>").append(changeInfo.topic != null ? changeInfo.topic : "").append("</td></tr>")
                    .append("<tr valign=\"top\"><td><i>Uploaded:</i></td><td>")
                    .append(changeInfo.created != null ? DateFormatUtil.formatPrettyDateTime(changeInfo.created) : "")
                    .append("</td></tr>")
                    .append("<tr valign=\"top\"><td><i>Updated:</i></td><td>")
                    .append(changeInfo.updated != null ? DateFormatUtil.formatPrettyDateTime(changeInfo.updated) : "")
                    .append("</td></tr>")
                    .append("<tr valign=\"top\"><td><i>Status:</i></td><td>").append(changeInfo.status).append("</td></tr>")
                    .append("<tr valign=\"top\"><td><i>Description:</i></td><td><b>").append(comment).append("</b></td></tr>");
        }

        private void addLabels(ChangeInfo changeInfo, StringBuilder sb) {
            if (changeInfo.labels != null) {
                List<ApprovalInfo> ccAccounts = null;
                for (Map.Entry<String, LabelInfo> labelInfoEntry : changeInfo.labels.entrySet()) {
                    sb.append("<tr valign=\"top\"><td><i>").append(labelInfoEntry.getKey()).append(":</i></td><td>");
                    List<ApprovalInfo> all = labelInfoEntry.getValue().all;
                    if (ccAccounts == null) {
                        if (all != null) {
                            ccAccounts = Lists.newArrayList(all);
                        } else {
                            ccAccounts = Lists.newArrayList();
                        }
                    }
                    if (all != null) {
                        for (ApprovalInfo approvalInfo : all) {
                            if (approvalInfo.value != null && approvalInfo.value != 0) {
                                sb.append("<b>").append(approvalInfo.name).append("</b>").append(": ");
                                sb.append(APPROVAL_VALUE_FORMAT.get().format(approvalInfo.value)).append("<br/>");
                                ccAccounts.remove(approvalInfo); // remove accounts from CC which are already listed in a review section
                            }
                        }
                    }
                    sb.append("</td></tr>");
                }
                if (ccAccounts != null) {
                    sb.append("<tr valign=\"top\"><td><i>").append("CC").append(":</i></td><td>");
                    for (ApprovalInfo approvalInfo : ccAccounts) {
                        sb.append("<b>").append(approvalInfo.name).append("</b>").append("<br/>");
                    }
                }
            }
        }

        private void addMessages(ChangeInfo changeInfo, StringBuilder sb) {
            if (changeInfo.messages != null && changeInfo.messages.size() > 0) {
                sb.append("<tr valign=\"top\"><td><i>Comments:</i></td><td>");
                for (ChangeMessageInfo changeMessageInfo : changeInfo.messages) {
                    AccountInfo author = changeMessageInfo.author;
                    if (author != null && author.name != null) {
                        sb.append("<b>").append(author.name).append("</b>");
                        if (changeMessageInfo.date != null) {
                            sb.append(" (").append(DateFormatUtil.formatPrettyDateTime(changeMessageInfo.date)).append(')');
                        }
                        sb.append(": ");
                    }
                    sb.append(TextToHtml.textToHtml(changeMessageInfo.message)).append("<br/>");
                }
                sb.append("</td></tr>");
            }
        }

        public boolean isReady() {
            return startPattern != null;
        }

        public String getText() {
            return startPattern + endPattern;
        }
    }
}
