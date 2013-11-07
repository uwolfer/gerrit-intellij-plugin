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

package com.urswolfer.intellij.plugin.gerrit.extension;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Thomas Forrer
 */
public class GerritCheckoutProviderProxy implements CheckoutProvider {

    private final GerritCheckoutProvider delegate;

    public GerritCheckoutProviderProxy() {
        this.delegate = GerritModule.getInstance(GerritCheckoutProvider.class);
    }

    @Override
    public void doCheckout(@NotNull Project project, @Nullable Listener listener) {
        delegate.doCheckout(project, listener);
    }

    @Override
    public String getVcsName() {
        return delegate.getVcsName();
    }
}
