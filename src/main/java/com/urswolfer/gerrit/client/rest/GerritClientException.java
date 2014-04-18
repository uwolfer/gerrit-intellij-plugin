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

package com.urswolfer.gerrit.client.rest;

/**
 * @author Urs Wolfer
 */
public class GerritClientException extends Exception {

    public GerritClientException() {
        super();
    }

    public GerritClientException(String message) {
        super(message);
    }

    public GerritClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public GerritClientException(Throwable cause) {
        super(cause);
    }
}
