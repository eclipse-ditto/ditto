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
package org.eclipse.ditto.gateway.service.endpoints.routes.things;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import org.eclipse.ditto.base.model.exceptions.TimeoutInvalidException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.gateway.service.endpoints.actors.AbstractHttpRequestActor;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.gateway.service.util.config.endpoints.MessageConfig;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.messages.model.MessageBuilder;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.messages.model.MessagesModelFactory;
import org.eclipse.ditto.messages.model.SubjectInvalidException;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandSizeValidator;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.ThingId;

import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpCharset;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.RequestEntity;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
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

    private final Duration maxMessageTimeout;
    private final Duration maxClaimTimeout;

    /**
     * Constructs a {@code MessagesRoute} object.
     *
     * @param routeBaseProperties the base properties of the route.
     * @param messageConfig the MessageConfig.
     * @param claimMessageConfig the MessageConfig for claim messages.
     * @throws NullPointerException if any argument is {@code null}.
     */
    MessagesRoute(final RouteBaseProperties routeBaseProperties,
            final MessageConfig messageConfig,
            final MessageConfig claimMessageConfig) {

        super(routeBaseProperties);
        maxMessageTimeout = messageConfig.getMaxTimeout();
        maxClaimTimeout = claimMessageConfig.getMaxTimeout();
    }

    /**
     * Builds the {@code /{inbox|outbox}} sub route for the things route.
     *
     * @return the {@code /{inbox|outbox}} route.
     */
    public Route buildThingsInboxOutboxRoute(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {

        return concat(
                // /inbox/claim
                claimMessages(ctx, dittoHeaders, thingId),
                rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment(INBOX_OUTBOX_PATTERN)),
                        // /<inbox|outbox>
                        inboxOutbox -> post(() -> thingMessages(ctx, dittoHeaders, thingId, inboxOutbox))
                )
        );
    }

    /**
     * Builds the {@code /{inbox|outbox}} sub route for the features route.
     *
     * @return the {@code /{inbox|outbox}} route.
     */
    public Route buildFeaturesInboxOutboxRoute(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final ThingId thingId,
            final String featureId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment(INBOX_OUTBOX_PATTERN)),
                // /<inbox|outbox>
                inboxOutbox -> post(() -> featureMessages(ctx, dittoHeaders, thingId, featureId, inboxOutbox))
        );
    }

    /*
     * Describes {@code /inbox/claim} route.
     *
     * @return route for claim messages resource.
     */
    private Route claimMessages(final RequestContext ctx, final DittoHeaders dittoHeaders, final ThingId thingId) {
        return rawPathPrefix(PathMatchers.slash().concat(PATH_INBOX), () -> // /inbox
                rawPathPrefix(PathMatchers.slash().concat(PATH_CLAIM), () -> // /inbox/claim
                        post(() ->
                                pathEndOrSingleSlash(() ->
                                        withCustomRequestTimeout(dittoHeaders.getTimeout().orElse(null),
                                                this::checkClaimTimeout,
                                                timeout ->
                                                        extractDataBytes(payloadSource ->
                                                                handleMessage(ctx, payloadSource,
                                                                        buildSendClaimMessage(
                                                                                ctx,
                                                                                dittoHeaders,
                                                                                thingId,
                                                                                timeout
                                                                        )
                                                                )
                                                        )
                                        )
                                )
                        )
                )
        );
    }

    /*
     * Describes {@code /messages/<messageSubject>} sub route of the things route: {@code
     * ../things/<thingId>/{inbox|outbox}}.
     *
     * @return the sub route for things messages resource.
     */
    private Route thingMessages(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final ThingId thingId,
            final String inboxOutbox) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment(PATH_MESSAGES).slash()),
                () -> // /messages
                        extractUnmatchedPath(msgSubject -> // <msgSubject/with/slashes>
                                withCustomRequestTimeout(dittoHeaders.getTimeout().orElse(null),
                                        this::checkMessageTimeout,
                                        timeout ->
                                                extractDataBytes(payloadSource ->
                                                        handleMessage(ctx, payloadSource,
                                                                buildSendThingMessage(
                                                                        getMessageDirection(inboxOutbox),
                                                                        ctx,
                                                                        dittoHeaders,
                                                                        thingId,
                                                                        msgSubject,
                                                                        timeout
                                                                )
                                                        )
                                                )
                                )
                        )

        );
    }

    private static MessageDirection getMessageDirection(final String inboxOutbox) {
        return PATH_INBOX.equalsIgnoreCase(inboxOutbox) ? MessageDirection.TO : MessageDirection.FROM;
    }

    /*
     * Describes {@code /messages/<messageSubject>} sub route of the features route: {@code
     * ../features/<featureId>/{inbox|outbox}}.
     *
     * @return the sub route for feature messages resource.
     */
    private Route featureMessages(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final ThingId thingId,
            final String featureId,
            final String inboxOutbox) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment(PATH_MESSAGES).slash()),
                () -> // /messages
                        extractUnmatchedPath(msgSubject -> // /messages/<msgSubject/with/slashes>
                                withCustomRequestTimeout(dittoHeaders.getTimeout().orElse(null),
                                        this::checkMessageTimeout,
                                        timeout ->
                                                extractDataBytes(payloadSource ->
                                                        handleMessage(ctx, payloadSource,
                                                                buildSendFeatureMessage(
                                                                        getMessageDirection(inboxOutbox),
                                                                        ctx,
                                                                        dittoHeaders,
                                                                        thingId,
                                                                        featureId,
                                                                        msgSubject,
                                                                        timeout
                                                                )
                                                        )
                                                )
                                )
                        )
        );
    }

    private static Function<ByteBuffer, MessageCommand<?, ?>> buildSendThingMessage(final MessageDirection direction,
            final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final ThingId thingId,
            final String msgSubject,
            final Duration timeout) {

        return payload -> {
            final HttpRequest httpRequest = ctx.getRequest();
            final ContentType contentType = httpRequest.entity().getContentType();

            final MessageHeaders headers = MessageHeaders.newBuilder(direction, thingId, normalizeSubject(msgSubject))
                    .correlationId(dittoHeaders.getCorrelationId().orElse(null))
                    .contentType(contentType.toString())
                    .timestamp(OffsetDateTime.now())
                    .build();

            final MessageBuilder<Object> messageBuilder = initMessageBuilder(payload, contentType, headers, httpRequest);
            return SendThingMessage.of(thingId, messageBuilder.build(), enhanceHeaders(dittoHeaders, timeout));
        };
    }

    private static Function<ByteBuffer, MessageCommand<?, ?>> buildSendFeatureMessage(final MessageDirection direction,
            final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final ThingId thingId,
            final String featureId,
            final String msgSubject,
            final Duration timeout) {

        final HttpRequest httpRequest = ctx.getRequest();

        return payload -> {
            final ContentType contentType = httpRequest.entity()
                    .getContentType();

            final MessageHeaders headers = MessageHeaders.newBuilder(direction, thingId, normalizeSubject(msgSubject))
                    .featureId(featureId)
                    .correlationId(dittoHeaders.getCorrelationId().orElse(null))
                    .contentType(contentType.toString())
                    .timestamp(OffsetDateTime.now())
                    .build();

            final MessageBuilder<Object> messageBuilder = initMessageBuilder(payload, contentType, headers, httpRequest);
            return SendFeatureMessage.of(thingId, featureId, messageBuilder.build(),
                    enhanceHeaders(dittoHeaders, timeout));
        };
    }

    private static String normalizeSubject(final String msgSubject) {
        if (msgSubject.isEmpty() || "/".equals(msgSubject)) {
            throw SubjectInvalidException.newBuilder(msgSubject).build();
        }

        return msgSubject.charAt(0) == '/' ? msgSubject.substring(1) : msgSubject;
    }

    private static Function<ByteBuffer, MessageCommand<?, ?>> buildSendClaimMessage(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final ThingId thingId,
            final Duration timeout) {

        return payload -> {
            final ContentType contentType = ctx.getRequest()
                    .entity()
                    .getContentType();

            final MessageHeaders headers = MessageHeaders.newBuilderForClaiming(thingId)
                    .correlationId(dittoHeaders.getCorrelationId().orElse(null))
                    .contentType(contentType.toString())
                    .timestamp(OffsetDateTime.now())
                    .build();

            final MessageBuilder<Object> messageBuilder =
                    initMessageBuilder(payload, contentType, headers, ctx.getRequest());

            return SendClaimMessage.of(thingId, messageBuilder.build(), enhanceHeaders(dittoHeaders, timeout));
        };
    }

    private static DittoHeaders enhanceHeaders(final DittoHeaders dittoHeaders, final Duration timeout) {

        final DittoHeadersBuilder<?, ?> headersBuilder = dittoHeaders.toBuilder()
                .channel(TopicPath.Channel.LIVE.getName());
        if (timeout.toMillis() % 1000 == 0) {
            // omit the milliseconds, required for backwards compatibility - not sending the unit along
            headersBuilder.timeout(String.valueOf(timeout.getSeconds()));
        } else {
            headersBuilder.timeout(timeout);
        }

        return headersBuilder.build();
    }

    private static MessageBuilder<Object> initMessageBuilder(final ByteBuffer payload, final ContentType contentType,
            final MessageHeaders headers, final HttpRequest request) {

        if (hasZeroContentLength(request)) {
            // don't define any payload if a message has explicit content-length of 0.
            return createMessageBuilderWithoutPayload(headers);
        }

        return createMessageBuilderWithPayload(payload, contentType, headers);
    }

    private static MessageBuilder<Object> createMessageBuilderWithoutPayload(final MessageHeaders headers) {
        return MessagesModelFactory.newMessageBuilder(headers);
    }

    private static MessageBuilder<Object> createMessageBuilderWithPayload(final ByteBuffer payload,
            final ContentType contentType,  final MessageHeaders headers) {
        final ByteBuffer payloadWithoutOffset = ByteBuffer.wrap(payload.array());

        MessageCommandSizeValidator.getInstance().ensureValidSize(() ->
                payloadWithoutOffset.array().length, () -> headers);

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
        runWithSupervisionStrategy(payloadSource.fold(ByteString.emptyByteString(), ByteString::concat)
                .map(ByteString::toArray)
                .map(ByteBuffer::wrap)
                .map(requestPayloadToCommandFunction)
                .to(Sink.actorRef(createHttpPerRequestActor(ctx, httpResponseFuture),
                        AbstractHttpRequestActor.COMPLETE_MESSAGE))
        );

        return completeWithFuture(preprocessResponse(httpResponseFuture));
    }

    private Duration checkMessageTimeout(final Duration timeout) {
        // check if the timeout is smaller than the maximum possible message-timeout and > 0:
        if (timeout.isNegative() || timeout.getSeconds() > maxMessageTimeout.getSeconds()) {
            throw TimeoutInvalidException.newBuilder(timeout, maxMessageTimeout).build();
        }
        return timeout;
    }

    private Duration checkClaimTimeout(final Duration timeout) {
        // check if the timeout is smaller than the maximum possible claim-timeout and > 0:
        if (timeout.isNegative() || timeout.getSeconds() > maxClaimTimeout.getSeconds()) {
            throw TimeoutInvalidException.newBuilder(timeout, maxClaimTimeout).build();
        }

        return timeout;
    }

    /**
     * Check if the request has a Content-Length of {@code 0}, indicating it has no payload. The non-existence of
     * Content-Length in a message will return {@code false}, as the akka documentation states that "in many cases
     * it's dangerous to rely on the (non-)existence of a content-length" (see {@link RequestEntity#getContentLengthOption()}).
     *
     * @param request The request.
     * @return {@code true} if the message contains Content-Length {@code 0}; {@code false} if no Content-Length is
     * found or it isn't {@code 0}.
     * @see RequestEntity#getContentLengthOption()
     */
    private static boolean hasZeroContentLength(final HttpRequest request) {
        final OptionalLong contentLengthOption = request.entity().getContentLengthOption();

        return contentLengthOption.isPresent() && 0 == contentLengthOption.getAsLong();
    }

}
