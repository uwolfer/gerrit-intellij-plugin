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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.common.base.Optional;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.inject.Inject;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.IconLoader;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // proxy class below is registered
public class OpenInBrowserAction extends AbstractChangeAction {
    @Inject
    private GerritSettings gerritSettings;

    public OpenInBrowserAction() {
        super("Open in Gerrit", "Open corresponding link in browser", IconLoader.getIcon("/icons/gerrit.png", OpenInBrowserAction.class));
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        String urlToOpen = getUrl(selectedChange.get());
        BrowserUtil.browse(urlToOpen);
    }

    private String getUrl(ChangeInfo change) {
        String url = gerritSettings.getHost();
        int changeNumber = change._number;
        return String.format("%s/%s", url, changeNumber);
    }

    public static class Proxy extends OpenInBrowserAction {
        private final OpenInBrowserAction delegate;

        public Proxy() {
            delegate = GerritModule.getInstance(OpenInBrowserAction.class);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            delegate.actionPerformed(e);
        }
    }
}
