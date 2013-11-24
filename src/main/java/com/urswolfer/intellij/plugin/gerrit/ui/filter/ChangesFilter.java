package com.urswolfer.intellij.plugin.gerrit.ui.filter;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * @author Thomas Forrer
 */
public interface ChangesFilter {
    /**
     * @return an action to be included in the toolbar
     */
    AnAction getAction(Project project);

    /**
     * @return a part to be included in the search query, null otherwise
     */
    @Nullable
    String getSearchQueryPart();
}
