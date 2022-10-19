/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.MappedInboundExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.mapping.DefaultMessageMapperFactory;
import org.eclipse.ditto.connectivity.service.mapping.DittoMessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperFactory;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperRegistry;
import org.eclipse.ditto.connectivity.service.messaging.mappingoutcome.MappingOutcome;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.edge.service.headers.DittoHeadersValidator;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.things.model.ThingConstants;

import akka.actor.ActorSystem;

/**
 * Processes incoming {@link ExternalMessage}s to {@link Signal}s.
 * Encapsulates the message processing logic from the inbound message mapping processor actor.
 */
public final class InboundMappingProcessor
        extends AbstractMappingProcessor<ExternalMessage, MappedInboundExternalMessage> {

    private final ProtocolAdapter protocolAdapter;
    private final DittoHeadersValidator dittoHeadersSizeValidator;

    private InboundMappingProcessor(final ConnectionId connectionId,
            final ConnectionType connectionType,
            final MessageMapperRegistry registry,
            final ThreadSafeDittoLoggingAdapter logger,
            final ProtocolAdapter protocolAdapter,
            final DittoHeadersValidator dittoHeadersValidator) {

        super(registry, logger, connectionId, connectionType);
        this.protocolAdapter = protocolAdapter;
        this.dittoHeadersSizeValidator = dittoHeadersValidator;
    }

    /**
     * Initializes a new command processor with mappers defined in mapping mappingContext.
     * The dynamic access is needed to instantiate message mappers for an actor system.
     *
     * @param connection the connection that the processor works for.
     * @param connectivityConfig the connectivity config related to the given connection.
     * @param actorSystem the dynamic access used for message mapper instantiation.
     * @param protocolAdapter the ProtocolAdapter to be used.
     * @param logger the logging adapter to be used for log statements.
     * @return the processor instance.
     * @throws org.eclipse.ditto.connectivity.model.MessageMapperConfigurationInvalidException if the configuration of
     * one of the {@code mappingContext} is invalid.
     * @throws org.eclipse.ditto.connectivity.model.MessageMapperConfigurationFailedException if the configuration of
     * one of the {@code mappingContext} failed for a mapper specific reason.
     */
    public static InboundMappingProcessor of(final Connection connection,
            final ConnectivityConfig connectivityConfig,
            final ActorSystem actorSystem,
            final ProtocolAdapter protocolAdapter,
            final ThreadSafeDittoLoggingAdapter logger) {

        final var mappingDefinition = connection.getPayloadMappingDefinition();
        final ThreadSafeDittoLoggingAdapter loggerWithConnectionId =
                logger.withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connection.getId());

        final MessageMapperFactory messageMapperFactory =
                DefaultMessageMapperFactory.of(connection, connectivityConfig, actorSystem, loggerWithConnectionId);
        final MessageMapperRegistry registry =
                messageMapperFactory.registryOf(DittoMessageMapper.CONTEXT, mappingDefinition);

        final var dittoHeadersValidator = DittoHeadersValidator.get(actorSystem,
                ScopedConfig.dittoExtension(actorSystem.settings().config()));

        return of(connection, registry, loggerWithConnectionId, protocolAdapter, dittoHeadersValidator);
    }

    static InboundMappingProcessor of(final Connection connection,
            final MessageMapperRegistry registry, final ThreadSafeDittoLoggingAdapter logger,
            final ProtocolAdapter adapter, final DittoHeadersValidator dittoHeadersValidator) {
        final var connectionId = connection.getId();
        final var connectionType = connection.getConnectionType();
        return new InboundMappingProcessor(connectionId,
                connectionType,
                registry,
                logger,
                adapter,
                dittoHeadersValidator);
    }

    /**
     * Processes an {@link ExternalMessage} which may result in 0..n messages/errors.
     *
     * @param message the inbound {@link ExternalMessage} to be processed
     * @return combined results of all message mappers.
     */
    @Override
    List<MappingOutcome<MappedInboundExternalMessage>> process(final ExternalMessage message) {
        final var mappers = getMappers(message.getPayloadMapping().orElse(null));
        logger.withCorrelationId(message.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey()))
                .debug("Mappers resolved for message: {}", mappers);
        final var mappingTimer = MappingTimer.inbound(connectionId, connectionType, message.getHeaders());
        return mappingTimer.overall(() -> mappers.stream()
                .flatMap(mapper -> {
                            final var mappingTimerSpan = mappingTimer.getSpan();
                            return runMapper(
                                    mapper,
                                    message.withHeaders(mappingTimerSpan.propagateContext(message.getHeaders())),
                                    mappingTimer
                            );
                        }
                )
                .toList()
        );
    }

    private Stream<MappingOutcome<MappedInboundExternalMessage>> runMapper(final MessageMapper mapper,
            final ExternalMessage message, final MappingTimer timer) {

        checkNotNull(message, "message");
        try {
            if (shouldMapMessageByContentType(message, mapper) && shouldMapMessageByConditions(message, mapper)) {
                logger.withCorrelationId(message.getInternalHeaders())
                        .debug("Mapping message using mapper {}.", mapper.getId());
                final List<Adaptable> adaptables = timer.inboundPayload(mapper.getId(), () -> mapper.map(message));

                if (isNullOrEmpty(adaptables)) {
                    return Stream.of(MappingOutcome.dropped(mapper.getId(), message));
                } else {
                    final DittoHeaders additionalInboundHeaders = mapper.getAdditionalInboundHeaders(message);
                    final List<MappedInboundExternalMessage> mappedMessages = new ArrayList<>(adaptables.size());
                    for (final Adaptable adaptable : adaptables) {
                        try {
                            final Signal<?> signal =
                                    timer.inboundProtocol(() -> protocolAdapter.fromAdaptable(adaptable));
                            final DittoHeaders headersWithMapper = signal.getDittoHeaders().toBuilder()
                                    .inboundPayloadMapper(mapper.getId())
                                    .putHeaders(additionalInboundHeaders)
                                    .build();
                            dittoHeadersSizeValidator.validate(headersWithMapper).toCompletableFuture().join();
                            final Signal<?> signalWithMapperHeader = signal.setDittoHeaders(headersWithMapper);
                            final MappedInboundExternalMessage mappedMessage =
                                    MappedInboundExternalMessage.of(message, adaptable.getTopicPath(),
                                            signalWithMapperHeader);
                            mappedMessages.add(mappedMessage);
                        } catch (final Exception e) {
                            logger.withCorrelationId(e instanceof WithDittoHeaders wdh ? wdh.getDittoHeaders() :
                                            adaptable.getDittoHeaders())
                                    .info("Exception during inbound adaptable conversion to Signal: <{}: {}>",
                                            e.getClass().getSimpleName(), e.getMessage());
                            return Stream.of(MappingOutcome.error(mapper.getId(),
                                    toDittoRuntimeException(e, mapper, adaptable.getDittoHeaders(), message),
                                    adaptable.getTopicPath(),
                                    message
                            ));
                        }
                    }
                    return mappedMessages.stream()
                            .map(mapped -> MappingOutcome.mapped(mapper.getId(), mapped, mapped.getTopicPath(),
                                    message));
                }
            } else {
                logger.withCorrelationId(message.getInternalHeaders())
                        .debug("Not mapping message with mapper <{}> as content-type <{}> was " +
                                        "blocked or MessageMapper conditions {} were not matched.",
                                mapper.getId(), message.findContentType(), mapper.getIncomingConditions());
                return Stream.of(MappingOutcome.dropped(mapper.getId(), message));
            }
        } catch (final Exception e) {
            return Stream.of(MappingOutcome.error(mapper.getId(), toDittoRuntimeException(e, mapper,
                    resolveDittoHeadersBestEffort(message), message), null, message));
        }
    }

    private DittoHeaders resolveDittoHeadersBestEffort(final ExternalMessage message) {
        final DittoHeadersBuilder<?, ?> headersBuilder = DittoHeaders.newBuilder();
        message.getHeaders().forEach((key, value) -> {
            try {
                headersBuilder.putHeader(key, value);
                if (key.equals("device_id")) {
                    // this is kind of a workaround to preserve "best effort" an entityId by a special header value
                    // whenever the device connectivity layer sends the "entity id" in this header
                    headersBuilder.putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(),
                            ThingConstants.ENTITY_TYPE + ":" + value);
                }
            } catch (final Exception e) {
                // ignore this single invalid header
                logger.info("Putting a (protocol) header resulted in an exception: {} - {}",
                        e.getClass().getSimpleName(), e.getMessage());
            }
        });
        return headersBuilder.build();
    }

    private static DittoRuntimeException toDittoRuntimeException(final Throwable error,
            final MessageMapper mapper,
            final DittoHeaders bestEffortHeaders,
            final ExternalMessage message) {

        final DittoRuntimeException dittoRuntimeException = DittoRuntimeException.asDittoRuntimeException(error, e ->
                buildMappingFailedException("inbound",
                        message.findContentType().orElse(""),
                        mapper.getId(),
                        bestEffortHeaders,
                        e)
        );

        if (error instanceof WithDittoHeaders withDittoHeaders) {
            final DittoHeaders existingHeaders = withDittoHeaders.getDittoHeaders();
            final DittoHeaders mergedHeaders = bestEffortHeaders.toBuilder().putHeaders(existingHeaders)
                    .build();
            return dittoRuntimeException.setDittoHeaders(mergedHeaders);
        } else {
            return dittoRuntimeException.setDittoHeaders(bestEffortHeaders);
        }
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
