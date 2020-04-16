/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.endpoints.routes;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.base.config.ThrottlingConfig;
import org.eclipse.ditto.services.gateway.endpoints.actors.AbstractHttpRequestActor;
import org.eclipse.ditto.services.gateway.endpoints.actors.HttpRequestActorPropsFactory;
import org.eclipse.ditto.services.gateway.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.services.gateway.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.services.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayTimeoutInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.headers.TimeoutAccess;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.japi.function.Function;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.Supervision;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamConverters;
import akka.util.ByteString;

/**
 * Base class for Akka HTTP routes.
 */
public abstract class AbstractRoute extends AllDirectives {

    /**
     * Don't configure URL decoding as JsonParseOptions because Akka-Http already decodes the fields-param and we would
     * decode twice.
     */
    public static final JsonParseOptions JSON_FIELD_SELECTOR_PARSE_OPTIONS = JsonFactory.newParseOptionsBuilder()
            .withoutUrlDecoding()
            .build();

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRoute.class);

    protected final ActorRef proxyActor;
    protected final ActorMaterializer materializer;
    protected final ActorSystem actorSystem;

    private final HttpConfig httpConfig;
    private final CommandConfig commandConfig;
    private final HeaderTranslator headerTranslator;
    private final HttpRequestActorPropsFactory httpRequestActorPropsFactory;

    /**
     * Constructs the abstract route builder.
     *
     * @param proxyActor an actor selection of the actor handling delegating to persistence.
     * @param actorSystem the ActorSystem to use.
     * @param httpConfig the configuration settings of the Gateway service's HTTP endpoint.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected AbstractRoute(final ActorRef proxyActor,
            final ActorSystem actorSystem,
            final HttpConfig httpConfig,
            final CommandConfig commandConfig,
            final HeaderTranslator headerTranslator) {

        this.proxyActor = checkNotNull(proxyActor, "delegate actor");
        this.actorSystem = checkNotNull(actorSystem, "actor system");
        this.httpConfig = httpConfig;
        this.commandConfig = commandConfig;
        this.headerTranslator = checkNotNull(headerTranslator, "header translator");

        LOGGER.debug("Using headerTranslator <{}>.", headerTranslator);

        materializer = ActorMaterializer.create(ActorMaterializerSettings.create(actorSystem)
                .withSupervisionStrategy((Function<Throwable, Supervision.Directive>) exc -> {
                            if (exc instanceof DittoRuntimeException) {
                                LogUtil.logWithCorrelationId(LOGGER, (DittoRuntimeException) exc, logger ->
                                        logger.debug("DittoRuntimeException during materialization of HTTP request: [{}] {}",
                                                exc.getClass().getSimpleName(), exc.getMessage()));
                            } else {
                                LOGGER.warn("Exception during materialization of HTTP request: {}", exc.getMessage(), exc);
                            }
                            return Supervision.stop(); // in any case, stop!
                        }
                ), actorSystem);

        httpRequestActorPropsFactory =
                AkkaClassLoader.instantiate(actorSystem, HttpRequestActorPropsFactory.class,
                        httpConfig.getActorPropsFactoryFullQualifiedClassname());
    }

    /**
     * Calculates a JsonFieldSelector from the passed {@code fieldsString}.
     *
     * @param fieldsString the fields as string.
     * @return the Optional JsonFieldSelector
     */
    public static Optional<JsonFieldSelector> calculateSelectedFields(final Optional<String> fieldsString) {
        return fieldsString.map(fs -> JsonFactory.newFieldSelector(fs, JSON_FIELD_SELECTOR_PARSE_OPTIONS));
    }

    /**
     * Interpret a throttling config and throttle a stream with it.
     *
     * @param throttlingConfig the throttling config to interpret.
     * @param <T> type of elements in the stream.
     * @return a throttling flow.
     * @since 1.1.0
     */
    public static <T> Flow<T, T, NotUsed> throttleByConfig(final ThrottlingConfig throttlingConfig) {
        final int limit = throttlingConfig.getLimit();
        final Duration interval = throttlingConfig.getInterval();
        if (limit > 0 && interval.negated().isNegative()) {
            return Flow.<T>create().throttle(throttlingConfig.getLimit(), throttlingConfig.getInterval());
        } else {
            return Flow.create();
        }
    }

    protected Route handlePerRequest(final RequestContext ctx, final Command command) {
        return handlePerRequest(ctx, command.getDittoHeaders(), Source.empty(), emptyRequestBody -> command);
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
            @Nullable final Function<JsonValue, JsonValue> responseTransformFunction) {

        // check if Akka HTTP timeout was overwritten by our code (e.g. for claim messages)
        final boolean increasedAkkaHttpTimeout = ctx.getRequest().getHeader(TimeoutAccess.class)
                .map(TimeoutAccess::timeoutAccess)
                .map(akka.http.javadsl.TimeoutAccess::getTimeout)
                .map(scalaDuration -> Duration.ofNanos(scalaDuration.toNanos()))
                .filter(akkaHttpTimeout -> akkaHttpTimeout.compareTo(commandConfig.getMaxTimeout()) > 0)
                .isPresent();

        if (increasedAkkaHttpTimeout) {
            return doHandlePerRequest(ctx, dittoHeaders, payloadSource, requestJsonToCommandFunction,
                    responseTransformFunction);
        } else {
            return withCustomRequestTimeout(dittoHeaders.getTimeout().orElse(null),
                    this::validateCommandTimeout,
                    null, // don't set default timeout in order to use the configured akka-http default
                    timeout -> doHandlePerRequest(ctx, dittoHeaders.toBuilder().timeout(timeout).build(), payloadSource,
                            requestJsonToCommandFunction, responseTransformFunction));
        }
    }

    private Route doHandlePerRequest(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final Source<ByteString, ?> payloadSource,
            final Function<String, Command> requestJsonToCommandFunction,
            @Nullable final Function<JsonValue, JsonValue> responseTransformFunction) {

        final CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();

        payloadSource
                .fold(ByteString.empty(), ByteString::concat)
                .map(ByteString::utf8String)
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
                        AbstractHttpRequestActor.COMPLETE_MESSAGE))
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

    /**
     * Create HTTP request actor by the dynamically loaded props factory.
     *
     * @param ctx the request context.
     * @param httpResponseFuture the promise of a response to be fulfilled by the HTTP request actor.
     * @return reference of the created actor.
     */
    protected ActorRef createHttpPerRequestActor(final RequestContext ctx,
            final CompletableFuture<HttpResponse> httpResponseFuture) {

        final Props props = httpRequestActorPropsFactory.props(
                proxyActor, headerTranslator, ctx.getRequest(), httpResponseFuture, httpConfig);

        return actorSystem.actorOf(props);
    }

    /**
     * Configures the passed {@code optionalTimeout} as Akka HTTP request timeout validating it with the passed
     * {@code checkTimeoutFunction} falling back to the optional {@code defaultTimeout} wrapping the passed
     * {@code inner} route.
     *
     * @param optionalTimeout the custom timeout to use as Akka HTTP request timeout adjusting the configured default
     * one.
     * @param checkTimeoutFunction a function to check the passed optionalTimeout for validity e. g. within some bounds.
     * @param defaultTimeout an optional default timeout if the passed optionalTimeout was not set.
     * @param inner the inner Route to wrap.
     * @return the wrapped route - potentially with custom timeout adjusted.
     */
    protected Route withCustomRequestTimeout(@Nullable final Duration optionalTimeout,
            final UnaryOperator<Duration> checkTimeoutFunction,
            @Nullable final Duration defaultTimeout,
            final java.util.function.Function<Duration, Route> inner) {

        @Nullable Duration customRequestTimeout = defaultTimeout;
        if (null != optionalTimeout) {
            customRequestTimeout = checkTimeoutFunction.apply(optionalTimeout);
        }

        if (null != customRequestTimeout) {
            return increaseHttpRequestTimeout(inner, customRequestTimeout);
        } else {
            return extractRequestTimeout(configuredTimeout ->
                    increaseHttpRequestTimeout(inner, configuredTimeout));
        }
    }

    private Route increaseHttpRequestTimeout(final java.util.function.Function<Duration, Route> inner,
            final Duration requestTimeout) {
        return increaseHttpRequestTimeout(inner,
                scala.concurrent.duration.Duration.create(requestTimeout.toMillis(), TimeUnit.MILLISECONDS));
    }

    private Route increaseHttpRequestTimeout(final java.util.function.Function<Duration, Route> inner,
            final scala.concurrent.duration.Duration requestTimeout) {
        if (requestTimeout.isFinite()) {
            // adds some time in order to avoid race conditions with internal receiveTimeouts which shall return "408"
            // in case of message timeouts or "424" in case of requested-acks timeouts:
            final scala.concurrent.duration.Duration akkaHttpRequestTimeout = requestTimeout
                    .plus(scala.concurrent.duration.Duration.create(5, TimeUnit.SECONDS));
            return withRequestTimeout(akkaHttpRequestTimeout, () ->
                    inner.apply(Duration.ofMillis(requestTimeout.toMillis()))
            );
        } else {
            return inner.apply(Duration.ofMillis(Long.MAX_VALUE));
        }
    }

    /**
     * Validates the passed {@code timeout} based on the configured {@link CommandConfig#getMaxTimeout()} as the upper
     * bound.
     *
     * @param timeout the timeout to validate.
     * @return the passed in timeout if it was valid.
     * @throws GatewayTimeoutInvalidException if the passed {@code timeout} was not within its bounds.
     */
    protected Duration validateCommandTimeout(final Duration timeout) {
        final Duration maxTimeout = commandConfig.getMaxTimeout();
        // check if the timeout is smaller than the maximum possible timeout and > 0:
        if (timeout.isNegative() || timeout.compareTo(maxTimeout) > 0) {
            throw GatewayTimeoutInvalidException.newBuilder(timeout, maxTimeout)
                    .build();
        }
        return timeout;
    }
}
