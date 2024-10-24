/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.wot.api.provider;

import java.net.URL;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.eclipse.ditto.json.JsonObject;

/**
 * Provides functionality to asynchronously download a {@link JsonObject} from a given {@link URL}.
 *
 * @since 3.6.0
 */
public interface JsonDownloader {

    /**
     * Downloads from the given {@code url} the content as {@code JsonObject} and provides it asynchronously using the
     * passed {@code executor}.
     *
     * @param url the URL to download the json object from.
     * @param executor the executor to use.
     * @return a CompletionStage of the downloaded {@code JsonObject}, which may also be failed exceptionally, e.g.
     * if the resource could not be accessed or of the provided resource could not be parsed as a {@link JsonObject}.
     */
    CompletionStage<JsonObject> downloadJsonViaHttp(URL url, Executor executor);
}
