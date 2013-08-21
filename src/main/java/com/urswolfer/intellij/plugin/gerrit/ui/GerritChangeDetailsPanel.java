/*
 * Copyright 2013 Urs Wolfer
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


import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.UIVcsUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeMessageInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

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
    private static final String MULTIPLE_SELECTED = "multiple_selected";

    private final JPanel myPanel;

    private final MyPresentationData myPresentationData;

    private JEditorPane myJEditorPane;

    public GerritChangeDetailsPanel(final Project myProject) {
        myPanel = new JPanel(new CardLayout());
        myPanel.add(UIVcsUtil.errorPanel("Nothing selected", false), NOTHING_SELECTED);
        myPanel.add(UIVcsUtil.errorPanel("Loading...", false), LOADING);
        myPanel.add(UIVcsUtil.errorPanel("Several commits selected", false), MULTIPLE_SELECTED);

        myPresentationData = new MyPresentationData(myProject);

        final JPanel wrapper = new JPanel(new BorderLayout());

        myJEditorPane = new JEditorPane(UIUtil.HTML_MIME, "");
        myJEditorPane.setPreferredSize(new Dimension(150, 100));
        myJEditorPane.setEditable(false);
        myJEditorPane.setBackground(UIUtil.getComboBoxDisabledBackground());

        final JBScrollPane tableScroll = new JBScrollPane(myJEditorPane);
        tableScroll.setBorder(null);
        myJEditorPane.setBorder(null);
        wrapper.add(tableScroll, SwingConstants.CENTER);
        myJEditorPane.setBackground(UIUtil.getTableBackground());
        wrapper.setBackground(UIUtil.getTableBackground());

        myPanel.add(wrapper, DATA);
        ((CardLayout) myPanel.getLayout()).show(myPanel, NOTHING_SELECTED);
    }

    public void severalSelected() {
        ((CardLayout) myPanel.getLayout()).show(myPanel, MULTIPLE_SELECTED);
    }

    public void nothingSelected() {
        ((CardLayout) myPanel.getLayout()).show(myPanel, NOTHING_SELECTED);
    }


    public void loading() {
        ((CardLayout) myPanel.getLayout()).show(myPanel, LOADING);
    }

    public void setData(@NotNull final ChangeInfo changeInfo) {
        myPresentationData.setCommit(changeInfo);
        ((CardLayout) myPanel.getLayout()).show(myPanel, DATA);

        changeDetailsText();
    }

    private void changeDetailsText() {
        if (myPresentationData.isReady()) {
            myJEditorPane.setText(myPresentationData.getText());
            myPanel.revalidate();
            myPanel.repaint();
        }
    }

    public JPanel getComponent() {
        return myPanel;
    }

    private static class MyPresentationData {
        private String myStartPattern;
        private final String myEndPattern = "</table></body></html>";
        private final Project myProject;

        private MyPresentationData(final Project project) {
            myProject = project;
        }

        public void setCommit(final ChangeInfo changeInfo) {
            final String comment = IssueLinkHtmlRenderer.formatTextWithLinks(myProject, changeInfo.getSubject());

            final StringBuilder sb = new StringBuilder().append("<html><head>").append(UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()))
                    .append("</head><body><table>")
                    .append("<tr valign=\"top\"><td><i>Change-Id:</i></td><td><b>").append(changeInfo.getChangeId()).append("</b></td></tr>")
                    .append("<tr valign=\"top\"><td><i>Owner:</i></td><td>").append(changeInfo.getOwner().getName()).append("</td></tr>")
                    .append("<tr valign=\"top\"><td><i>Project:</i></td><td>").append(changeInfo.getProject()).append("</td></tr>")
                    .append("<tr valign=\"top\"><td><i>Branch:</i></td><td>").append(changeInfo.getBranch()).append("</td></tr>")
                    .append("<tr valign=\"top\"><td><i>Topic:</i></td><td>").append(changeInfo.getTopic() != null ? changeInfo.getTopic() : "").append("</td></tr>")
                    .append("<tr valign=\"top\"><td><i>Uploaded:</i></td><td>")
                    .append(DateFormatUtil.formatPrettyDateTime(changeInfo.getCreated()))
                    .append("</td></tr>")
                    .append("<tr valign=\"top\"><td><i>Updated:</i></td><td>")
                    .append(DateFormatUtil.formatPrettyDateTime(changeInfo.getUpdated()))
                    .append("</td></tr>")
                    .append("<tr valign=\"top\"><td><i>Status:</i></td><td>").append(changeInfo.getStatus()).append("</td></tr>")
                    .append("<tr valign=\"top\"><td><i>Description:</i></td><td><b>").append(comment).append("</b></td></tr>");

            if (changeInfo.getMessages() != null && changeInfo.getMessages().length > 0) {
                sb.append("<tr valign=\"top\"><td><i>Comments:</i></td><td>");
                for (ChangeMessageInfo changeMessageInfo : changeInfo.getMessages()) {
                    sb.append(changeMessageInfo.getAuthor().getName()).append(": ");
                    sb.append(changeMessageInfo.getMessage()).append("<br/>");
                }
                sb.append("</td></tr>");
            }

            myStartPattern = sb.toString();
        }

        public boolean isReady() {
            return myStartPattern != null;
        }

        public String getText() {
            return myStartPattern + myEndPattern;
        }
    }
}
