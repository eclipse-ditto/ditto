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
package org.eclipse.ditto.wot.api.resolver;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.wot.api.config.WotConfig;
import org.eclipse.ditto.wot.api.generator.WotThingModelExtensionResolver;
import org.eclipse.ditto.wot.api.provider.WotThingModelFetcher;
import org.eclipse.ditto.wot.model.IRI;
import org.eclipse.ditto.wot.model.ThingDefinitionInvalidException;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.model.WotThingModelInvalidException;
import org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

/**
 * Default implementation of {@link WotThingModelResolver} which should be not Ditto specific.
 */
final class DefaultWotThingModelResolver implements WotThingModelResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWotThingModelResolver.class);

    private static final String TM_SUBMODEL = "tm:submodel";
    private static final String TM_SUBMODEL_INSTANCE_NAME = "instanceName";

    private static final Duration MAX_RESOLVE_MODEL_DURATION = Duration.ofSeconds(12);

    private final WotThingModelFetcher thingModelFetcher;
    private final WotThingModelExtensionResolver thingModelExtensionResolver;
    private final Executor executor;
    private final Cache<URL, ThingModel> fullyResolvedThingModelCache;

    DefaultWotThingModelResolver(final WotConfig wotConfig,
            final WotThingModelFetcher thingModelFetcher,
            final WotThingModelExtensionResolver thingModelExtensionResolver,
            final Executor cacheLoaderExecutor) {
        this.thingModelFetcher = thingModelFetcher;
        this.thingModelExtensionResolver = thingModelExtensionResolver;
        this.executor = cacheLoaderExecutor;
        final AsyncCacheLoader<URL, ThingModel> loader = this::loadThingModelViaHttp;
        fullyResolvedThingModelCache = CacheFactory.createCache(loader,
                wotConfig.getCacheConfig(),
                "ditto_wot_fully_resolved_thing_model_cache",
                cacheLoaderExecutor);
    }

    @Override
    public CompletableFuture<ThingModel> resolveThingModel(final IRI iri, final DittoHeaders dittoHeaders) {
        try {
            return resolveThingModel(new URL(iri.toString()), dittoHeaders);
        } catch (final MalformedURLException e) {
            throw ThingDefinitionInvalidException.newBuilder(iri)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    @Override
    public CompletableFuture<ThingModel> resolveThingModel(final URL url, final DittoHeaders dittoHeaders) {
        LOGGER.debug("Resolving ThingModel (from cache or downloading as fallback) from URL: <{}>", url);
        return fullyResolvedThingModelCache.get(url)
                .thenApplyAsync(optTm -> resolveThingModel(optTm.orElse(null), url, dittoHeaders), executor)
                .orTimeout(MAX_RESOLVE_MODEL_DURATION.toSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public CompletionStage<Map<ThingSubmodel, ThingModel>> resolveThingModelSubmodels(final ThingModel thingModel,
            final DittoHeaders dittoHeaders) {

        final List<CompletableFuture<AbstractMap.SimpleEntry<ThingSubmodel, ThingModel>>> futureList =
                thingModel.getLinks()
                        .map(links -> links.stream()
                                .filter(baseLink -> baseLink.getRel().filter(TM_SUBMODEL::equals).isPresent())
                                .map(baseLink -> {
                                            final String instanceName = baseLink.getValue(TM_SUBMODEL_INSTANCE_NAME)
                                                    .filter(JsonValue::isString)
                                                    .map(JsonValue::asString)
                                                    .orElseThrow(() -> WotThingModelInvalidException
                                                            .newBuilder("The required 'instanceName' field of the " +
                                                                    "'tm:submodel' link was not provided."
                                                            ).dittoHeaders(dittoHeaders)
                                                            .build()
                                                    );
                                            LOGGER.debug("Resolved TM submodel with instanceName <{}> and href <{}>",
                                                    instanceName, baseLink.getHref());
                                            return new ThingSubmodel(instanceName, baseLink.getHref());
                                        }
                                )
                        )
                        .orElseGet(Stream::empty)
                        .map(submodel -> resolveThingModel(submodel.href(), dittoHeaders)
                                .thenApplyAsync(subThingModel ->
                                        new AbstractMap.SimpleEntry<>(submodel, subThingModel), executor
                                )
                                .toCompletableFuture()
                        )
                        .toList();

        return CompletableFuture.allOf(futureList.toArray(CompletableFuture<?>[]::new))
                .thenApplyAsync(aVoid -> futureList.stream()
                        .map(CompletableFuture::join) // joining does not block anything here as "allOf" already guaranteed that all futures are ready
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> {
                            throw WotThingModelInvalidException.newBuilder(String.format("Thing submodels: Duplicate key %s", u))
                                    .dittoHeaders(dittoHeaders)
                                    .build();
                        }, LinkedHashMap::new))
                );
    }

    private ThingModel resolveThingModel(@Nullable final ThingModel thingModel,
            final URL tmUrl,
            final DittoHeaders dittoHeaders) {
        if (null != thingModel) {
            LOGGER.debug("Fully Resolved ThingModel: <{}>", thingModel);
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
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        return thingModelFetcher.fetchThingModel(url, dittoHeaders)
                .thenComposeAsync(thingModel ->
                                thingModelExtensionResolver
                                        .resolveThingModelExtensions(thingModel, dittoHeaders)
                                        .thenComposeAsync(thingModelWithExtensions ->
                                                thingModelExtensionResolver.resolveThingModelRefs(thingModelWithExtensions,
                                                        dittoHeaders),
                                                executor
                                        ),
                        executor
                )
                .toCompletableFuture();
    }

}
