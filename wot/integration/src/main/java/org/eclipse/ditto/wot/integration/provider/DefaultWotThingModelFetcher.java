/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.integration.provider;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheFactory;
import org.eclipse.ditto.internal.utils.http.DefaultHttpClientFacade;
import org.eclipse.ditto.internal.utils.http.HttpClientFacade;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.wot.integration.config.WotConfig;
import org.eclipse.ditto.wot.model.IRI;
import org.eclipse.ditto.wot.model.ThingDefinitionInvalidException;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.model.WotThingModelInvalidException;
import org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.MediaRanges;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.headers.Accept;
import akka.http.javadsl.model.headers.Location;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Sink;
import akka.util.ByteString;

/**
 * Default implementation of {@link WotThingModelFetcher} which should be not Ditto specific.
 */
final class DefaultWotThingModelFetcher implements WotThingModelFetcher {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(DefaultWotThingModelFetcher.class);

    private static final HttpHeader ACCEPT_HEADER = Accept.create(
            MediaRanges.create(MediaTypes.applicationWithOpenCharset("tm+json")),
            MediaRanges.create(MediaTypes.APPLICATION_JSON)
    );

    private final HttpClientFacade httpClient;
    private final Materializer materializer;
    private final Cache<URL, ThingModel> thingModelCache;

    DefaultWotThingModelFetcher(final ActorSystem actorSystem, final WotConfig wotConfig) {
        this.httpClient = DefaultHttpClientFacade.getInstance(actorSystem, wotConfig.getHttpProxyConfig());
        materializer = SystemMaterializer.get(actorSystem).materializer();
        final AsyncCacheLoader<URL, ThingModel> loader = this::loadThingModelViaHttp;
        thingModelCache = CacheFactory.createCache(loader,
                wotConfig.getCacheConfig(),
                "ditto_wot_thing_model_cache",
                actorSystem.dispatchers().lookup("wot-dispatcher"));
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
        LOGGER.withCorrelationId(dittoHeaders)
                .debug("Fetching ThingModel (from cache or downloading as fallback) from URL: <{}>", url);
        return thingModelCache.get(url)
                .thenApply(optTm -> resolveThingModel(optTm.orElse(null), url, dittoHeaders));
    }

    private ThingModel resolveThingModel(@Nullable final ThingModel thingModel,
            final URL tmUrl,
            final DittoHeaders dittoHeaders) {
        if (null != thingModel) {
            LOGGER.withCorrelationId(dittoHeaders).debug("Resolved ThingModel: <{}>", thingModel);
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
        final CompletionStage<HttpResponse> responseFuture = getThingModelFromUrl(url);
        final CompletionStage<ThingModel> thingModelFuture = responseFuture.thenCompose(
                response -> mapResponseToThingModel(response, url));
        return thingModelFuture.toCompletableFuture();
    }

    private CompletionStage<HttpResponse> getThingModelFromUrl(final URL url) {
        return httpClient.createSingleHttpRequest(HttpRequest.GET(url.toString()).withHeaders(List.of(ACCEPT_HEADER)))
                .thenCompose(response -> {
                    if (response.status().isRedirection()) {
                        return response.getHeader(Location.class)
                                .map(location -> {
                                    try {
                                        LOGGER.debug("Following redirect to location: <{}>", location);
                                        return new URL(location.getUri().toString());
                                    } catch (final MalformedURLException e) {
                                        throw DittoRuntimeException.asDittoRuntimeException(e,
                                                cause -> handleUnexpectedException(cause, url));
                                    }
                                })
                                .map(this::getThingModelFromUrl) // recurse following the redirect
                                .orElseGet(() -> CompletableFuture.completedFuture(response));
                    } else {
                        return CompletableFuture.completedFuture(response);
                    }
                })
                .thenApply(response -> {
                    if (!response.status().isSuccess() || response.status().isRedirection()) {
                        handleNonSuccessResponse(response, url);
                    }
                    return response;
                })
                .exceptionally(e -> {
                    throw DittoRuntimeException.asDittoRuntimeException(e,
                            cause -> handleUnexpectedException(cause, url));
                });
    }

    private CompletableFuture<ThingModel> mapResponseToThingModel(final HttpResponse response, final URL url) {
        final CompletableFuture<JsonObject> bodyFuture = mapResponseToJsonObject(response)
                .toCompletableFuture();
        return bodyFuture
                .thenApply(ThingModel::fromJson)
                .exceptionally(t -> {
                    LOGGER.warn("Failed to extract ThingModel from response <{}> because of <{}: {}>", response,
                            t.getClass().getSimpleName(), t.getMessage());
                    throw WotThingModelInvalidException.newBuilder(url)
                            .cause(t)
                            .build();
                });
    }

    private CompletionStage<JsonObject> mapResponseToJsonObject(final HttpResponse response) {
        return response.entity().getDataBytes().fold(ByteString.emptyByteString(), ByteString::concat)
                .map(ByteString::utf8String)
                .map(JsonFactory::readFrom)
                .map(JsonValue::asObject)
                .runWith(Sink.head(), materializer);
    }

    private void handleNonSuccessResponse(final HttpResponse response, final URL url) {
        final String msg = MessageFormat.format(
                "Got non success response from ThingModel endpoint with status code: <{0}>", response.status());
        getBodyAsString(response)
                .thenAccept(stringBody -> LOGGER.info("{} and body: <{}>.", msg, stringBody));
        throw WotThingModelNotAccessibleException.newBuilder(url)
                .build();
    }

    private CompletionStage<String> getBodyAsString(final HttpResponse response) {
        return response.entity().getDataBytes().fold(ByteString.emptyByteString(), ByteString::concat)
                .map(ByteString::utf8String)
                .runWith(Sink.head(), materializer);
    }


    private static DittoRuntimeException handleUnexpectedException(final Throwable e, final URL url) {
        final String msg = MessageFormat.format("Got Exception from ThingModel endpoint <{0}>.", url);
        LOGGER.warn(msg, e);
        throw WotThingModelNotAccessibleException.newBuilder(url)
                .cause(e)
                .build();
    }
}
