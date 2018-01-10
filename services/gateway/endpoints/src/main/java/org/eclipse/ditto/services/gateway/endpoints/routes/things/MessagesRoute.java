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
package org.eclipse.ditto.services.gateway.endpoints.routes.things;

import static akka.http.javadsl.server.Directives.completeWithFuture;
import static akka.http.javadsl.server.Directives.extractDataBytes;
import static akka.http.javadsl.server.Directives.extractUnmatchedPath;
import static akka.http.javadsl.server.Directives.parameterOptional;
import static akka.http.javadsl.server.Directives.pathEndOrSingleSlash;
import static akka.http.javadsl.server.Directives.post;
import static akka.http.javadsl.server.Directives.rawPathPrefix;
import static akka.http.javadsl.server.Directives.route;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers.mergeDoubleSlashes;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.messages.MessageBuilder;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.messages.MessagesModelFactory;
import org.eclipse.ditto.model.messages.SubjectInvalidException;
import org.eclipse.ditto.model.messages.TimeoutInvalidException;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.gateway.endpoints.HttpRequestActor;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessage;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpCharset;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMessage;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.japi.function.Function;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

/**
 * Builder for creating Akka HTTP routes for MessagesService.
 */
final class MessagesRoute extends AbstractRoute {

    static final String PATH_INBOX = "inbox";
    static final String PATH_OUTBOX = "outbox";
    static final String PATH_MESSAGES = "messages";
    static final String PATH_CLAIM = "claim";

    private static final Pattern INBOX_OUTBOX_PATTERN = Pattern.compile(PATH_INBOX + "|" + PATH_OUTBOX);

    static final String TIMEOUT_PARAMETER = "timeout";
    private static final String X_DITTO_VALIDATION_URL = "x-ditto-validation-url";

    private final Duration defaultMessageTimeout;
    private final Duration maxMessageTimeout;
    private final Duration defaultClaimTimeout;
    private final Duration maxClaimTimeout;
    private final List<String> headerBlacklist;

    /**
     * Constructs the MessagesService route builder.
     *
     * @param proxyActor an actor selection of the command delegating actor.
     * @param actorSystem the ActorSystem.
     * @param defaultMessageTimeout the duration of the default message timeout.
     * @param maxMessageTimeout the max duration of the message timeout.
     * @param defaultClaimTimeout the duration of the default claim timeout.
     * @param maxClaimTimeout the max duration of the claim timeout.
     * @throws NullPointerException if any argument is {@code null}.
     */
    MessagesRoute(final ActorRef proxyActor,
            final ActorSystem actorSystem,
            final Duration defaultMessageTimeout,
            final Duration maxMessageTimeout,
            final Duration defaultClaimTimeout,
            final Duration maxClaimTimeout) {

        super(proxyActor, actorSystem);

        this.defaultMessageTimeout = defaultMessageTimeout;
        this.maxMessageTimeout = maxMessageTimeout;
        this.defaultClaimTimeout = defaultClaimTimeout;
        this.maxClaimTimeout = maxClaimTimeout;

        headerBlacklist = actorSystem.settings().config().getStringList(ConfigKeys.MESSAGE_HEADER_BLACKLIST);
    }

