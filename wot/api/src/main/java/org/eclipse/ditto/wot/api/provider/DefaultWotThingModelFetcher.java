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

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.wot.api.config.WotConfig;
import org.eclipse.ditto.wot.model.IRI;
import org.eclipse.ditto.wot.model.ThingDefinitionInvalidException;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.model.WotThingModelInvalidException;
import org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

/**
 * Default implementation of {@link WotThingModelFetcher} which should be not Ditto specific.
 */
final class DefaultWotThingModelFetcher implements WotThingModelFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWotThingModelFetcher.class);

    private static final Duration MAX_FETCH_MODEL_DURATION = Duration.ofSeconds(10);

    private final JsonDownloader jsonDownloader;
    private final Executor executor;
    private final Cache<URL, ThingModel> thingModelCache;

    DefaultWotThingModelFetcher(final WotConfig wotConfig,
            final JsonDownloader jsonDownloader,
            final Executor cacheLoaderExecutor) {
        this.jsonDownloader = jsonDownloader;
        this.executor = cacheLoaderExecutor;
        final AsyncCacheLoader<URL, ThingModel> loader = this::loadThingModelViaHttp;
        thingModelCache = CacheFactory.createCache(loader,
                wotConfig.getCacheConfig(),
                "ditto_wot_thing_model_cache",
                cacheLoaderExecutor
        );
    }

    @Override
    public CompletableFuture<ThingModel> fetchThingModel(final IRI iri, final DittoHeaders dittoHeaders) {
        try {
            return fetchThingModel(new URL(iri.toString()), dittoHeaders);
        } catch (final MalformedURLException e) {
            throw ThingDefinitionInvalidException.newBuilder(iri)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    @Override
    public CompletableFuture<ThingModel> fetchThingModel(final URL url, final DittoHeaders dittoHeaders) {
        LOGGER.debug("Fetching ThingModel (from cache or downloading as fallback) from URL: <{}>", url);
        return thingModelCache.get(url)
                .thenApplyAsync(optTm -> resolveThingModel(optTm.orElse(null), url, dittoHeaders), executor)
                .orTimeout(MAX_FETCH_MODEL_DURATION.toSeconds(), TimeUnit.SECONDS);
    }

    private ThingModel resolveThingModel(@Nullable final ThingModel thingModel,
            final URL tmUrl,
            final DittoHeaders dittoHeaders) {
        if (null != thingModel) {
            LOGGER.debug("Resolved ThingModel: <{}>", thingModel);
            return thingModel;
        } else {
            throw WotThingModelNotAccessibleException.newBuilder(tmUrl)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    /* this method is used to asynchronously load the ThingModel into the cache */
    private CompletableFuture<ThingModel> loadThingModelViaHttp(final URL url, final Executor executor) {
        LOGGER.debug("Loading ThingModel from URL <{}>.", url);
        final CompletionStage<JsonObject> responseFuture = jsonDownloader.downloadJsonViaHttp(url, executor);
        final CompletionStage<ThingModel> thingModelFuture = responseFuture
                .thenApplyAsync(ThingModel::fromJson, executor)
                .exceptionally(t -> {
                    LOGGER.warn("Failed to extract ThingModel from response because of <{}: {}>",
                            t.getClass().getSimpleName(), t.getMessage());
                    throw WotThingModelInvalidException.newBuilder(url)
                            .cause(t)
                            .build();
                });
        return thingModelFuture.toCompletableFuture();
    }

}
