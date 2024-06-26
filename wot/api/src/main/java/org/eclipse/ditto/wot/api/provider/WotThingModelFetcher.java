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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.wot.api.config.WotConfig;
import org.eclipse.ditto.wot.model.IRI;
import org.eclipse.ditto.wot.model.ThingModel;

/**
 * Fetches WoT (Web of Things) ThingModels from {@code IRI}s/{@code URL}s, optionally caching those
 * (as part of the implementation strategy).
 *
 * @since 2.4.0
 */
public interface WotThingModelFetcher {

    /**
     * Fetches the ThingModel resource at the passed {@code iri}.
     *
     * @param iri the IRI (URL) from which to fetch the ThingModel.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeExceptions.
     * @return a CompletionStage containing the fetched ThingModel or completed exceptionally with a
     * {@link org.eclipse.ditto.wot.model.WotThingModelInvalidException} if the fetched ThingModel could not be
     * parsed/interpreted as correct WoT ThingModel.
     * @throws org.eclipse.ditto.wot.model.ThingDefinitionInvalidException if the passed {@code iri} did not contain a
     * valid URL.
     * @throws org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException if the ThingModel could not be
     * fetched at the given {@code iri}.
     */
    CompletionStage<ThingModel> fetchThingModel(IRI iri, DittoHeaders dittoHeaders);

    /**
     * Fetches the ThingModel resource at the passed {@code url}.
     *
     * @param url the URL from which to fetch the ThingModel.
     * @param dittoHeaders the DittoHeaders for possibly thrown DittoRuntimeExceptions.
     * @return a CompletionStage containing the fetched ThingModel or completed exceptionally with a
     * {@link org.eclipse.ditto.wot.model.WotThingModelInvalidException} if the fetched ThingModel could not be
     * parsed/interpreted as correct WoT ThingModel.
     * @throws org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException if the ThingModel could not be
     * fetched at the given {@code url}.
     */
    CompletionStage<ThingModel> fetchThingModel(URL url, DittoHeaders dittoHeaders);

    /**
     * Creates a new instance of WotThingModelFetcher with the given {@code actorSystem} and {@code wotConfig}.
     *
     * @param wotConfig the WoTConfig to use for creating the generator.
     * @param jsonDownloader the downloader to use to download a JsonObject from a given URL.
     * @param cacheLoaderExecutor the executor to use to run async cache loading tasks.
     * @return the created WotThingModelFetcher.
     * @since 3.6.0
     */
    static WotThingModelFetcher of(final WotConfig wotConfig,
            final JsonDownloader jsonDownloader,
            final Executor cacheLoaderExecutor) {
        return new DefaultWotThingModelFetcher(wotConfig, jsonDownloader, cacheLoaderExecutor);
    }
}