    /**
     * Builds the {@code /{inbox|outbox}} sub route for the things route.
     *
     * @return the {@code /{inbox|outbox}} route.
     */
    public Route buildThingsInboxOutboxRoute(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String thingId) {
        return route(
                claimMessages(ctx, dittoHeaders, thingId), // /inbox/claim
                rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment(INBOX_OUTBOX_PATTERN)),
                        inboxOutbox -> // /<inbox|outbox>
                                post(() -> thingMessages(ctx, dittoHeaders, thingId, inboxOutbox))
                )
        );
    }

    /**
     * Builds the {@code /{inbox|outbox}} sub route for the features route.
     *
     * @return the {@code /{inbox|outbox}} route.
     */
    public Route buildFeaturesInboxOutboxRoute(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String thingId, final String featureId) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment(INBOX_OUTBOX_PATTERN)),
                inboxOutbox -> // /<inbox|outbox>
                        post(() -> featureMessages(ctx, dittoHeaders, thingId, featureId, inboxOutbox))
        );
    }

    /*
    * Describes {@code /inbox/claim} route.
    *
    * @return route for claim messages resource.
    */
    private Route claimMessages(final RequestContext ctx, final DittoHeaders dittoHeaders, final String thingId) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PATH_INBOX), () -> // /inbox
                rawPathPrefix(mergeDoubleSlashes().concat(PATH_CLAIM), () -> // /inbox/claim
                        post(() -> pathEndOrSingleSlash(() ->
                                parameterOptional(Unmarshaller.sync(Long::parseLong), TIMEOUT_PARAMETER,
                                        optionalTimeout ->
                                                extractDataBytes(payloadSource ->
                                                        handleMessage(ctx, payloadSource,
                                                                buildSendClaimMessage(ctx, dittoHeaders, thingId,
                                                                        optionalTimeout))
                                                )
                                )
                        ))
                )
        );
    }

    /*
    * Describes {@code /messages/<messageSubject>} sub route of the things route: {@code
    * ../things/<thingId>/{inbox|outbox}}.
    *
    * @return the sub route for things messages resource.
    */
    private Route thingMessages(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String thingId, final String inboxOutbox) {
        final MessageDirection direction = PATH_INBOX.equalsIgnoreCase(inboxOutbox) ? MessageDirection.TO :
                MessageDirection.FROM;
        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment(PATH_MESSAGES).slash()),
                () -> // /messages
                        extractUnmatchedPath(msgSubject -> // <msgSubject/with/slashes>
                                parameterOptional(Unmarshaller.sync(Long::parseLong), TIMEOUT_PARAMETER,
                                        optionalTimeout ->
                                                extractDataBytes(payloadSource ->
                                                        handleMessage(ctx, payloadSource, buildSendThingMessage(
                                                                direction,
                                                                ctx,
                                                                dittoHeaders,
                                                                thingId,
                                                                msgSubject,
                                                                optionalTimeout)
                                                        )
                                                )
                                )
                        )

        );
    }

    /*
    * Describes {@code /messages/<messageSubject>} sub route of the features route: {@code
    * ../features/<featureId>/{inbox|outbox}}.
    *
    * @return the sub route for feature messages resource.
    */
    private Route featureMessages(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String thingId, final String featureId, final String inboxOutbox) {
        final MessageDirection direction = PATH_INBOX.equalsIgnoreCase(inboxOutbox) ? MessageDirection.TO :
                MessageDirection.FROM;
        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment(PATH_MESSAGES).slash()),
                () -> // /messages
                        extractUnmatchedPath(msgSubject -> // /messages/<msgSubject/with/slashes>
                                parameterOptional(Unmarshaller.sync(Long::parseLong), TIMEOUT_PARAMETER,
                                        optionalTimeout ->
                                                extractDataBytes(payloadSource ->
                                                        handleMessage(ctx, payloadSource, buildSendFeatureMessage(
                                                                direction,
                                                                ctx,
                                                                dittoHeaders,
                                                                thingId,
                                                                featureId,
                                                                msgSubject,
                                                                optionalTimeout)
                                                        )
                                                )
                                )
                        )
        );
    }

    private Function<ByteBuffer, MessageCommand<?, ?>> buildSendThingMessage(final MessageDirection direction,
            final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final String thingId,
            final String msgSubject,
            final Optional<Long> optionalTimeout) {

        return payload -> {
            final HttpRequest httpRequest = ctx.getRequest();
            final ContentType contentType = httpRequest.entity().getContentType();

            final MessageHeaders headers = MessageHeaders.newBuilder(direction, thingId, normalizeSubject(msgSubject))
                    .correlationId(dittoHeaders.getCorrelationId().orElse(null))
                    .contentType(contentType.toString())
                    .timeout(optionalTimeout.map(this::checkMessageTimeout).orElse(defaultMessageTimeout))
                    .timestamp(OffsetDateTime.now())
                    .putHeaders(getHeadersAsMap(httpRequest))
                    .build();

            final MessageBuilder<Object> messageBuilder = initMessageBuilder(payload, contentType, headers);
            return SendThingMessage.of(thingId, messageBuilder.build(), enhanceHeaders(dittoHeaders));
        };
    }

    @Nonnull
    private Map<String, String> getHeadersAsMap(final HttpMessage httpRequest) {
        final Map<String, String> result = new HashMap<>();
        for (final HttpHeader httpHeader : httpRequest.getHeaders()) {
            final String lowerCaseHeaderName = httpHeader.name().toLowerCase();
            if (!headerBlacklist.contains(lowerCaseHeaderName)) {
                result.put(lowerCaseHeaderName, httpHeader.value());
            }
        }
        return result;
    }

    private Function<ByteBuffer, MessageCommand<?, ?>> buildSendFeatureMessage(final MessageDirection direction,
            final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final String thingId,
            final String featureId,
            final String msgSubject,
            final Optional<Long> optionalTimeout) {

        final HttpRequest httpRequest = ctx.getRequest();

        return payload -> {
            final ContentType contentType = httpRequest.entity()
                    .getContentType();

            final MessageHeaders headers = MessageHeaders.newBuilder(direction, thingId, normalizeSubject(msgSubject))
                    .featureId(featureId)
                    .correlationId(dittoHeaders.getCorrelationId().orElse(null))
                    .contentType(contentType
                            .toString())
                    .timeout(optionalTimeout.map(this::checkMessageTimeout).orElse(defaultMessageTimeout))
                    .timestamp(OffsetDateTime.now())
                    .validationUrl(httpRequest.getHeader(X_DITTO_VALIDATION_URL)
                            .map(HttpHeader::value)
                            .orElse(null))
                    .putHeaders(getHeadersAsMap(httpRequest))
                    .build();

            final MessageBuilder<Object> messageBuilder = initMessageBuilder(payload, contentType, headers);
            return SendFeatureMessage.of(thingId, featureId, messageBuilder.build(), enhanceHeaders(dittoHeaders));
        };
    }

    private static String normalizeSubject(final String msgSubject) {
        if (msgSubject.isEmpty() || "/".equals(msgSubject)) {
            throw SubjectInvalidException.newBuilder(msgSubject).build();
        }
        return msgSubject.charAt(0) == '/' ? msgSubject.substring(1) : msgSubject;
    }

    private Function<ByteBuffer, MessageCommand<?, ?>> buildSendClaimMessage(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final String thingId,
            final Optional<Long> optionalTimeout) {

        return payload -> {
            final ContentType contentType = ctx.getRequest()
                    .entity()
                    .getContentType();

            final MessageHeaders headers = MessageHeaders.newBuilderForClaiming(thingId)
                    .correlationId(dittoHeaders.getCorrelationId().orElse(null))
                    .contentType(contentType.toString())
                    .timeout(optionalTimeout.map(this::checkClaimTimeout).orElse(defaultClaimTimeout))
                    .timestamp(OffsetDateTime.now())
                    .putHeaders(getHeadersAsMap(ctx.getRequest()))
                    .build();

            final MessageBuilder<Object> messageBuilder = initMessageBuilder(payload, contentType, headers);
            return SendClaimMessage.of(thingId, messageBuilder.build(), enhanceHeaders(dittoHeaders));
        };
    }

    private DittoHeaders enhanceHeaders(final DittoHeaders dittoHeaders) {
        return dittoHeaders.toBuilder().channel(TopicPath.Channel.LIVE.getName()).build();
    }

    private static MessageBuilder<Object> initMessageBuilder(final ByteBuffer payload, final ContentType contentType,
            final MessageHeaders headers) {

        // reset bytebuffer offset, otherwise payload will not be appended
        final ByteBuffer payloadWithoutOffset = ByteBuffer.wrap(payload.array());

        final MessageBuilder<Object> messageBuilder = MessagesModelFactory.newMessageBuilder(headers)
                .rawPayload(payloadWithoutOffset);

        final Charset charset = contentType.getCharsetOption()
                .map(HttpCharset::nioCharset)
                .orElse(StandardCharsets.UTF_8);

        final String payloadString = charset.decode(payload).toString();
        if (contentType.mediaType().isText()) {
            messageBuilder.payload(payloadString);
        } else if (ContentTypes.APPLICATION_JSON.equals(contentType)) {
            messageBuilder.payload(JsonFactory.readFrom(payloadString));
        }
        return messageBuilder;
    }

    private Route handleMessage(final RequestContext ctx, final Source<ByteString, Object> payloadSource,
            final Function<ByteBuffer, MessageCommand<?, ?>> requestPayloadToCommandFunction) {

        final CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();
        payloadSource.fold(ByteString.empty(), ByteString::concat)
                .map(ByteString::toArray)
                .map(ByteBuffer::wrap)
                .map(requestPayloadToCommandFunction)
                .to(Sink.actorRef(createHttpPerRequestActor(ctx, httpResponseFuture),
                        HttpRequestActor.COMPLETE_MESSAGE))
                .run(materializer);

        return completeWithFuture(httpResponseFuture);
    }

    private Duration checkMessageTimeout(final long timeoutInSeconds) {
        // check if the timeout is smaller than the maximum possible message-timeout and > 0:
        if ((timeoutInSeconds < 0) || (timeoutInSeconds > maxMessageTimeout.getSeconds())) {
            throw new TimeoutInvalidException(timeoutInSeconds, maxMessageTimeout.getSeconds());
        } else {
            return Duration.ofSeconds(timeoutInSeconds);
        }
    }

    private Duration checkClaimTimeout(final long timeoutInSeconds) {
        // check if the timeout is smaller than the maximum possible claim-timeout and > 0:
        if ((timeoutInSeconds < 0) || (timeoutInSeconds > maxClaimTimeout.getSeconds())) {
            throw new TimeoutInvalidException(timeoutInSeconds, maxClaimTimeout.getSeconds());
        } else {
            return Duration.ofSeconds(timeoutInSeconds);
        }
    }

}
