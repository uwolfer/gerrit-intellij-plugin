package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.common.base.Optional;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

/**
 * @author Urs Wolfer
 */
public class StarAction extends AbstractLoggedInChangeAction {

    public StarAction() {
        super("Star", "Switch star status of change", AllIcons.Nodes.Favorite);
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        Project project = anActionEvent.getProject();
        ChangeInfo changeInfo = selectedChange.get();
        gerritUtil.changeStarredStatus(changeInfo.id, !(changeInfo.starred != null && changeInfo.starred), project);
    }

}
