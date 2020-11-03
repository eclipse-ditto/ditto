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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeadersSizeChecker;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.PayloadMappingDefinition;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.connectivity.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMessageMapperFactory;
import org.eclipse.ditto.services.connectivity.mapping.DittoMessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperFactory;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperRegistry;
import org.eclipse.ditto.services.connectivity.messaging.mappingoutcome.MappingOutcome;
import org.eclipse.ditto.services.connectivity.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.MappedInboundExternalMessage;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.ActorSystem;

/**
 * Processes incoming {@link ExternalMessage}s to {@link Signal}s.
 * Encapsulates the message processing logic from the inbound message mapping processor actor.
 */
public final class InboundMappingProcessor
        extends AbstractMappingProcessor<ExternalMessage, MappedInboundExternalMessage> {

    private final ProtocolAdapter protocolAdapter;
    private final DittoHeadersSizeChecker dittoHeadersSizeChecker;

    private InboundMappingProcessor(final ConnectionId connectionId,
            final ConnectionType connectionType,
            final MessageMapperRegistry registry,
            final ThreadSafeDittoLoggingAdapter logger,
            final ProtocolAdapter protocolAdapter,
            final DittoHeadersSizeChecker dittoHeadersSizeChecker) {

        super(registry, logger, connectionId, connectionType);
        this.protocolAdapter = protocolAdapter;
        this.dittoHeadersSizeChecker = dittoHeadersSizeChecker;
    }

    /**
     * Initializes a new command processor with mappers defined in mapping mappingContext.
     * The dynamic access is needed to instantiate message mappers for an actor system.
     *
     * @param connectionId the connection that the processor works for.
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
    public static InboundMappingProcessor of(final ConnectionId connectionId,
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

        final LimitsConfig limitsConfig = connectivityConfig.getLimitsConfig();
        final DittoHeadersSizeChecker dittoHeadersSizeChecker =
                DittoHeadersSizeChecker.of(limitsConfig.getHeadersMaxSize(), limitsConfig.getAuthSubjectsMaxCount());

        return new InboundMappingProcessor(connectionId, connectionType, registry, loggerWithConnectionId,
                protocolAdapter, dittoHeadersSizeChecker);
    }

    /**
     * Processes an {@link ExternalMessage} which may result in 0..n messages/errors.
     *
     * @param message the inbound {@link ExternalMessage} to be processed
     * @return combined results of all message mappers.
     */
    @Override
    List<MappingOutcome<MappedInboundExternalMessage>> process(final ExternalMessage message) {
        final List<MessageMapper> mappers = getMappers(message.getPayloadMapping().orElse(null));
        logger.withCorrelationId(message.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey()))
                .debug("Mappers resolved for message: {}", mappers);
        final MappingTimer mappingTimer = MappingTimer.inbound(connectionId, connectionType);
        return mappingTimer.overall(() -> mappers.stream()
                .flatMap(mapper -> runMapper(mapper, message, mappingTimer))
                .collect(Collectors.toList())
        );
    }


    private Stream<MappingOutcome<MappedInboundExternalMessage>> runMapper(final MessageMapper mapper,
            final ExternalMessage message,
            final MappingTimer timer) {

        checkNotNull(message, "message");
        try {
            if (shouldMapMessageByContentType(message, mapper) && shouldMapMessageByConditions(message, mapper)) {
                logger.withCorrelationId(message.getInternalHeaders())
                        .debug("Mapping message using mapper {}.", mapper.getId());
                final List<Adaptable> adaptables = timer.payload(mapper.getId(), () -> mapper.map(message));

                if (isNullOrEmpty(adaptables)) {
                    return Stream.of(MappingOutcome.dropped(mapper.getId(), message));
                } else {
                    final List<MappedInboundExternalMessage> mappedMessages = new ArrayList<>(adaptables.size());
                    for (final Adaptable adaptable : adaptables) {
                        try {
                            final Signal<?> signal = timer.protocol(() -> protocolAdapter.fromAdaptable(adaptable));
                            dittoHeadersSizeChecker.check(signal.getDittoHeaders());
                            final DittoHeaders dittoHeaders = signal.getDittoHeaders();
                            final DittoHeaders headersWithMapper =
                                    dittoHeaders.toBuilder().inboundPayloadMapper(mapper.getId()).build();
                            final Signal<?> signalWithMapperHeader = signal.setDittoHeaders(headersWithMapper);
                            final MappedInboundExternalMessage mappedMessage =
                                    MappedInboundExternalMessage.of(message, adaptable.getTopicPath(),
                                            signalWithMapperHeader);
                            mappedMessages.add(mappedMessage);
                        } catch (final Exception e) {
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
                    headersBuilder.putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(), value);
                }
            } catch (final Exception e) {
                // ignore this single invalid header
                logger.info("Putting a (protocol) header resulted in an exception: {} - {}",
                        e.getClass().getSimpleName(), e.getMessage());
            }
        });
        message.getTextPayload()
                .map(JsonFactory::readFrom)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .flatMap(obj -> obj.getValue(JsonifiableAdaptable.JsonFields.HEADERS))
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .ifPresent(obj -> obj.forEach(field -> {
                    try {
                        final JsonValue value = field.getValue();
                        headersBuilder.putHeader(field.getKey(),
                                value.isString() ? value.asString() : value.toString());
                    } catch (final Exception e) {
                        // ignore this single invalid header
                        logger.info("Putting a (payload) header resulted in an exception: {} - {}",
                                e.getClass().getSimpleName(), e.getMessage());
                    }
                }));
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

        if (error instanceof WithDittoHeaders) {
            final DittoHeaders existingHeaders = ((WithDittoHeaders<?>) error).getDittoHeaders();
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
