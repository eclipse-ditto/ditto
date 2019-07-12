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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.headers.DittoHeaderDefinition.CORRELATION_ID;

import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersSizeChecker;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMessageMapperFactory;
import org.eclipse.ditto.services.connectivity.mapping.DittoMessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperFactory;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperRegistry;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.InboundExternalMessage;
import org.eclipse.ditto.services.models.connectivity.MappedInboundExternalMessage;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.services.utils.tracing.TracingTags;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.ActorSystem;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Processes incoming {@link ExternalMessage}s to {@link Signal}s and {@link Signal}s back to {@link ExternalMessage}s.
 * Encapsulates the message processing logic from the message mapping processor actor.
 */
public final class MessageMappingProcessor {

    private static final String TIMER_NAME = "connectivity_message_mapping";
    private static final String INBOUND = "inbound";
    private static final String OUTBOUND = "outbound";
    private static final String PAYLOAD_SEGMENT_NAME = "payload";
    private static final String PROTOCOL_SEGMENT_NAME = "protocol";
    private static final String DIRECTION_TAG_NAME = "direction";

    private final String connectionId;
    private final MessageMapperRegistry registry;
    private final DiagnosticLoggingAdapter log;
    private final ProtocolAdapter protocolAdapter;
    private final DittoHeadersSizeChecker dittoHeadersSizeChecker;

    private MessageMappingProcessor(final String connectionId,
            final MessageMapperRegistry registry,
            final DiagnosticLoggingAdapter log,
            final ProtocolAdapter protocolAdapter,
            final DittoHeadersSizeChecker dittoHeadersSizeChecker) {

        this.connectionId = connectionId;
        this.registry = registry;
        this.log = log;
        this.protocolAdapter = protocolAdapter;
        this.dittoHeadersSizeChecker = dittoHeadersSizeChecker;
    }

    /**
     * Initializes a new command processor with mappers defined in mapping mappingContext.
     * The dynamic access is needed to instantiate message mappers for an actor system.
     *
     * @param connectionId the connection that the processor works for.
     * @param mappingContext the mapping Context.
     * @param actorSystem the dynamic access used for message mapper instantiation.
     * @param connectivityConfig the configuration settings of the Connectivity service.
     * @param protocolAdapterProvider provides the ProtocolAdapter to be used.
     * @param log the log adapter.
     * @return the processor instance.
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException if the configuration of
     * one of the {@code mappingContext} is invalid.
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationFailedException if the configuration of
     * one of the {@code mappingContext} failed for a mapper specific reason.
     */
    public static MessageMappingProcessor of(final String connectionId,
            @Nullable final MappingContext mappingContext,
            final ActorSystem actorSystem,
            final ConnectivityConfig connectivityConfig,
            final ProtocolAdapterProvider protocolAdapterProvider,
            final DiagnosticLoggingAdapter log) {

        final MessageMapperFactory messageMapperFactory =
                DefaultMessageMapperFactory.of(connectionId, actorSystem, connectivityConfig.getMappingConfig(), log);
        final MessageMapperRegistry registry =
                messageMapperFactory.registryOf(DittoMessageMapper.CONTEXT, mappingContext);

        final LimitsConfig limitsConfig = connectivityConfig.getLimitsConfig();
        final DittoHeadersSizeChecker dittoHeadersSizeChecker =
                DittoHeadersSizeChecker.of(limitsConfig.getHeadersMaxSize(), limitsConfig.getAuthSubjectsMaxCount());

        return new MessageMappingProcessor(connectionId, registry, log,
                protocolAdapterProvider.getProtocolAdapter(null), dittoHeadersSizeChecker);
    }

    /**
     * @return the message mapper registry to use for mapping messages.
     */
    MessageMapperRegistry getRegistry() {
        return registry;
    }

    /**
     * Processes an ExternalMessage to a Signal.
     *
     * @param message the message
     * @return the signal
     */
    Optional<InboundExternalMessage> process(final ExternalMessage message) {
        final StartedTimer overAllProcessingTimer = startNewTimer().tag(DIRECTION_TAG_NAME, INBOUND);
        return withTimer(overAllProcessingTimer, () -> convertMessage(message, overAllProcessingTimer));
    }

    /**
     * Processes a Signal to an ExternalMessage.
     *
     * @param signal the signal
     * @return the message
     */
    Optional<ExternalMessage> process(final Signal<?> signal) {
        final StartedTimer overAllProcessingTimer = startNewTimer().tag(DIRECTION_TAG_NAME, OUTBOUND);
        return withTimer(overAllProcessingTimer,
                () -> convertToExternalMessage(signal, () -> protocolAdapter.toAdaptable(signal),
                        overAllProcessingTimer));
    }

