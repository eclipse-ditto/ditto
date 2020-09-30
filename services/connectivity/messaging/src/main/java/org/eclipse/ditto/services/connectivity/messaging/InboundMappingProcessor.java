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

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersSizeChecker;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.model.connectivity.PayloadMappingDefinition;
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
import org.eclipse.ditto.services.models.connectivity.MappedInboundExternalMessage;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.ActorSystem;

/**
 * Processes incoming {@link org.eclipse.ditto.services.models.connectivity.ExternalMessage}s to {@link org.eclipse.ditto.signals.base.Signal}s.
 * Encapsulates the message processing logic from the inbound message mapping processor actor.
 */
public final class InboundMappingProcessor
        extends AbstractMappingProcessor<ExternalMessage, MappedInboundExternalMessage> {

    private final ConnectionId connectionId;
    private final DittoDiagnosticLoggingAdapter logger;
    private final ProtocolAdapter protocolAdapter;
    private final DittoHeadersSizeChecker dittoHeadersSizeChecker;

    private InboundMappingProcessor(final ConnectionId connectionId,
            final MessageMapperRegistry registry,
            final DittoDiagnosticLoggingAdapter logger,
            final ProtocolAdapter protocolAdapter,
            final DittoHeadersSizeChecker dittoHeadersSizeChecker) {

        super(registry, logger, connectionId);
        this.connectionId = connectionId;
        this.logger = logger;
        this.protocolAdapter = protocolAdapter;
        this.dittoHeadersSizeChecker = dittoHeadersSizeChecker;
    }

    /**
     * Initializes a new command processor with mappers defined in mapping mappingContext.
     * The dynamic access is needed to instantiate message mappers for an actor system.
     *
     * @param connectionId the connection that the processor works for.
     * @param mappingDefinition the configured mappings used by this processor
     * @param actorSystem the dynamic access used for message mapper instantiation.
     * @param connectivityConfig the configuration settings of the Connectivity service.
     * @param protocolAdapter the ProtocolAdapter to be used.
     * @param log the log adapter.
     * @return the processor instance.
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException if the configuration of
     * one of the {@code mappingContext} is invalid.
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationFailedException if the configuration of
     * one of the {@code mappingContext} failed for a mapper specific reason.
     */
    public static InboundMappingProcessor of(final ConnectionId connectionId,
            final PayloadMappingDefinition mappingDefinition,
            final ActorSystem actorSystem,
            final ConnectivityConfig connectivityConfig,
            final ProtocolAdapter protocolAdapter,
            final DittoDiagnosticLoggingAdapter log) {

        final MessageMapperFactory messageMapperFactory =
                DefaultMessageMapperFactory.of(connectionId, actorSystem, connectivityConfig.getMappingConfig(), log);
        final MessageMapperRegistry registry =
                messageMapperFactory.registryOf(DittoMessageMapper.CONTEXT, mappingDefinition);

        final LimitsConfig limitsConfig = connectivityConfig.getLimitsConfig();
        final DittoHeadersSizeChecker dittoHeadersSizeChecker =
                DittoHeadersSizeChecker.of(limitsConfig.getHeadersMaxSize(), limitsConfig.getAuthSubjectsMaxCount());

        return new InboundMappingProcessor(connectionId, registry, log, protocolAdapter, dittoHeadersSizeChecker);
    }

    /**
     * Processes an {@link org.eclipse.ditto.services.models.connectivity.ExternalMessage} which may result in 0..n messages/errors.
     *
     * @param message the inbound {@link org.eclipse.ditto.services.models.connectivity.ExternalMessage} to be processed
     * @param resultHandler handles the 0..n results of the mapping(s).
     * @param <R> type of results.
     * @return combined results of all message mappers.
     */
    @Override
    <R> R process(final ExternalMessage message,
            final MappingResultHandler<MappedInboundExternalMessage, R> resultHandler) {

        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(logger,
                message.getHeaders().get(CORRELATION_ID.getKey()), connectionId);
        final List<MessageMapper> mappers = getMappers(message.getPayloadMapping().orElse(null));
        logger.debug("Mappers resolved for message: {}", mappers);
        R result = resultHandler.emptyResult();
        for (final MessageMapper mapper : mappers) {
            final MappingTimer mappingTimer = MappingTimer.inbound(connectionId);
            final R mappingResult =
                    mappingTimer.overall(() -> convertInboundMessage(mapper, message, mappingTimer, resultHandler));
            result = resultHandler.combineResults(result, mappingResult);
        }
        return result;
    }

    private <R> R convertInboundMessage(final MessageMapper mapper,
            final ExternalMessage message,
            final MappingTimer timer,
            final MappingResultHandler<MappedInboundExternalMessage, R> handler) {

        checkNotNull(message, "message");
        R result = handler.emptyResult();
        try {

            if (shouldMapMessageByContentType(message, mapper) && shouldMapMessageByConditions(message, mapper)) {
                logger.withCorrelationId(message.getInternalHeaders())
                        .debug("Mapping message using mapper {}.", mapper.getId());
                final List<Adaptable> adaptables = timer.payload(mapper.getId(), () -> mapper.map(message));
                if (isNullOrEmpty(adaptables)) {
                    return handler.onMessageDropped();
                } else {
                    for (final Adaptable adaptable : adaptables) {
                        enhanceLogFromAdaptable(adaptable);
                        handler.onTopicPathResolved(adaptable.getTopicPath());
                        final Signal<?> signal = timer.protocol(() -> protocolAdapter.fromAdaptable(adaptable));
                        dittoHeadersSizeChecker.check(signal.getDittoHeaders());
                        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
                        final DittoHeaders headersWithMapper =
                                dittoHeaders.toBuilder().inboundPayloadMapper(mapper.getId()).build();
                        final Signal<?> signalWithMapperHeader = signal.setDittoHeaders(headersWithMapper);
                        final MappedInboundExternalMessage mappedMessage =
                                MappedInboundExternalMessage.of(message, adaptable.getTopicPath(),
                                        signalWithMapperHeader);
                        result = handler.combineResults(result, handler.onMessageMapped(mappedMessage));
                    }
                }
            } else {
                result = handler.onMessageDropped();
                logger.withCorrelationId(message.getInternalHeaders())
                        .debug("Not mapping message with mapper <{}> as content-type <{}> was " +
                                        "blocked or MessageMapper conditions {} were not matched.",
                                mapper.getId(), message.findContentType(), mapper.getIncomingConditions());
            }
        } catch (final DittoRuntimeException e) {
            // combining error result with any previously successfully mapped result
            result = handler.combineResults(result, handler.onException(e));
        } catch (final Exception e) {
            final MessageMappingFailedException mappingFailedException = buildMappingFailedException("inbound",
                    message.findContentType().orElse(""), mapper.getId(), DittoHeaders.of(message.getHeaders()), e);
            // combining error result with any previously successfully mapped result
            result = handler.combineResults(result, handler.onException(mappingFailedException));
        }
        return result;
    }

    private static boolean shouldMapMessageByContentType(final ExternalMessage message, final MessageMapper mapper) {
        return message.findContentType()
                .map(filterByContentTypeBlocklist(mapper))
                .orElse(true);
    }

    private boolean shouldMapMessageByConditions(final ExternalMessage message, final MessageMapper mapper) {
        return resolveConditions(mapper.getIncomingConditions().values(),
                Resolvers.forExternalMessage(message, connectionId));
    }

    private static Function<String, Boolean> filterByContentTypeBlocklist(final MessageMapper mapper) {
        return contentType -> !mapper.getContentTypeBlocklist().contains(contentType);
    }

    private static boolean isNullOrEmpty(@Nullable final Collection<?> messages) {
        return messages == null || messages.isEmpty();
    }

}
