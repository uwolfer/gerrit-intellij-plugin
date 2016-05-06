/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.urswolfer.intellij.plugin.gerrit.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.ClickListener;
import com.intellij.ui.RoundedLineBorder;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.*;

/**
 * Merge of original BasePopupAction (IntelliJ < 14) and com.intellij.vcs.log.ui.filter.FilterPopupComponent.
 */
public abstract class BasePopupAction extends DumbAwareAction implements CustomComponentAction {
    private static final int GAP_BEFORE_ARROW = 3;
    private static final int BORDER_SIZE = 2;
    private static final Border INNER_MARGIN_BORDER = BorderFactory.createEmptyBorder(2, 2, 2, 2);
    private static final Border FOCUSED_BORDER = createFocusedBorder();
    private static final Border UNFOCUSED_BORDER = createUnfocusedBorder();

    private final JLabel myFilterNameLabel;
    private final JLabel myFilterValueLabel;
    private final JPanel myPanel;

    public BasePopupAction(String filterName) {
        myFilterNameLabel = new JLabel(filterName + ": ");

        myFilterValueLabel = new JLabel();

        myPanel = new JPanel();
        BoxLayout layout = new BoxLayout(myPanel, BoxLayout.X_AXIS);
        myPanel.setLayout(layout);
        myPanel.setFocusable(true);
        myPanel.setBorder(UNFOCUSED_BORDER);

        myPanel.add(myFilterNameLabel);
        myPanel.add(myFilterValueLabel);
        myPanel.add(Box.createHorizontalStrut(GAP_BEFORE_ARROW));
        myPanel.add(new JLabel(AllIcons.Ide.Statusbar_arrows));

        showPopupMenuOnClick();
        showPopupMenuFromKeyboard();
        indicateHovering();
        indicateFocusing();
    }

    private DefaultActionGroup createActionGroup() {
        final DefaultActionGroup group = new DefaultActionGroup();
        createActions(new Consumer<AnAction>() {
            @Override
            public void consume(AnAction anAction) {
                group.add(anAction);
            }
        });
        return group;
    }

    protected abstract void createActions(final Consumer<AnAction> actionConsumer);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
    }

    @Override
    public JComponent createCustomComponent(Presentation presentation) {
        return myPanel;
    }

    protected void updateFilterValueLabel(String text) {
        myFilterValueLabel.setText(text);
    }

    protected JLabel getFilterValueLabel() {
        return myFilterValueLabel;
    }

    private void indicateFocusing() {
        myPanel.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(@NotNull FocusEvent e) {
                myPanel.setBorder(FOCUSED_BORDER);
            }

            @Override
            public void focusLost(@NotNull FocusEvent e) {
                myPanel.setBorder(UNFOCUSED_BORDER);
            }
        });
    }

    private void showPopupMenuFromKeyboard() {
        myPanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(@NotNull KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_DOWN) {
                    showPopupMenu();
                }
            }
        });
    }

    private void showPopupMenuOnClick() {
        new ClickListener() {
            @Override
            public boolean onClick(@NotNull MouseEvent event, int clickCount) {
                showPopupMenu();
                return true;
            }
        }.installOn(myPanel);
    }

    private void indicateHovering() {
        myPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(@NotNull MouseEvent e) {
                setOnHoverForeground();
            }

            @Override
            public void mouseExited(@NotNull MouseEvent e) {
                setDefaultForeground();
            }
        });
    }

    private void setDefaultForeground() {
        myFilterNameLabel.setForeground(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getInactiveTextColor());
        myFilterValueLabel.setForeground(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() :
            UIUtil.getInactiveTextColor().darker().darker());
    }

    private void setOnHoverForeground() {
        myFilterNameLabel.setForeground(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getTextAreaForeground());
        myFilterValueLabel.setForeground(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getTextFieldForeground());
    }


    private void showPopupMenu() {
        ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, createActionGroup(),
            DataManager.getInstance().getDataContext(myPanel), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
        popup.showUnderneathOf(myPanel);
    }

    private static Border createFocusedBorder() {
        return BorderFactory.createCompoundBorder(new RoundedLineBorder(UIUtil.getHeaderActiveColor(), 10, BORDER_SIZE),
            INNER_MARGIN_BORDER);
    }

    private static Border createUnfocusedBorder() {
        return BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE),
            INNER_MARGIN_BORDER);
    }
}
