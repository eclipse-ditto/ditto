/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.headers.DittoHeaderDefinition.CORRELATION_ID;

import java.time.Instant;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressMetric;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.placeholder.HeadersPlaceholder;
import org.eclipse.ditto.services.models.connectivity.placeholder.PlaceholderFactory;
import org.eclipse.ditto.services.models.connectivity.placeholder.PlaceholderFilter;
import org.eclipse.ditto.services.models.connectivity.placeholder.ThingPlaceholder;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;

import akka.actor.AbstractActor;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Base class for publisher actors. Holds the map of configured targets.
 *
 * @param <T> the type of targets for this actor
 */
public abstract class BasePublisherActor<T extends PublishTarget> extends AbstractActor {

    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();
    private static final ThingPlaceholder THING_PLACEHOLDER = PlaceholderFactory.newThingPlaceholder();

    protected long publishedMessages = 0L;
    protected Instant lastMessagePublishedAt;
    protected AddressMetric addressMetric;

    protected BasePublisherActor() {
        addressMetric =
                ConnectivityModelFactory.newAddressMetric(ConnectionStatus.OPEN, "Started at " + Instant.now(),
                        0, null);
    }

    @Override
    public Receive createReceive() {
        final ReceiveBuilder receiveBuilder = receiveBuilder();
        preEnhancement(receiveBuilder);

        receiveBuilder
                .match(OutboundSignal.WithExternalMessage.class, this::isResponseOrError, outbound -> {
                    final ExternalMessage response = outbound.getExternalMessage();
                    final String correlationId = response.getHeaders().get(CORRELATION_ID.getKey());
                    LogUtil.enhanceLogWithCorrelationId(log(), correlationId);

                    final String replyToFromHeader = response.getHeaders().get(ExternalMessage.REPLY_TO_HEADER);
                    if (replyToFromHeader != null) {
                        final T replyTarget = toReplyTarget(replyToFromHeader);
                        log().info("Publishing mapped response/error message of type <{}> to reply target <{}>",
                                outbound.getSource().getType(), replyTarget);
                        log().debug("Publishing mapped response/error message of type <{}> to reply target <{}>: {}",
                                outbound.getSource().getType(), replyTarget, response);

                        publishMessage(null, replyTarget, response);
                    } else {
                        log().info("Response dropped, missing replyTo address: {}", response);
                    }
                })
                .match(OutboundSignal.WithExternalMessage.class, outbound -> {
                    final ExternalMessage message = outbound.getExternalMessage();
                    final String correlationId = message.getHeaders().get(CORRELATION_ID.getKey());
                    LogUtil.enhanceLogWithCorrelationId(log(), correlationId);

                    final Signal<?> outboundSource = outbound.getSource();
                    log().debug("Publishing mapped message of type <{}> to targets <{}>: {}",
                            outboundSource.getType(), outbound.getTargets(), message);
                    outbound.getTargets().forEach(target -> {
                        log().info("Publishing mapped message of type <{}> to target address <{}>",
                                outboundSource.getType(), target.getAddress());
                        try {
                            final T publishTarget = toPublishTarget(target.getAddress());
                            final ExternalMessage messageWithMappedHeaders = applyHeaderMapping(outbound, target);
                            publishMessage(target, publishTarget, messageWithMappedHeaders);
                        } catch (final DittoRuntimeException e) {
                            log().warning("Got unexpected DittoRuntimeException when applying header mapping - " +
                                            "thus NOT publishing the message: {} {}",
                                    e.getClass().getSimpleName(), e.getMessage());
                        }
                    });
                })
                .match(AddressMetric.class, this::handleAddressMetric)
                .match(RetrieveAddressMetric.class, ram -> getSender().tell(ConnectivityModelFactory.newAddressMetric(
                        addressMetric != null ? addressMetric.getStatus() : ConnectionStatus.UNKNOWN,
                        addressMetric != null ? addressMetric.getStatusDetails().orElse(null) : null,
                        publishedMessages, lastMessagePublishedAt), getSelf())
                )
                .matchAny(m -> {
                    log().warning("Unknown message: {}", m);
                    unhandled(m);
                });

        postEnhancement(receiveBuilder);
        return receiveBuilder.build();
    }

