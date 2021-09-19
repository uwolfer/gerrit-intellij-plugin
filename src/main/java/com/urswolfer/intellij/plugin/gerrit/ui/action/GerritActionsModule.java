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

import com.google.inject.AbstractModule;

/**
 * @author Thomas Forrer
 */
public class GerritActionsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ReviewActionFactory.class);

        bind(AddReviewersAction.class);
        bind(FetchAction.class);
        bind(CheckoutAction.class);
        bind(CherryPickAction.class);
        bind(CompareBranchAction.class);
        bind(OpenInBrowserAction.class);
        bind(SettingsAction.class);
        bind(SubmitAction.class);
        bind(AbandonAction.class);
        bind(RefreshAction.class);
        bind(StarAction.class);
        bind(ResetAction.class);
    }
}
