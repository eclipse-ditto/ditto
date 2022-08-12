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
package org.eclipse.ditto.gateway.service.endpoints.routes;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandNotSupportedException;
import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.gateway.api.GatewayTimeoutInvalidException;
import org.eclipse.ditto.gateway.service.endpoints.actors.AbstractHttpRequestActor;
import org.eclipse.ditto.gateway.service.endpoints.actors.HttpRequestActorPropsFactory;
import org.eclipse.ditto.gateway.service.endpoints.directives.ContentTypeValidationDirective;
import org.eclipse.ditto.gateway.service.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonValue;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.stream.ActorAttributes;
import akka.stream.Attributes;
import akka.stream.Supervision;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.RunnableGraph;
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

    /**
     * Timeout for Akka HTTP. Timeout is normally managed in HttpRequestActor and AcknowledgementAggregatorActor.
     * The Akka HTTP timeout is only there to prevent resource leak.
     */
    private static final scala.concurrent.duration.Duration AKKA_HTTP_TIMEOUT =
            scala.concurrent.duration.Duration.create(2, TimeUnit.MINUTES);

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(AbstractRoute.class);

    private final RouteBaseProperties routeBaseProperties;

    private final HttpRequestActorPropsFactory httpRequestActorPropsFactory;
    private final Attributes supervisionStrategy;
    private final Set<String> mediaTypeJsonWithFallbacks;

    /**
     * Constructs a {@code AbstractRoute} object.
     *
     * @param routeBaseProperties the base properties of the route.
     * @throws NullPointerException if {@code routeBaseProperties} is {@code null}.
     */
    protected AbstractRoute(final RouteBaseProperties routeBaseProperties) {
        this.routeBaseProperties = checkNotNull(routeBaseProperties, "routeBaseProperties");

        final var httpConfig = routeBaseProperties.getHttpConfig();
        final var fallbackMediaTypes = httpConfig.getAdditionalAcceptedMediaTypes().stream();
        final var jsonMediaType = Stream.of(MediaTypes.APPLICATION_JSON.toString());
        mediaTypeJsonWithFallbacks = Stream.concat(jsonMediaType, fallbackMediaTypes).collect(Collectors.toSet());

        LOGGER.debug("Using headerTranslator <{}>.", routeBaseProperties.getHeaderTranslator());
        final var dittoExtensionsConfig =
                ScopedConfig.dittoExtension(routeBaseProperties.getActorSystem().settings().config());
        httpRequestActorPropsFactory =
                HttpRequestActorPropsFactory.get(routeBaseProperties.getActorSystem(), dittoExtensionsConfig);

        supervisionStrategy = createSupervisionStrategy();
    }

    private static Attributes createSupervisionStrategy() {
        return ActorAttributes.withSupervisionStrategy(exc -> {
            if (exc instanceof DittoRuntimeException dre) {
                LOGGER.withCorrelationId(dre)
                        .debug("DittoRuntimeException during materialization of HTTP request: [{}] {}",
                                dre.getClass().getSimpleName(), dre.getMessage());
            } else {
                LOGGER.warn("Exception during materialization of HTTP request: {}", exc.getMessage(), exc);
            }
            return (Supervision.Directive) Supervision.stop(); // in any case, stop!
        });
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

    /**
     * Interpret a throttling config and throttle a stream with it.
     *
     * @param throttlingConfig the throttling config to interpret.
     * @param <T> type of elements in the stream.
     * @return a throttling flow.
     * @since 1.1.0
     */
    public static <T> Flow<T, T, NotUsed> throttleByConfig(final ThrottlingConfig throttlingConfig) {
        if (throttlingConfig.isEnabled()) {
            return Flow.<T>create().throttle(throttlingConfig.getLimit(), throttlingConfig.getInterval());
        } else {
            return Flow.<T>create();
        }
    }

    /**
     * Handle a request by converting it to a command.
     *
     * @param ctx the request context.
     * @param dittoHeaders the extracted Ditto headers.
     * @param payloadSource source of the request body.
     * @param requestJsonToCommandFunction function converting a string to a command.
     * @return the request handling route.
     */
    public Route handlePerRequest(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final Source<ByteString, ?> payloadSource,
            final Function<String, Command<?>> requestJsonToCommandFunction) {

        return handlePerRequest(ctx, dittoHeaders, payloadSource, requestJsonToCommandFunction, null);
    }

    protected Route handlePerRequest(final RequestContext ctx, final Command<?> command) {
        return handlePerRequest(ctx, command.getDittoHeaders(), Source.empty(), emptyRequestBody -> command);
    }

    protected Route handlePerRequest(final RequestContext ctx, final Command<?> command,
            @Nullable final BiFunction<JsonValue, HttpResponse, HttpResponse> responseTransformFunction) {

        return handlePerRequest(ctx, command.getDittoHeaders(), Source.empty(),
                emptyRequestBody -> command, responseTransformFunction);
    }

    protected Route handlePerRequest(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final Source<ByteString, ?> payloadSource,
            final Function<String, Command<?>> requestJsonToCommandFunction,
            @Nullable final BiFunction<JsonValue, HttpResponse, HttpResponse> responseTransformFunction) {

        return withCustomRequestTimeout(dittoHeaders.getTimeout().orElse(null),
                this::validateCommandTimeout,
                timeout -> doHandlePerRequest(ctx, dittoHeaders.toBuilder().timeout(timeout).build(), payloadSource,
                        requestJsonToCommandFunction, responseTransformFunction));
    }

    protected <M> M runWithSupervisionStrategy(final RunnableGraph<M> graph) {
        return graph.withAttributes(supervisionStrategy).run(routeBaseProperties.getActorSystem());
    }

    private Route doHandlePerRequest(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final Source<ByteString, ?> payloadSource,
            final Function<String, Command<?>> requestJsonToCommandFunction,
            @Nullable final BiFunction<JsonValue, HttpResponse, HttpResponse> responseValueTransformFunction) {

        final CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();

        runWithSupervisionStrategy(payloadSource
                .fold(ByteString.emptyByteString(), ByteString::concat)
                .map(ByteString::utf8String)
                .map(x -> {
                    try {
                        // DON'T replace this try-catch by .recover: The supervising strategy is called before recovery!
                        final Command<?> command = requestJsonToCommandFunction.apply(x);
                        final JsonSchemaVersion schemaVersion =
                                dittoHeaders.getSchemaVersion().orElse(command.getImplementedSchemaVersion());
                        return command.implementsSchemaVersion(schemaVersion) ? command
                                : CommandNotSupportedException.newBuilder(schemaVersion.toInt())
                                .dittoHeaders(dittoHeaders)
                                .build();
                    } catch (final Exception e) {
                        return new Status.Failure(e);
                    }
                })
                .to(Sink.actorRef(createHttpPerRequestActor(ctx, httpResponseFuture),
                        AbstractHttpRequestActor.COMPLETE_MESSAGE))
        );

        // optional step: transform the response entity:
        if (responseValueTransformFunction != null) {
            final CompletionStage<HttpResponse> strictResponseFuture = httpResponseFuture.thenCompose(this::toStrict);
            final CompletionStage<HttpResponse> transformedResponse = strictResponseFuture.thenApply(response -> {
                final boolean isSuccessfulResponse = response.status().isSuccess();
                // we have to check if response is empty, because otherwise we'll get an IOException when trying to
                // read it
                final boolean isEmptyResponse = response.entity().isKnownEmpty();
                if (isSuccessfulResponse && !isEmptyResponse) {
                    final InputStream inputStream = runWithSupervisionStrategy(response.entity()
                            .getDataBytes()
                            .fold(ByteString.emptyByteString(), ByteString::concat)
                            .toMat(StreamConverters.asInputStream(), Keep.right())
                    );
                    final JsonValue jsonValue = JsonFactory.readFrom(new InputStreamReader(inputStream));
                    try {
                        return responseValueTransformFunction.apply(jsonValue, response);
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

        final var props = httpRequestActorPropsFactory.props(routeBaseProperties.getProxyActor(),
                routeBaseProperties.getHeaderTranslator(),
                ctx.getRequest(),
                httpResponseFuture,
                routeBaseProperties.getHttpConfig(),
                routeBaseProperties.getCommandConfig());

        final var actorSystem = routeBaseProperties.getActorSystem();
        return actorSystem.actorOf(props);
    }

    /**
     * Provides a composed directive of {@link AllDirectives#extractDataBytes} and
     * {@link org.eclipse.ditto.gateway.service.endpoints.directives.ContentTypeValidationDirective#ensureValidContentType}, where the supported media-types are
     * application/json and the fallback/additional media-types from config.
     *
     * @param ctx The context of a request.
     * @param dittoHeaders The ditto headers of a request.
     * @param inner route directive to handles the extracted payload.
     * @return Route.
     */
    protected Route ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final java.util.function.Function<Source<ByteString, Object>, Route> inner) {

        return ContentTypeValidationDirective.ensureValidContentType(mediaTypeJsonWithFallbacks, ctx, dittoHeaders,
                () -> extractDataBytes(inner));
    }

    /**
     * Provides a composed directive of {@link AllDirectives#extractDataBytes} and
     * {@link org.eclipse.ditto.gateway.service.endpoints.directives.ContentTypeValidationDirective#ensureValidContentType}, where the only supported media-type is
     * application/merge-patch+json.
     *
     * @param ctx The context of a request.
     * @param dittoHeaders The ditto headers of a request.
     * @param inner route directive to handles the extracted payload.
     * @return Route.
     */
    protected Route ensureMediaTypeMergePatchJsonThenExtractDataBytes(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final java.util.function.Function<Source<ByteString, Object>, Route> inner) {

        return ContentTypeValidationDirective.ensureMergePatchJsonContentType(ctx, dittoHeaders,
                () -> extractDataBytes(inner));
    }

    /**
     * Validate the passed {@code optionalTimeout} with the passed
     * {@code checkTimeoutFunction} falling back to the optional {@code defaultTimeout} wrapping the passed
     * {@code inner} route. Set Akka HTTP timeout to a ceiling because the actual timeout handling happens in
     * HttpRequestActor and AcknowledgementAggregatorActor.
     *
     * @param optionalTimeout the custom timeout to use as Akka HTTP request timeout adjusting the configured default
     * one.
     * @param checkTimeoutFunction a function to check the passed optionalTimeout for validity e. g. within some bounds.
     * @param inner the inner Route to wrap.
     * @return the wrapped route - potentially with custom timeout adjusted.
     */
    protected Route withCustomRequestTimeout(@Nullable final Duration optionalTimeout,
            final UnaryOperator<Duration> checkTimeoutFunction,
            final java.util.function.Function<Duration, Route> inner) {

        Duration customRequestTimeout = routeBaseProperties.getHttpConfig().getRequestTimeout();
        if (null != optionalTimeout) {
            customRequestTimeout = checkTimeoutFunction.apply(optionalTimeout);
        }

        return increaseHttpRequestTimeout(inner, customRequestTimeout);
    }

    private CompletionStage<HttpResponse> toStrict(final HttpResponse response) {
        final var timeoutMillis = routeBaseProperties.getHttpConfig().getRequestTimeout().toMillis();
        return response.toStrict(timeoutMillis, routeBaseProperties.getActorSystem()).thenApply(x -> x);
    }

    private Route increaseHttpRequestTimeout(final java.util.function.Function<Duration, Route> inner,
            final Duration requestTimeout) {
        return increaseHttpRequestTimeout(inner,
                scala.concurrent.duration.Duration.create(requestTimeout.toMillis(), TimeUnit.MILLISECONDS));
    }

    private Route increaseHttpRequestTimeout(final java.util.function.Function<Duration, Route> inner,
            final scala.concurrent.duration.Duration requestTimeout) {
        return withRequestTimeout(AKKA_HTTP_TIMEOUT,
                () -> inner.apply(Duration.ofMillis(requestTimeout.toMillis())));
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
        final var commandConfig = routeBaseProperties.getCommandConfig();
        final var maxTimeout = commandConfig.getMaxTimeout();

        // check if the timeout is smaller than the maximum possible timeout and > 0:
        if (timeout.isNegative() || timeout.compareTo(maxTimeout) > 0) {
            throw GatewayTimeoutInvalidException.newBuilder(timeout, maxTimeout).build();
        }
        return timeout;
    }

}
