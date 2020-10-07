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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.PayloadMappingDefinition;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMessageMapperFactory;
import org.eclipse.ditto.services.connectivity.mapping.DittoMessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperFactory;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperRegistry;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.ActorSystem;

/**
 * Processes outgoing {@link Signal}s to {@link ExternalMessage}s.
 * Encapsulates the message processing logic from the message mapping processor actor.
 */
public final class OutboundMappingProcessor extends AbstractMappingProcessor<OutboundSignal, OutboundSignal.Mapped> {

    private final ProtocolAdapter protocolAdapter;

    private OutboundMappingProcessor(final ConnectionId connectionId,
            final ConnectionType connectionType,
            final MessageMapperRegistry registry,
            final ThreadSafeDittoLoggingAdapter logger,
            final ProtocolAdapter protocolAdapter) {

        super(registry, logger, connectionId, connectionType);
        this.protocolAdapter = protocolAdapter;
    }

    /**
     * Initializes a new command processor with mappers defined in mapping mappingContext.
     * The dynamic access is needed to instantiate message mappers for an actor system.
     *
     * @param connectionId the connection ID that the processor works for.
     * @param connectionType the type of the connection that the processor works for.
     * @param mappingDefinition the configured mappings used by this processor
     * @param actorSystem the dynamic access used for message mapper instantiation.
     * @param connectivityConfig the configuration settings of the Connectivity service.
     * @param protocolAdapter the ProtocolAdapter to be used.
     * @param logger the logging adapter to be used for log statements.
     * @return the processor instance.
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException if the configuration of
     * one of the {@code mappingContext} is invalid.
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationFailedException if the configuration of
     * one of the {@code mappingContext} failed for a mapper specific reason.
     */
    public static OutboundMappingProcessor of(final ConnectionId connectionId,
            final ConnectionType connectionType,
            final PayloadMappingDefinition mappingDefinition,
            final ActorSystem actorSystem,
            final ConnectivityConfig connectivityConfig,
            final ProtocolAdapter protocolAdapter,
            final ThreadSafeDittoLoggingAdapter logger) {

        final ThreadSafeDittoLoggingAdapter loggerWithConnectionId =
                logger.withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connectionId);

        final MessageMapperFactory messageMapperFactory =
                DefaultMessageMapperFactory.of(connectionId, actorSystem, connectivityConfig.getMappingConfig(),
                        loggerWithConnectionId);
        final MessageMapperRegistry registry =
                messageMapperFactory.registryOf(DittoMessageMapper.CONTEXT, mappingDefinition);