    /**
     * Provides the possibility to add custom matchers before applying the default matchers of the BasePublisherActor.
     *
     * @param receiveBuilder the ReceiveBuilder to add other matchers to.
     */
    protected abstract void preEnhancement(final ReceiveBuilder receiveBuilder);

    /**
     * Provides the possibility to add custom matchers after applying the default matchers of the BasePublisherActor.
     *
     * @param receiveBuilder the ReceiveBuilder to add other matchers to.
     */
    protected abstract void postEnhancement(final ReceiveBuilder receiveBuilder);

    /**
     * Converts the passed {@code address} to a {@link PublishTarget} of type {@code <T>}.
     *
     * @param address the address to convert to a {@link PublishTarget} of type {@code <T>}.
     * @return the instance of type {@code <T>}
     */
    protected abstract T toPublishTarget(final String address);

    /**
     * Converts the passed {@code replyToAddress} to a {@link PublishTarget} of type {@code <T>}.
     *
     * @param replyToAddress the replyTo address to convert to a {@link PublishTarget} of type {@code <T>}.
     * @return the instance of type {@code <T>}
     */
    protected abstract T toReplyTarget(final String replyToAddress);

    /**
     * Publishes the passed {@code message} to the passed {@code publishTarget}.
     *
     * @param target the nullable Target for getting even more information about the configured Target to publish to.
     * @param publishTarget the {@link PublishTarget} to publish to.
     * @param message the {@link ExternalMessage} to publish.
     */
    protected abstract void publishMessage(@Nullable final Target target,
            final T publishTarget, final ExternalMessage message);

    /**
     * @return the logger to use.
     */
    protected abstract DiagnosticLoggingAdapter log();

    /**
     * Checks whether the passed in {@code outboundSignal} is a response or an error.
     *
     * @param outboundSignal the OutboundSignal to check.
     * @return {@code true} if the OutboundSignal is a response or an error, {@code false} otherwise
     */
    private boolean isResponseOrError(final OutboundSignal.WithExternalMessage outboundSignal) {
        return (outboundSignal.getExternalMessage().isResponse() || outboundSignal.getExternalMessage().isError());
    }

    /**
     * Applies the optional "header mapping" potentially configured on the passed {@code target} on the passed {@code
     * outboundSignal}.
     *
     * @param outboundSignal the OutboundSignal containing the {@link ExternalMessage} with headers potentially
     * containing placeholders.
     * @param target the {@link Target} to extract the {@link org.eclipse.ditto.model.connectivity.HeaderMapping} from.
     * @return the ExternalMessage with replaced headers
     */
    private ExternalMessage applyHeaderMapping(final OutboundSignal.WithExternalMessage outboundSignal,
            final Target target) {
        final ExternalMessage message = outboundSignal.getExternalMessage();
        return target.getHeaderMapping().map(mapping -> {
            if (mapping.getMapping().isEmpty()) {
                return message;
            }
            final Map<String, String> originalHeaders = new HashMap<>(message.getHeaders());
            final Signal<?> sourceSignal = outboundSignal.getSource();
            if (sourceSignal instanceof MessageCommand) {
                // we must access the message headers to get hold of the message subject - only copy the message subject
                // to the headers:
                final MessageHeaders messageHeaders = ((MessageCommand) sourceSignal).getMessage().getHeaders();
                originalHeaders.put(MessageHeaderDefinition.SUBJECT.getKey(), messageHeaders.getSubject());
            }

            LogUtil.enhanceLogWithCorrelationId(log(), sourceSignal);
            final Map<String, String> mappedHeaders = mapping.getMapping().entrySet().stream()
                    .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(),
                            PlaceholderFilter.apply(e.getValue(), originalHeaders, HEADERS_PLACEHOLDER, true)))
                    .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(),
                            PlaceholderFilter.apply(e.getValue(), sourceSignal.getId(), THING_PLACEHOLDER, true)))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            log().debug("Result of header mapping <{}>: {}", mapping, mappedHeaders);
            return message.withHeaders(mappedHeaders);
        }).orElse(message);
    }

    private void handleAddressMetric(final AddressMetric addressMetric) {
        this.addressMetric = addressMetric;
    }
}
