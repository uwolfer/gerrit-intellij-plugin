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

package com.urswolfer.intellij.plugin.gerrit.rest;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.gson.JsonElement;
import com.google.inject.Inject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Thomas Forrer
 */
public class GerritRestAccess {
    @Inject
    private GerritSettings gerritSettings;
    @Inject
    private GerritApiUtil gerritApiUtil;

    public void getRequest(final String request,
                           final Project project,
                           final Consumer<ConsumerResult<JsonElement>> consumer) {
        Function<Void, ConsumerResultImpl<JsonElement>> function = new Function<Void, ConsumerResultImpl<JsonElement>>() {
            @Override
            public ConsumerResultImpl<JsonElement> apply(Void aVoid) {
                final ConsumerResultImpl<JsonElement> consumerResult = new ConsumerResultImpl<JsonElement>();
                try {
                    JsonElement jsonElement = gerritApiUtil.getRequest(request);
                    consumerResult.setResult(jsonElement);
                } catch (RestApiException e) {
                    consumerResult.setException(e);
                }
                return consumerResult;
            }
        };
        accessGerrit(project, consumer, function);
    }

    public void postRequest(final String request,
                            final String json,
                            final Project project,
                            final Consumer<ConsumerResult<JsonElement>> consumer) {
        Function<Void, ConsumerResultImpl<JsonElement>> function = new Function<Void, ConsumerResultImpl<JsonElement>>() {
            @Override
            public ConsumerResultImpl<JsonElement> apply(Void aVoid) {
                final ConsumerResultImpl<JsonElement> consumerResult = new ConsumerResultImpl<JsonElement>();
                try {
                    JsonElement jsonElement = gerritApiUtil.postRequest(request, json);
                    consumerResult.setResult(jsonElement);
                } catch (RestApiException e) {
                    consumerResult.setException(e);
                }
                return consumerResult;
            }
        };
        accessGerrit(project, consumer, function);
    }

    public void putRequest(final String request,
                           final Project project,
                           final Consumer<ConsumerResult<JsonElement>> consumer) {
        Function<Void, ConsumerResultImpl<JsonElement>> function = new Function<Void, ConsumerResultImpl<JsonElement>>() {
            @Override
            public ConsumerResultImpl<JsonElement> apply(Void aVoid) {
                final ConsumerResultImpl<JsonElement> consumerResult = new ConsumerResultImpl<JsonElement>();
                try {
                    JsonElement jsonElement = gerritApiUtil.putRequest(request);
                    consumerResult.setResult(jsonElement);
                } catch (RestApiException e) {
                    consumerResult.setException(e);
                }
                return consumerResult;
            }
        };
        accessGerrit(project, consumer, function);
    }

    public void deleteRequest(final String request,
                              final Project project,
                              final Consumer<ConsumerResult<JsonElement>> consumer) {
        Function<Void, ConsumerResultImpl<JsonElement>> function = new Function<Void, ConsumerResultImpl<JsonElement>>() {
            @Override
            public ConsumerResultImpl<JsonElement> apply(Void aVoid) {
                final ConsumerResultImpl<JsonElement> consumerResult = new ConsumerResultImpl<JsonElement>();
                try {
                    JsonElement jsonElement = gerritApiUtil.deleteRequest(request);
                    consumerResult.setResult(jsonElement);
                } catch (RestApiException e) {
                    consumerResult.setException(e);
                }
                return consumerResult;
            }
        };
        accessGerrit(project, consumer, function);
    }

    public void accessGerrit(final Project project,
                             final Consumer<ConsumerResult<JsonElement>> consumer,
                             final Function<Void, ConsumerResultImpl<JsonElement>> function) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                gerritSettings.preloadPassword();
                (new Task.Backgroundable(project, "Accessing Gerrit", true) {
                    public void run(@NotNull ProgressIndicator indicator) {
                        final ConsumerResultImpl<JsonElement> consumerResult = function.apply(null);
                        ApplicationManager.getApplication().invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                consumer.consume(consumerResult);
                            }
                        });
                    }
                }).queue();
            }
        });
    }

    private static class ConsumerResultImpl<T> implements ConsumerResult<T> {
        private T result;
        private Optional<Exception> exceptionOptional = Optional.absent();

        @Override
        public T getResult() {
            return result;
        }

        private void setResult(T result) {
            this.result = result;
        }

        @Override
        public Optional<Exception> getException() {
            return exceptionOptional;
        }

        private void setException(Exception exception) {
            this.exceptionOptional = Optional.fromNullable(exception);
        }
    }
}
