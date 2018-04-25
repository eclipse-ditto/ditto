/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.endpoints.routes;

import static akka.http.javadsl.server.Directives.completeWithFuture;
import static java.util.Objects.requireNonNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.gateway.endpoints.HttpRequestActor;
import org.eclipse.ditto.services.gateway.endpoints.utils.RequestPreProcessors;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.japi.function.Function;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.Supervision;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamConverters;
import akka.util.ByteString;

/**
 * Base class for Akka HTTP routes.
 */
public abstract class AbstractRoute {

    /**
     * Don't configure URL decoding as JsonParseOptions because Akka-Http already decodes the fields-param and we would
     * decode twice.
     */
    private static final JsonParseOptions JSON_FIELD_SELECTOR_PARSE_OPTIONS = JsonFactory.newParseOptionsBuilder()
            .withoutUrlDecoding()
            .build();

    protected final ActorRef proxyActor;
    protected final ActorMaterializer materializer;
    private final ActorSystem actorSystem;

    /**
     * Constructs the abstract route builder.
     *
     * @param proxyActor an actor selection of the actor handling delegating to persistence.
     * @param actorSystem the ActorSystem to use.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected AbstractRoute(final ActorRef proxyActor, final ActorSystem actorSystem) {
        requireNonNull(proxyActor, "The delegate actor must not be null!");
        requireNonNull(actorSystem, "The actor system must not be null!");

        this.proxyActor = proxyActor;
        this.actorSystem = actorSystem;
        materializer = ActorMaterializer.create(ActorMaterializerSettings.create(actorSystem)
                .withSupervisionStrategy((Function<Throwable, Supervision.Directive>) exc ->
                        Supervision.stop() // in any case, stop!
                ), actorSystem);
    }

    /**
     * Calculates a JsonFieldSelector from the passed {@code fieldsString}.
     *
     * @param fieldsString the fields as string.
     * @return the Optional JsonFieldSelector
     */
    protected static Optional<JsonFieldSelector> calculateSelectedFields(final Optional<String> fieldsString) {
        return fieldsString.map(fs -> JsonFactory.newFieldSelector(fs, JSON_FIELD_SELECTOR_PARSE_OPTIONS));
    }

    protected Route handlePerRequest(final RequestContext ctx, final Command command) {
        return handlePerRequest(ctx, command.getDittoHeaders(), Source.empty(),
                emptyRequestBody -> command);
    }

    protected Route handlePerRequest(final RequestContext ctx, final Command command,
            final Function<JsonValue, JsonValue> responseTransformFunction) {
        return handlePerRequest(ctx, command.getDittoHeaders(), Source.empty(),
                emptyRequestBody -> command, responseTransformFunction);
    }

    protected Route handlePerRequest(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final Source<ByteString, ?> payloadSource,
            final Function<String, Command> requestJsonToCommandFunction) {
        return handlePerRequest(ctx, dittoHeaders, payloadSource, requestJsonToCommandFunction, null);
    }

    protected Route handlePerRequest(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final Source<ByteString, ?> payloadSource,
            final Function<String, Command> requestJsonToCommandFunction,
            final Function<JsonValue, JsonValue> responseTransformFunction) {
        final CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();

        payloadSource
                .fold(ByteString.empty(), ByteString::concat)
                .map(ByteString::utf8String)
                .map(str -> RequestPreProcessors.replacePlaceholders(str, dittoHeaders))
                .map(requestJsonToCommandFunction)
                .map(command -> {
                    final JsonSchemaVersion schemaVersion =
                            dittoHeaders.getSchemaVersion().orElse(command.getImplementedSchemaVersion());
                    return command.implementsSchemaVersion(schemaVersion) ? command
                            : CommandNotSupportedException.newBuilder(schemaVersion.toInt())
                            .dittoHeaders(dittoHeaders)
                            .build();
                })
                .to(Sink.actorRef(createHttpPerRequestActor(ctx, httpResponseFuture),
                        HttpRequestActor.COMPLETE_MESSAGE))
                .run(materializer);

        // optional step: transform the response entity:
        if (responseTransformFunction != null) {
            final CompletableFuture<HttpResponse> transformedResponse = httpResponseFuture.thenApply(response -> {
                final boolean isSuccessfulResponse = response.status().isSuccess();
                // we have to check if response is empty, because otherwise we'll get an IOException when trying to
                // read it
                final boolean isEmptyResponse = response.entity().isKnownEmpty();
                if (isSuccessfulResponse && !isEmptyResponse) {
                    final InputStream inputStream = response.entity()
                            .getDataBytes()
                            .fold(ByteString.empty(), ByteString::concat)
                            .runWith(StreamConverters.asInputStream(), materializer);
                    final JsonValue jsonValue = JsonFactory.readFrom(new InputStreamReader(inputStream));
                    try {
                        final JsonValue transformed = responseTransformFunction.apply(jsonValue);
                        return response.withEntity(ContentTypes.APPLICATION_JSON, transformed.toString());
                    } catch (final Exception e) {
                        throw JsonParseException.newBuilder()
                                .message("Could not transform JSON: " + e.getMessage())
                                .cause(e)
                                .build();
                    }
                } else {
                    // for non-successful and empty responses, don't transform the response body
                    return response;
                }
            });
            return completeWithFuture(preprocessResponse(transformedResponse));
        } else {
            return completeWithFuture(preprocessResponse(httpResponseFuture));
        }
    }

    /**
     * Processes the {@link HttpResponse} by consuming the CompletionStage and returning another (or the same)
     * CompletionStage. May be used to modify the HttpResponse before it is sent back to client.
     */
    protected CompletionStage<HttpResponse> preprocessResponse(final CompletionStage<HttpResponse> responseStage) {
        return responseStage; // default: do nothing
    }

    protected ActorRef createHttpPerRequestActor(final RequestContext ctx,
            final CompletableFuture<HttpResponse> httpResponseFuture) {
        return actorSystem.actorOf(HttpRequestActor.props(proxyActor, ctx.getRequest(), httpResponseFuture));
    }
}