        return new OutboundMappingProcessor(connectionId, connectionType, registry, loggerWithConnectionId,
                protocolAdapter);
    }

    /**
     * Processes an {@link OutboundSignal} to 0..n {@link OutboundSignal.Mapped} signals and passes them to the given
     * {@link MappingResultHandler}.
     *
     * @param outboundSignal the outboundSignal to be processed.
     * @param resultHandler handles the 0..n results of the mapping(s).
     * @param <R> type of results.
     */
    @Override
    <R> R process(final OutboundSignal outboundSignal,
            final MappingResultHandler<OutboundSignal.Mapped, R> resultHandler) {

        final List<OutboundSignal.Mappable> mappableSignals;
        if (outboundSignal.getTargets().isEmpty()) {
            // responses/errors do not have a target assigned, read mapper used for inbound message from internal header
            final PayloadMapping payloadMapping = outboundSignal.getSource()
                    .getDittoHeaders()
                    .getInboundPayloadMapper()
                    .map(ConnectivityModelFactory::newPayloadMapping)
                    .orElseGet(ConnectivityModelFactory::emptyPayloadMapping); // fallback to default payload mapping
            final OutboundSignal.Mappable mappableSignal =
                    OutboundSignalFactory.newMappableOutboundSignal(outboundSignal.getSource(),
                            outboundSignal.getTargets(), payloadMapping);
            mappableSignals = Collections.singletonList(mappableSignal);
        } else {
            // group targets with exact same list of mappers together to avoid redundant mappings
            mappableSignals = outboundSignal.getTargets()
                    .stream()
                    .collect(Collectors.groupingBy(Target::getPayloadMapping, LinkedHashMap::new, Collectors.toList()))
                    .entrySet()
                    .stream()
                    .map(e -> OutboundSignalFactory.newMappableOutboundSignal(outboundSignal.getSource(), e.getValue(),
                            e.getKey()))
                    .collect(Collectors.toList());
        }
        return processMappableSignals(outboundSignal, mappableSignals, resultHandler);
    }

    private <R> R processMappableSignals(final OutboundSignal outboundSignal,
            final List<OutboundSignal.Mappable> mappableSignals,
            final MappingResultHandler<OutboundSignal.Mapped, R> resultHandler) {

        final MappingTimer timer = MappingTimer.outbound(connectionId, connectionType);
        final Adaptable adaptableWithoutExtra =
                timer.protocol(() -> protocolAdapter.toAdaptable(outboundSignal.getSource()));
        final Adaptable adaptable = outboundSignal.getExtra()
                .map(extra -> ProtocolFactory.setExtra(adaptableWithoutExtra, extra))
                .orElse(adaptableWithoutExtra);
        resultHandler.onTopicPathResolved(adaptable.getTopicPath());

        R result = resultHandler.emptyResult();
        for (final OutboundSignal.Mappable mappableSignal : mappableSignals) {
            final Signal<?> source = mappableSignal.getSource();
            final List<Target> targets = mappableSignal.getTargets();
            final List<MessageMapper> mappers = getMappers(mappableSignal.getPayloadMapping());
            logger.withCorrelationId(adaptable)
                    .debug("Resolved mappers for message {} to targets {}: {}", source, targets, mappers);
            // convert messages in the order of payload mapping and forward to result handler
            for (final MessageMapper mapper : mappers) {
                final R nextResult = convertOutboundMessage(mappableSignal, adaptable, mapper, timer, resultHandler);
                result = resultHandler.combineResults(result, nextResult);
            }
        }
        return result;
    }

    private <R> R convertOutboundMessage(final OutboundSignal.Mappable outboundSignal,
            final Adaptable adaptable,
            final MessageMapper mapper,
            final MappingTimer timer,
            final MappingResultHandler<OutboundSignal.Mapped, R> resultHandler) {

        R result = resultHandler.emptyResult();
        try {
            if (shouldMapMessageByConditions(outboundSignal, mapper)) {
                logger.withCorrelationId(adaptable)
                        .debug("Applying mapper <{}> to message <{}>", mapper.getId(), adaptable);

                final List<OutboundSignal.Mapped> messages = timer.payload(mapper.getId(),
                        () -> toStream(mapper.map(adaptable))
                                .map(em -> {
                                    final ExternalMessage externalMessage =
                                            ExternalMessageFactory.newExternalMessageBuilder(em)
                                                    .withTopicPath(adaptable.getTopicPath())
                                                    .withInternalHeaders(outboundSignal.getSource().getDittoHeaders())
                                                    .build();
                                    return OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable,
                                            externalMessage);
                                })
                                .collect(Collectors.toList()));

                logger.withCorrelationId(adaptable)
                        .debug("Mapping <{}> produced <{}> messages.", mapper.getId(), messages.size());

                if (messages.isEmpty()) {
                    result = resultHandler.combineResults(result, resultHandler.onMessageDropped());
                } else {
                    for (final OutboundSignal.Mapped message : messages) {
                        result = resultHandler.combineResults(result, resultHandler.onMessageMapped(message));
                    }
                }
            } else {
                result = resultHandler.onMessageDropped();
                logger.withCorrelationId(adaptable)
                        .debug("Not mapping message with mapper <{}> as MessageMapper conditions {} were not matched.",
                                mapper.getId(), mapper.getIncomingConditions());
            }
        } catch (final DittoRuntimeException e) {
            result = resultHandler.combineResults(result, resultHandler.onException(e));
        } catch (final Exception e) {
            final DittoHeaders headers = adaptable.getDittoHeaders();
            final String contentType = headers.getOrDefault(ExternalMessage.CONTENT_TYPE_HEADER, "");
            final MessageMappingFailedException mappingFailedException =
                    buildMappingFailedException("outbound", contentType, mapper.getId(), headers, e);
            result = resultHandler.combineResults(result, resultHandler.onException(mappingFailedException));
        }
        return result;
    }

    private boolean shouldMapMessageByConditions(final OutboundSignal.Mappable mappable,
            final MessageMapper mapper) {
        return resolveConditions(mapper.getOutgoingConditions().values(),
                Resolvers.forOutboundSignal(mappable, connectionId));
    }

    private static <T> Stream<T> toStream(@Nullable final Collection<T> messages) {
        return messages == null ? Stream.empty() : messages.stream();
    }

}
