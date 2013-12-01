/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.urswolfer.intellij.plugin.gerrit.ui.diff;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.external.DiffManagerImpl;
import com.intellij.openapi.diff.impl.external.FrameDiffTool;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * Copy of:
 * com.intellij.openapi.diff.impl.external.FrameDiffTool
 *
 * It allows to intercept DiffPanel creation (everything is private in FrameDiffTool).
 */
public class CustomizableFrameDiffTool extends FrameDiffTool {
    public void show(DiffRequest request) {
        Collection hints = request.getHints();
        boolean shouldOpenDialog = shouldOpenDialog(hints);
        if (shouldOpenDialog) {
            final DialogBuilder builder = new DialogBuilder(request.getProject());
            DiffPanelImpl diffPanel = createDiffPanelIfShouldShow(request, builder.getWindow(), builder, true);
            if (diffPanel == null) {
                Disposer.dispose(builder);
                return;
            }
            if (hints.contains(DiffTool.HINT_DIFF_IS_APPROXIMATE)) {
                diffPanel.setPatchAppliedApproximately(); // todo read only and not variants
            }
            final Runnable onOkRunnable = request.getOnOkRunnable();
            if (onOkRunnable != null){
                builder.setOkOperation(new Runnable() {
                    @Override
                    public void run() {
                        builder.getDialogWrapper().close(0);
                        onOkRunnable.run();
                    }
                });
            } else {
                builder.removeAllActions();
            }
            builder.setCenterPanel(diffPanel.getComponent());
            builder.setPreferredFocusComponent(diffPanel.getPreferredFocusedComponent());
            builder.setTitle(request.getWindowTitle());
            builder.setDimensionServiceKey(request.getGroupKey());

            new AnAction() {
                public void actionPerformed(final AnActionEvent e) {
                    builder.getDialogWrapper().close(0);
                }
            }.registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts("CloseContent")),
                    diffPanel.getComponent());
            showDiffDialog(builder, hints);
        }
        else {
            final FrameWrapper frameWrapper = new FrameWrapper(request.getProject(), request.getGroupKey());
            DiffPanelImpl diffPanel = createDiffPanelIfShouldShow(request, frameWrapper.getFrame(), frameWrapper, true);
            if (diffPanel == null) {
                Disposer.dispose(frameWrapper);
                return;
            }
            if (hints.contains(DiffTool.HINT_DIFF_IS_APPROXIMATE)) {
                diffPanel.setPatchAppliedApproximately();
            }
            frameWrapper.setTitle(request.getWindowTitle());
            DiffUtil.initDiffFrame(diffPanel.getProject(), frameWrapper, diffPanel, diffPanel.getComponent());

            new AnAction() {
                public void actionPerformed(final AnActionEvent e) {
                    frameWrapper.getFrame().dispose();
                }
            }.registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts("CloseContent")),
                    diffPanel.getComponent());

            frameWrapper.show();
        }
    }

    @Nullable
    private DiffPanelImpl createDiffPanelIfShouldShow(DiffRequest request, Window window, @NotNull Disposable parentDisposable,
                                                      final boolean showMessage) {
        DiffPanelImpl diffPanel = (DiffPanelImpl) createDiffPanel(request, window, parentDisposable, this);
        if (checkNoDifferenceAndNotify(diffPanel, request, window, showMessage)) {
            Disposer.dispose(diffPanel);
            diffPanel = null;
        }
        return diffPanel;
    }

    static void showDiffDialog(DialogBuilder builder, Collection hints) {
        builder.showModal(!hints.contains(DiffTool.HINT_SHOW_NOT_MODAL_DIALOG));
    }

    static boolean shouldOpenDialog(Collection hints) {
        if (hints.contains(DiffTool.HINT_SHOW_MODAL_DIALOG)) return true;
        if (hints.contains(DiffTool.HINT_SHOW_NOT_MODAL_DIALOG)) return true;
        if (hints.contains(DiffTool.HINT_SHOW_FRAME)) return false;
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() instanceof JDialog;
    }

    private boolean checkNoDifferenceAndNotify(DiffPanel diffPanel, DiffRequest data, final Window window, final boolean showMessage) {
        if (!diffPanel.hasDifferences() && !data.getHints().contains(HINT_ALLOW_NO_DIFFERENCES)) {
            DiffManagerImpl manager = (DiffManagerImpl) DiffManager.getInstance();
            if (!Comparing.equal(manager.getComparisonPolicy(), ComparisonPolicy.DEFAULT)) {
                ComparisonPolicy oldPolicy = manager.getComparisonPolicy();
                manager.setComparisonPolicy(ComparisonPolicy.DEFAULT);
                Disposable parentDisposable = Disposer.newDisposable();
                DiffPanel maybeDiffPanel = createDiffPanel(data, window, parentDisposable, this);
                manager.setComparisonPolicy(oldPolicy);

                boolean hasDiffs = maybeDiffPanel.hasDifferences();
                Disposer.dispose(parentDisposable);

                if (hasDiffs) return false;
            }

            if (! showMessage) {
                return true;
            }
            return !askForceOpenDiff(data);
        }
        return false;
    }

    private static boolean askForceOpenDiff(DiffRequest data) {
        byte[] bytes1;
        byte[] bytes2;
        try {
            bytes1 = data.getContents()[0].getBytes();
            bytes2 = data.getContents()[1].getBytes();
        }
        catch (IOException e) {
            MessagesEx.error(data.getProject(), e.getMessage()).showNow();
            return false;
        }
        String message = Arrays.equals(bytes1, bytes2)
                ? DiffBundle.message("diff.contents.are.identical.message.text")
                : DiffBundle.message("diff.contents.have.differences.only.in.line.separators.message.text");
        Messages.showInfoMessage(data.getProject(), message, DiffBundle.message("no.differences.dialog.title"));
        return false;
    }

    public boolean canShow(DiffRequest data) {
        return canShowDiff(data);
    }

    public static boolean canShowDiff(DiffRequest data) {
        DiffContent[] contents = data.getContents();
        if (contents.length != 2) return false;
        for (DiffContent content : contents) {
            if (content.isBinary()) return false;
            VirtualFile file = content.getFile();
            if (file != null && file.isDirectory()) return false;
        }
        return true;
    }

    @Override
    public DiffViewer createComponent(String title, DiffRequest request, Window window, @NotNull Disposable parentDisposable) {
        return createDiffPanelIfShouldShow(request, window, parentDisposable, false);
    }


    /**
     * Based on:
     * com.intellij.openapi.diff.impl.external.DiffManagerImpl#createDiffPanel
     */
    private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.external.DiffManagerImpl");
    protected DiffPanel createDiffPanel(DiffRequest data, Window window, @NotNull Disposable parentDisposable, FrameDiffTool tool) {
        DiffPanel diffPanel = null;
        try {
            diffPanel = new DiffPanelImpl(window, data.getProject(), true, true, DiffManagerImpl.FULL_DIFF_DIVIDER_POLYGONS_OFFSET, tool) {
                @Override
                public void setDiffRequest(DiffRequest data) {
                    super.setDiffRequest(data);
                    diffRequestChange(data, this);
                }
            };
            int contentCount = data.getContents().length;
            LOG.assertTrue(contentCount == 2, String.valueOf(contentCount));
            LOG.assertTrue(data.getContentTitles().length == contentCount);
            diffPanel.setDiffRequest(data);
            return diffPanel;
        }
        catch (RuntimeException e) {
            if (diffPanel != null) {
                Disposer.dispose(diffPanel);
            }
            throw e;
        }
    }

    public void diffRequestChange(DiffRequest diffRequest, DiffPanelImpl diffPanel) {}
}
