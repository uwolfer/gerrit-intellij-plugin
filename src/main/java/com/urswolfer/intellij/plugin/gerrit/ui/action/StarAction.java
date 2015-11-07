package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.common.base.Optional;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import icons.Git4ideaIcons;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // proxy class below is registered
public class StarAction extends AbstractLoggedInChangeAction {

    public StarAction() {
        super("Star", "Switch star status of change", Git4ideaIcons.Star);
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);
        ChangeInfo changeInfo = selectedChange.get();
        gerritUtil.changeStarredStatus(changeInfo.id, !(changeInfo.starred != null && changeInfo.starred), project);
    }

    public static class Proxy extends StarAction {
        private final StarAction delegate;

        public Proxy() {
            delegate = GerritModule.getInstance(StarAction.class);
        }

        @Override
        public void update(AnActionEvent e) {
            delegate.update(e);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            delegate.actionPerformed(e);
        }
    }
}
