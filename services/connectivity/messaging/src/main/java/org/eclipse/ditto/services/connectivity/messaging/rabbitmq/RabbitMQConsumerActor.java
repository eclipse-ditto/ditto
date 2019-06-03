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
package org.eclipse.ditto.services.connectivity.messaging.rabbitmq;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.common.CharsetDeterminer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.placeholders.EnforcementFactoryFactory;
import org.eclipse.ditto.model.placeholders.EnforcementFilterFactory;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.services.connectivity.messaging.BaseConsumerActor;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageBuilder;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;


/**
 * Actor which receives message from an RabbitMQ source and forwards them to a {@code MessageMappingProcessorActor}.
 */
public final class RabbitMQConsumerActor extends BaseConsumerActor {

    private static final String MESSAGE_ID_HEADER = "messageId";
    private static final Set<String> CONTENT_TYPES_INTERPRETED_AS_TEXT = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("text/plain", "text/html", "text/yaml", "application/json", "application/xml")));

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final EnforcementFilterFactory<Map<String, String>, String> headerEnforcementFilterFactory;

    @SuppressWarnings("unused")
    private RabbitMQConsumerActor(final String connectionId, final String sourceAddress,
            final ActorRef messageMappingProcessor, final AuthorizationContext authorizationContext,
            @Nullable final Enforcement enforcement, @Nullable final HeaderMapping headerMapping) {
        super(connectionId, sourceAddress, messageMappingProcessor, authorizationContext, headerMapping);
        headerEnforcementFilterFactory =
                enforcement != null ? EnforcementFactoryFactory.newEnforcementFilterFactory(enforcement,
                        PlaceholderFactory.newHeadersPlaceholder()) : input -> null;
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code RabbitMQConsumerActor}.
     *
     * @param connectionId ID of the connection
     * @param source the source of messages
     * @param messageMappingProcessor the message mapping processor where received messages are forwarded to
     * @param authorizationContext the authorization context of this source
     * @param enforcement the enforcement configuration
     * @param headerMapping optional header mappings
     * @return the Akka configuration Props object.
     */
    static Props props(final String source, final ActorRef messageMappingProcessor, final
    AuthorizationContext authorizationContext, @Nullable final Enforcement enforcement,
            @Nullable final HeaderMapping headerMapping, final String connectionId) {

        return Props.create(RabbitMQConsumerActor.class, connectionId, source, messageMappingProcessor,
                                authorizationContext, enforcement, headerMapping);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Delivery.class, this::handleDelivery)
                .match(ResourceStatus.class, this::handleAddressStatus)
                .match(RetrieveAddressStatus.class, ram -> getSender().tell(getCurrentSourceStatus(), getSelf()))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void handleDelivery(final Delivery delivery) {
        final BasicProperties properties = delivery.getProperties();
        final Envelope envelope = delivery.getEnvelope();
        final byte[] body = delivery.getBody();

        Map<String, String> headers = null;
        try {
            final String correlationId = properties.getCorrelationId();
            LogUtil.enhanceLogWithCorrelationId(log, correlationId);
            if (log.isDebugEnabled()) {
                log.debug("Received message from RabbitMQ ({}//{}): {}", envelope, properties,
                        new String(body, StandardCharsets.UTF_8));
            }
            headers = extractHeadersFromMessage(properties, envelope);
            final ExternalMessageBuilder externalMessageBuilder =
                    ExternalMessageFactory.newExternalMessageBuilder(headers);
            final String contentType = properties.getContentType();
            if (shouldBeInterpretedAsText(contentType)) {
                final String text = new String(body, CharsetDeterminer.getInstance().apply(contentType));
                externalMessageBuilder.withText(text);
            } else {
                externalMessageBuilder.withBytes(body);
            }
            externalMessageBuilder.withAuthorizationContext(authorizationContext);
            externalMessageBuilder.withEnforcement(headerEnforcementFilterFactory.getFilter(headers));
            externalMessageBuilder.withHeaderMapping(headerMapping);
            externalMessageBuilder.withSourceAddress(sourceAddress);
            final ExternalMessage externalMessage = externalMessageBuilder.build();
            inboundCounter.recordSuccess();
            messageMappingProcessor.forward(externalMessage, getContext());
        } catch (final DittoRuntimeException e) {
            log.warning("Processing delivery {} failed: {}", envelope.getDeliveryTag(), e.getMessage(), e);
            inboundCounter.recordFailure();
            if (headers != null) {
                // send response if headers were extracted successfully
                messageMappingProcessor.forward(e.setDittoHeaders(DittoHeaders.of(headers)), getContext());
            }
        } catch (final Exception e) {
            log.warning("Processing delivery {} failed: {}", envelope.getDeliveryTag(), e.getMessage(), e);
            inboundCounter.recordFailure();
        }
    }

    private static boolean shouldBeInterpretedAsText(@Nullable final String contentType) {
        return contentType != null && CONTENT_TYPES_INTERPRETED_AS_TEXT.stream().anyMatch(contentType::startsWith);
    }

    private static Map<String, String> extractHeadersFromMessage(final BasicProperties properties,
            final Envelope envelope) {

        final Map<String, String> headersFromProperties = getHeadersFromProperties(properties.getHeaders());

        // set headers specific to rmq messages
        if (properties.getReplyTo() != null) {
            headersFromProperties.put(ExternalMessage.REPLY_TO_HEADER, properties.getReplyTo());
        }
        if (properties.getCorrelationId() != null) {
            headersFromProperties.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), properties.getCorrelationId());
        }
        if (properties.getContentType() != null) {
            headersFromProperties.put(ExternalMessage.CONTENT_TYPE_HEADER, properties.getContentType());
        }
        headersFromProperties.put(MESSAGE_ID_HEADER, Long.toString(envelope.getDeliveryTag()));

        return headersFromProperties;
    }

    private static Map<String, String> getHeadersFromProperties(@Nullable final Map<String, Object> originalProps) {
        if (null != originalProps) {
            return originalProps.entrySet()
                    .stream()
                    .filter(entry -> Objects.nonNull(entry.getValue()))
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue())));
        }

        return new HashMap<>();
    }

}