    private Optional<InboundExternalMessage> convertMessage(final ExternalMessage message,
            final StartedTimer overAllProcessingTimer) {

        checkNotNull(message, "external message");

        try {
            final Optional<Adaptable> adaptableOpt = withTimer(
                    overAllProcessingTimer.startNewSegment(PAYLOAD_SEGMENT_NAME),
                    () -> getMapper(message).map(message));

            return adaptableOpt.map(adaptable -> {
                enhanceLogFromAdaptable(adaptable);
                final Signal<?> signal = MessageMappingProcessor.<Signal<?>>withTimer(
                        overAllProcessingTimer.startNewSegment(PROTOCOL_SEGMENT_NAME),
                        () -> protocolAdapter.fromAdaptable(adaptable));

                dittoHeadersSizeChecker.check(signal.getDittoHeaders());

                return MappedInboundExternalMessage.of(message, adaptable.getTopicPath(), signal);
            });
        } catch (final DittoRuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw MessageMappingFailedException.newBuilder(message.findContentType().orElse(""))
                    .description("Could not map ExternalMessage due to unknown problem: " +
                            e.getClass().getSimpleName() + " " + e.getMessage())
                    .dittoHeaders(DittoHeaders.of(message.getHeaders()))
                    .cause(e)
                    .build();
        }
    }

    private Optional<ExternalMessage> convertToExternalMessage(
            final Signal signal,
            final Supplier<Adaptable> adaptableSupplier,
            final StartedTimer overAllProcessingTimer) {

        checkNotNull(adaptableSupplier);

        try {
            final Adaptable adaptable =
                    withTimer(overAllProcessingTimer.startNewSegment(PROTOCOL_SEGMENT_NAME), adaptableSupplier);

            enhanceLogFromAdaptable(adaptable);

            return withTimer(overAllProcessingTimer.startNewSegment(PAYLOAD_SEGMENT_NAME),
                    () -> getMapper(adaptable)
                            .map(adaptable)
                            .map(em -> ExternalMessageFactory.newExternalMessageBuilder(em)
                                    .withTopicPath(adaptable.getTopicPath())
                                    .withInternalHeaders(signal.getDittoHeaders())
                                    .build())
            );
        } catch (final DittoRuntimeException e) {
            throw e;
        } catch (final Exception e) {
            final Optional<DittoHeaders> headers = adaptableSupplier.get()
                    .getHeaders();
            final String contentType = headers
                    .map(h -> h.get(ExternalMessage.CONTENT_TYPE_HEADER))
                    .orElse("");
            throw MessageMappingFailedException.newBuilder(contentType)
                    .description("Could not map Adaptable due to unknown problem: " + e.getMessage())
                    .dittoHeaders(headers.orElseGet(DittoHeaders::empty))
                    .cause(e)
                    .build();
        }
    }

    private MessageMapper getMapper(final ExternalMessage message) {
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, message.getHeaders().get(CORRELATION_ID.getKey()), connectionId);

        final Optional<String> contentTypeOpt = message.findContentType();
        if (contentTypeOpt.isPresent()) {
            final String contentType = contentTypeOpt.get();
            if (registry.getDefaultMapper().getContentType().filter(contentType::equals).isPresent()) {
                log.info("Selected Default MessageMapper for mapping ExternalMessage as content-type matched <{}>",
                        contentType);
                return registry.getDefaultMapper();
            }
        }

        return registry.getMapper().orElseGet(() -> {
            log.debug("Falling back to Default MessageMapper for mapping ExternalMessage " +
                    "as no MessageMapper was present: {}", message);
            return registry.getDefaultMapper();
        });

    }

    private MessageMapper getMapper(final Adaptable adaptable) {
        enhanceLogFromAdaptable(adaptable);
        return registry.getMapper().orElseGet(() -> {
            log.debug("Falling back to Default MessageMapper for mapping Adaptable as no MessageMapper was present: {}",
                    adaptable);
            return registry.getDefaultMapper();
        });
    }

    private void enhanceLogFromAdaptable(final Adaptable adaptable) {
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, adaptable, connectionId);
    }

    private static <T> T withTimer(final StartedTimer timer, final Supplier<T> supplier) {
        try {
            final T result = supplier.get();
            timer.tag(TracingTags.MAPPING_SUCCESS, true)
                    .stop();
            return result;
        } catch (final Exception ex) {
            timer.tag(TracingTags.MAPPING_SUCCESS, false)
                    .stop();
            throw ex;
        }
    }

    private StartedTimer startNewTimer() {
        return DittoMetrics
                .expiringTimer(TIMER_NAME)
                .tag(TracingTags.CONNECTION_ID, connectionId)
                .expirationHandling(expiredTimer -> expiredTimer.tag(TracingTags.MAPPING_SUCCESS, false))
                .build();
    }

}
