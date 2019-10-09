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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersSizeChecker;
import org.eclipse.ditto.model.connectivity.ConnectionId;
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
import org.eclipse.ditto.services.models.connectivity.MappedInboundExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.ActorSystem;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Processes incoming {@link ExternalMessage}s to {@link Signal}s and {@link Signal}s back to {@link ExternalMessage}s.
 * Encapsulates the message processing logic from the message mapping processor actor.
 */
public final class MessageMappingProcessor {

    private final ConnectionId connectionId;
    private final MessageMapperRegistry registry;
    private final DiagnosticLoggingAdapter log;
    private final ProtocolAdapter protocolAdapter;
    private final DittoHeadersSizeChecker dittoHeadersSizeChecker;

    private MessageMappingProcessor(final ConnectionId connectionId,
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
     * @param mappings the configured mappings used by this processor
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
    public static MessageMappingProcessor of(final ConnectionId connectionId,
            final Map<String, MappingContext> mappings,
            final ActorSystem actorSystem,
            final ConnectivityConfig connectivityConfig,
            final ProtocolAdapterProvider protocolAdapterProvider,
            final DiagnosticLoggingAdapter log) {

        final MessageMapperFactory messageMapperFactory =
                DefaultMessageMapperFactory.of(connectionId, actorSystem, connectivityConfig.getMappingConfig(), log);
        final MessageMapperRegistry registry =
                messageMapperFactory.registryOf(DittoMessageMapper.CONTEXT, mappings);

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
     * Processes an {@link ExternalMessage} which may result in 0..n messages/errors.
     *
     * @param message the inbound {@link ExternalMessage} to be processed
     * @param resultHandler handles the 0..n results of the mapping(s)
     */
    void process(final ExternalMessage message,
            final MappingResultHandler<MappedInboundExternalMessage> resultHandler) {
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log,
                message.getHeaders().get(CORRELATION_ID.getKey()), connectionId);
        final List<MessageMapper> mappers = getMappers(message);
        log.debug("Mappers resolved for message: {}", mappers);
        mappers.forEach(mapper -> {
            final MappingTimer mappingTimer = MappingTimer.inbound(connectionId);
            mappingTimer.overall(() -> convertInboundMessage(mapper, message, mappingTimer, resultHandler));
        });
    }

    /**
     * Processes an {@link OutboundSignal} to 0..n {@link ExternalMessage}s.
     *
     * @param outboundSignal the outboundSignal to be processed
     * @param resultHandler handles the 0..n results of the mapping(s)
     */
    void process(final OutboundSignal outboundSignal, final MappingResultHandler<ExternalMessage> resultHandler) {
        final MappingTimer timer = MappingTimer.outbound(connectionId);
        final Adaptable adaptable = timer.protocol(() -> protocolAdapter.toAdaptable(outboundSignal.getSource()));
        enhanceLogFromAdaptable(adaptable);
        final List<MessageMapper> mappers = getMappers(outboundSignal);
        log.debug("Resolved mappers for message {} to targets {}: {}",
                outboundSignal.getSource().getDittoHeaders().getCorrelationId(), outboundSignal.getTargets(), mappers);
        mappers.forEach(mapper -> convertOutboundMessage(adaptable, mapper, timer, resultHandler));
    }

    private void convertInboundMessage(final MessageMapper mapper, final ExternalMessage message,
            final MappingTimer timer,
            MappingResultHandler<MappedInboundExternalMessage> handler) {
        try {
            checkNotNull(message, "message");
            log.debug("Mapping message using mapper {}.", mapper.getId());
            final List<Adaptable> adaptables = timer.payload(mapper.getId(), () -> mapper.map(message));
            if (isNullOrEmpty(adaptables)) {
                handler.onMessageDropped();
            } else {
                adaptables.forEach(adaptable -> {
                    enhanceLogFromAdaptable(adaptable);
                    final Signal<?> signal = timer.protocol(() -> protocolAdapter.fromAdaptable(adaptable));
                    dittoHeadersSizeChecker.check(signal.getDittoHeaders());
                    handler.onMessageMapped(MappedInboundExternalMessage.of(message, adaptable.getTopicPath(), signal));
                });
            }
        } catch (final DittoRuntimeException e) {
            handler.onException(e);
        } catch (final Exception e) {
            final MessageMappingFailedException mappingFailedException = buildMappingFailedException("inbound",
                    message.findContentType().orElse(""), mapper.getId(), DittoHeaders.of(message.getHeaders()), e);
            handler.onException(mappingFailedException);
        }
    }

    private void convertOutboundMessage(final Adaptable adaptable, final MessageMapper mapper,
            final MappingTimer timer, final MappingResultHandler<ExternalMessage> resultHandler) {
        try {

            log.debug("Applying mapper {} to message {}", mapper.getId(),
                    adaptable.getDittoHeaders().getCorrelationId());

            final List<ExternalMessage> messages = timer.payload(mapper.getId(),
                    () -> toStream(mapper.map(adaptable))
                            .map(em -> ExternalMessageFactory.newExternalMessageBuilder(em)
                                    .withTopicPath(adaptable.getTopicPath())
                                    // TODO check if same as signal.getDittoHeaders()
                                    .withInternalHeaders(adaptable.getDittoHeaders())
                                    .build())
                            .collect(Collectors.toList()));

            log.debug("Mapping '{}' produced {} messages.", mapper.getId(), messages.size());

            if (messages.isEmpty()) {
                resultHandler.onMessageDropped();
            } else {
                messages.forEach(resultHandler::onMessageMapped);
            }
        } catch (final DittoRuntimeException e) {
            resultHandler.onException(e);
        } catch (final Exception e) {
            final Optional<DittoHeaders> headers = adaptable.getHeaders();
            final String contentType = headers.map(h -> h.get(ExternalMessage.CONTENT_TYPE_HEADER)).orElse("");
            final MessageMappingFailedException mappingFailedException =
                    buildMappingFailedException("outbound", contentType, mapper.getId(),
                            headers.orElseGet(DittoHeaders::empty), e);
            resultHandler.onException(mappingFailedException);
        }
    }

    private <T> Stream<T> toStream(@Nullable final List<T> messages) {
        return messages == null ? Stream.empty() : messages.stream();
    }

    private static boolean isNullOrEmpty(final List<?> messages) {
        return messages == null || messages.isEmpty();
    }

    private List<MessageMapper> getMappers(final ExternalMessage message) {
        final Optional<String> contentTypeOpt = message.findContentType();
        if (contentTypeOpt.isPresent()) {
            final String contentType = contentTypeOpt.get();
            if (registry.getDefaultMapper().getContentType().filter(contentType::equals).isPresent()) {
                log.info("Selected Default MessageMapper for mapping ExternalMessage as content-type matched <{}>",
                        contentType);
                // TODO check why we ignore any custom mapping in this case. keep this behavior?
                return Collections.singletonList(registry.getDefaultMapper());
            }
        }

        final List<MessageMapper> mappings = registry.getMappers(message.getPayloadMapping());
        if (mappings.isEmpty()) {
            log.debug("Falling back to Default MessageMapper for mapping ExternalMessage " +
                    "as no MessageMapper was present: {}", message);
            return Collections.singletonList(registry.getDefaultMapper());
        } else {
            return mappings;
        }
    }

    private List<MessageMapper> getMappers(final OutboundSignal outboundSignal) {
        final List<String> mappings;
        if (outboundSignal.getTargets().isEmpty()) {
            // responses/errors do not have a target assigned, read mapper from internal header
            mappings = outboundSignal.getSource()
                    .getDittoHeaders()
                    .getMapper()
                    .map(Collections::singletonList)
                    .orElseGet(Collections::emptyList);
        } else {
            // note: targets have been grouped by mapping -> all targets have the same mapping here
            mappings = outboundSignal.getTargets().get(0).getMapping();
        }

        final List<MessageMapper> mappers = registry.getMappers(mappings);
        if (mappers.isEmpty()) {
            log.debug("Falling back to Default MessageMapper for mapping as no MessageMapper was present: {}",
                    outboundSignal);
            return Collections.singletonList(registry.getDefaultMapper());
        } else {
            return mappers;
        }
    }

    private void enhanceLogFromAdaptable(final Adaptable adaptable) {
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, adaptable, connectionId);
    }

    private MessageMappingFailedException buildMappingFailedException(final String direction,
            final String contentType, final String mapperId, final DittoHeaders dittoHeaders, final Exception e) {
        final String description =
                String.format("Could not map %s message with mapper '%s' due to unknown problem: %s %s",
                        direction, mapperId, e.getClass().getSimpleName(), e.getMessage());
        return MessageMappingFailedException.newBuilder(contentType)
                .description(description)
                .dittoHeaders(dittoHeaders)
                .cause(e)
                .build();
    }
}
