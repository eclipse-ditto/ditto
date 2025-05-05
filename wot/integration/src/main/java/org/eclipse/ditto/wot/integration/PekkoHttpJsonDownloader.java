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
package org.eclipse.ditto.wot.integration;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.MediaRanges;
import org.apache.pekko.http.javadsl.model.MediaTypes;
import org.apache.pekko.http.javadsl.model.headers.Accept;
import org.apache.pekko.http.javadsl.model.headers.Location;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.util.ByteString;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.internal.utils.http.DefaultHttpClientFacade;
import org.eclipse.ditto.internal.utils.http.HttpClientFacade;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.wot.api.config.WotConfig;
import org.eclipse.ditto.wot.api.provider.JsonDownloader;
import org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException;

/**
 * Pekko HTTP based implementation of {@link JsonDownloader}.
 */
final class PekkoHttpJsonDownloader implements JsonDownloader {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(PekkoHttpJsonDownloader.class);

    private static final HttpHeader ACCEPT_HEADER = Accept.create(
            MediaRanges.create(MediaTypes.applicationWithOpenCharset("tm+json")),
            MediaRanges.create(MediaTypes.APPLICATION_JSON)
    );

    private final HttpClientFacade httpClient;
    private final Materializer materializer;
    private final Executor executor;

    PekkoHttpJsonDownloader(final ActorSystem actorSystem, final WotConfig wotConfig, final Executor executor) {
        this.executor = executor;
        this.httpClient = DefaultHttpClientFacade.getInstance(actorSystem, wotConfig.getHttpProxyConfig());
        materializer = SystemMaterializer.get(actorSystem).materializer();
    }

    @Override
    public CompletionStage<JsonObject> downloadJsonViaHttp(final URL url, final Executor executor) {
        LOGGER.debug("Loading JsonObject from URL <{}>.", url);
        final CompletionStage<HttpResponse> responseFuture = getJsonObjectFromUrl(url);
        final CompletionStage<JsonObject> thingModelFuture = responseFuture.thenComposeAsync(this::mapResponseToJsonObject, executor);
        return thingModelFuture.toCompletableFuture();
    }

    private CompletionStage<HttpResponse> getJsonObjectFromUrl(final URL url) {
        return httpClient.createSingleHttpRequest(HttpRequest.GET(url.toString()).withHeaders(List.of(ACCEPT_HEADER)))
                .thenComposeAsync(response -> {
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
                                .map(this::getJsonObjectFromUrl) // recurse following the redirect
                                .orElseGet(() -> CompletableFuture.completedFuture(response));
                    } else {
                        return CompletableFuture.completedFuture(response);
                    }
                }, executor)
                .thenApplyAsync(response -> {
                    if (!response.status().isSuccess() || response.status().isRedirection()) {
                        handleNonSuccessResponse(response, url);
                    }
                    return response;
                }, executor)
                .exceptionally(e -> {
                    throw DittoRuntimeException.asDittoRuntimeException(e,
                            cause -> handleUnexpectedException(cause, url));
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
                "Got non success response from JsonObject endpoint with status code: <{0}>", response.status());
        getBodyAsString(response)
                .thenAcceptAsync(stringBody -> LOGGER.info("{} and body: <{}>.", msg, stringBody), executor);
        throw WotThingModelNotAccessibleException.newBuilder(url)
                .build();
    }

    private CompletionStage<String> getBodyAsString(final HttpResponse response) {
        return response.entity().getDataBytes().fold(ByteString.emptyByteString(), ByteString::concat)
                .map(ByteString::utf8String)
                .runWith(Sink.head(), materializer);
    }


    private static DittoRuntimeException handleUnexpectedException(final Throwable e, final URL url) {
        final String msg = MessageFormat.format("Got Exception from JsonObject endpoint <{0}>.", url);
        LOGGER.warn(msg, e);
        throw WotThingModelNotAccessibleException.newBuilder(url)
                .cause(e)
                .build();
    }
}
