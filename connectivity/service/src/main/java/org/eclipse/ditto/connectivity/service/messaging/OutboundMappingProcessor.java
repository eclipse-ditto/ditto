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
package org.eclipse.ditto.connectivity.service.messaging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSelection;
import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperRegistry;
import org.eclipse.ditto.connectivity.service.messaging.mappingoutcome.MappingOutcome;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.protocol.AdaptablePartialAccessFilter;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;

/**
 * Processes outgoing {@link Signal}s to {@link ExternalMessage}s.
 * Encapsulates the message processing logic from the message mapping processor actor.
 */
public final class OutboundMappingProcessor extends AbstractMappingProcessor<OutboundSignal, OutboundSignal.Mapped> {

    private static final HeaderTranslator HEADER_TRANSLATOR = DittoProtocolAdapter.getHeaderTranslator();

    private final ProtocolAdapter protocolAdapter;
    private final Set<AcknowledgementLabel> sourceDeclaredAcks;
    private final Set<AcknowledgementLabel> targetIssuedAcks;
    @Nullable private final ResponseDiversionInterceptor responseDiversionInterceptor;

    private OutboundMappingProcessor(final ConnectionId connectionId,
            final ConnectionType connectionType,
            final MessageMapperRegistry registry,
            final ThreadSafeDittoLoggingAdapter logger,
            final ProtocolAdapter protocolAdapter,
            final Set<AcknowledgementLabel> sourceDeclaredAcks,
            final Set<AcknowledgementLabel> targetIssuedAcks,
            @Nullable final ResponseDiversionInterceptor responseDiversionInterceptor) {

        super(registry, logger, connectionId, connectionType);
        this.protocolAdapter = protocolAdapter;
        this.sourceDeclaredAcks = sourceDeclaredAcks;
        this.targetIssuedAcks = targetIssuedAcks;
        this.responseDiversionInterceptor = responseDiversionInterceptor;
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
     * @param diversionInterceptor the response diversion interceptor to be used or null if not configured.
     * @return the processor instance.
     * @throws org.eclipse.ditto.connectivity.model.MessageMapperConfigurationInvalidException if the configuration of
     * one of the {@code mappingContext} is invalid.
     * @throws org.eclipse.ditto.connectivity.model.MessageMapperConfigurationFailedException if the configuration of
     * one of the {@code mappingContext} failed for a mapper specific reason.
     */
    public static OutboundMappingProcessor of(final Connection connection,
            final ConnectivityConfig connectivityConfig,
            final ActorSystem actorSystem,
            final ProtocolAdapter protocolAdapter,
            final ThreadSafeDittoLoggingAdapter logger,
            @Nullable final ResponseDiversionInterceptor diversionInterceptor) {

        final ActorSelection deadLetterSelection = actorSystem.actorSelection(actorSystem.deadLetters().path());
        return of(OutboundMappingSettings.of(connection, connectivityConfig, actorSystem, deadLetterSelection,
                protocolAdapter, logger), diversionInterceptor);
    }

    /**
     * Initializes a new command processor with response diversion support.
     *
     * @param connection the connection that the processor works for.
     * @param connectivityConfig the connectivity config related to the given connection.
     * @param actorSystem the dynamic access used for message mapper instantiation.
     * @param protocolAdapter the ProtocolAdapter to be used.
     * @param logger the logging adapter to be used for log statements.
     * @return the processor instance.
     */
    public static OutboundMappingProcessor of(final Connection connection,
            final ConnectivityConfig connectivityConfig,
            final ActorSystem actorSystem,
            final ProtocolAdapter protocolAdapter,
            final ThreadSafeDittoLoggingAdapter logger) {
        final ActorSelection deadLetterSelection = actorSystem.actorSelection(actorSystem.deadLetters().path());

        return of(OutboundMappingSettings.of(connection, connectivityConfig, actorSystem, deadLetterSelection,
                protocolAdapter, logger), null);
    }

    // DIVERSION: Add overloaded method with diversion interceptor
    private static OutboundMappingProcessor of(final OutboundMappingSettings settings,
            @Nullable final ResponseDiversionInterceptor diversionInterceptor) {
        return new OutboundMappingProcessor(settings.getConnectionId(), settings.getConnectionType(),
                settings.getRegistry(), settings.getLogger(), settings.getProtocolAdapter(),
                settings.getSourceDeclaredAcks(), settings.getTargetIssuedAcks(), diversionInterceptor);
    }

    boolean isSourceDeclaredAck(final AcknowledgementLabel label) {
        return sourceDeclaredAcks.contains(label);
    }

    boolean isTargetIssuedAck(final AcknowledgementLabel label) {
        return targetIssuedAcks.contains(label);
    }

    boolean isSourceDeclaredOrTargetIssuedAck(final AcknowledgementLabel label) {
        return isSourceDeclaredAck(label) || isTargetIssuedAck(label);
    }

    /**
     * Processes an {@link OutboundSignal} to 0..n {@link OutboundSignal.Mapped} signals or errors.
     *
     * @param outboundSignal the outboundSignal to be processed.
     * @return the list of mapping outcomes.
     */
    @Override
    List<MappingOutcome<OutboundSignal.Mapped>> process(final OutboundSignal outboundSignal) {
        // DIVERSION FLOW:
        //  1. Check if diversion is enabled
        //  2. Check if response is already diverted
        //      a. If yes, check if authorized
        //          i. If authorized, continue normal flow
        //          ii. If not authorized, drop response (return empty list)
        //      b. If no, check if response is for diversion
        //          i. If yes, divert response
        //              - If diversion was successful and normal response should not be preserved, return empty list
        //              - If diversion was successful and normal response should be preserved, continue normal flow
        //          ii. If no, continue normal flow
        //  3. If diversion is not enabled, continue normal flow

        if (responseDiversionInterceptor != null) {
            if (responseDiversionInterceptor.isAlreadyDiverted(outboundSignal)) {
                 if (responseDiversionInterceptor.isAuthorized(outboundSignal)) {
                     logger.withCorrelationId(outboundSignal.getSource())
                             .debug("Response was already diverted and is authorized. Continue normal flow. {}",
                                     outboundSignal.getSource().getDittoHeaders());
                 } else {
                        logger.withCorrelationId(outboundSignal.getSource())
                                .warning("Response was diverted but not authorized, dropping: {}",
                                        outboundSignal.getSource().getDittoHeaders());
                        return Collections.emptyList();
                 }
            } else {
                if (responseDiversionInterceptor.isForDiversion(outboundSignal)) {
                    logger.withCorrelationId(outboundSignal.getSource())
                            .debug("Response will be diverted: {}", outboundSignal);
                    final boolean wasDiverted = responseDiversionInterceptor.interceptAndDivert(outboundSignal);
                    if (wasDiverted) {
                        if (!responseDiversionInterceptor.shouldPreserveNormalResponseViaSource()){
                            logger.withCorrelationId(outboundSignal.getSource())
                                    .debug("Response diverted, returning empty mapping outcomes. {}", outboundSignal);
                            return Collections.emptyList();
                        } else {
                            logger.withCorrelationId(outboundSignal.getSource())
                                    .debug("Response diverted, continuing normal processing to source reply target. {}", outboundSignal);
                        }
                    }
                }
            }
        }  else {
            logger.withCorrelationId(outboundSignal.getSource()).debug("Response diversion not enabled: {}", outboundSignal);
        }
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
                    .toList();
        }
        return processMappableSignals(outboundSignal, mappableSignals);
    }

    private List<MappingOutcome<OutboundSignal.Mapped>> processMappableSignals(
            final OutboundSignal outboundSignal,
            final List<OutboundSignal.Mappable> mappableSignals
    ) {
        final var outboundSignalSource = outboundSignal.getSource();
        final var dittoHeaders = outboundSignalSource.getDittoHeaders();

        final var mappingTimer = MappingTimer.outbound(connectionId, connectionType, dittoHeaders);

        return mappingTimer.overall(() -> mappableSignals.stream()
                .flatMap(mappableSignal -> {
                    final Signal<?> source = mappableSignal.getSource();
                    final List<Target> targets = mappableSignal.getTargets();
                    final List<MessageMapper> mappers = getMappers(mappableSignal.getPayloadMapping());
                    logger.withCorrelationId(source)
                            .debug("Resolved mappers for message {} to targets {}: {}", source, targets, mappers);
                    
                    final boolean hasPartialAccessPaths = dittoHeaders.containsKey(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey());
                    
                    if (hasPartialAccessPaths && !targets.isEmpty()) {
                        return processWithPartialAccessPaths(mappableSignal, source, targets, outboundSignal, mappingTimer, mappers);
                    } else {
                        return processWithoutPartialAccessPaths(mappableSignal, source, outboundSignal, mappingTimer, mappers);
                    }
                })
                .toList());
    }

    private Stream<MappingOutcome<OutboundSignal.Mapped>> processWithoutPartialAccessPaths(
            final OutboundSignal.Mappable mappableSignal,
            final Signal<?> source,
            final OutboundSignal outboundSignal,
            final MappingTimer mappingTimer,
            final List<MessageMapper> mappers) {
        final List<Target> targets = mappableSignal.getTargets();
        if (targets.isEmpty()) {
            final Adaptable baseAdaptable = createBaseAdaptable(source, null, outboundSignal, mappingTimer);
            return mappers.stream()
                    .flatMap(mapper -> runMapper(mappableSignal, baseAdaptable, mapper, mappingTimer));
        }
        
        final boolean hasTargetIssuedAcks = targets.stream()
                .anyMatch(target -> target.getIssuedAcknowledgementLabel().isPresent());
        
        if (!hasTargetIssuedAcks) {
            final Adaptable baseAdaptable = createBaseAdaptable(source, null, outboundSignal, mappingTimer);
            return mappers.stream()
                    .flatMap(mapper -> runMapper(mappableSignal, baseAdaptable, mapper, mappingTimer));
        }
        
        return targets.stream()
                .flatMap(target -> {
                    final AuthorizationContext targetAuthContext = target.getAuthorizationContext();
                    final Adaptable baseAdaptable = createBaseAdaptable(source, targetAuthContext, outboundSignal, mappingTimer);
                    final Adaptable adaptableWithoutTargetIssuedAcks = filterTargetIssuedAcknowledgements(baseAdaptable, target);
                    final Adaptable adaptableWithExternalHeaders = convertToExternalHeaders(adaptableWithoutTargetIssuedAcks);
                    final OutboundSignal.Mappable targetMappableSignal =
                            OutboundSignalFactory.newMappableOutboundSignal(source,
                                    List.of(target), mappableSignal.getPayloadMapping());
                    return mappers.stream()
                            .flatMap(mapper -> runMapper(targetMappableSignal, adaptableWithExternalHeaders, mapper, mappingTimer));
                });
    }

    private Stream<MappingOutcome<OutboundSignal.Mapped>> processWithPartialAccessPaths(
            final OutboundSignal.Mappable mappableSignal,
            final Signal<?> source,
            final List<Target> targets,
            final OutboundSignal outboundSignal,
            final MappingTimer mappingTimer,
            final List<MessageMapper> mappers) {
        final DittoHeaders dittoHeaders = source.getDittoHeaders();
        return targets.stream()
                .flatMap(target -> {
                    final AuthorizationContext targetAuthContext = target.getAuthorizationContext();
                    final Adaptable baseAdaptable = createBaseAdaptable(source, targetAuthContext, outboundSignal, mappingTimer);
                    
                    final Adaptable effective = AdaptablePartialAccessFilter.filterAdaptableForPartialAccess(
                            baseAdaptable, targetAuthContext);
                    
                    final boolean doFilter = shouldFilterForTarget(baseAdaptable, targetAuthContext, dittoHeaders);
                    
                    final TopicPath topicPath = effective.getTopicPath();
                    final boolean isThingEvent = TopicPath.Group.THINGS.equals(topicPath.getGroup()) &&
                            TopicPath.Criterion.EVENTS.equals(topicPath.getCriterion());
                    
                    if (isThingEvent && doFilter) {
                        final var valueOpt = effective.getPayload().getValue();
                        
                        final boolean emptyObject = valueOpt
                                .map(v -> v.isObject() && v.asObject().isEmpty())
                                .orElse(true);
                        
                        final boolean emptyPrimitive = valueOpt.isEmpty();
                        
                        if (emptyObject || emptyPrimitive) {
                            logger.withCorrelationId(source)
                                    .debug("Skipping event for target {} - filtered payload is empty (no access)",
                                            target.getAddress());
                            return Stream.empty();
                        }
                    }
                    
                    final Adaptable adaptableWithoutTargetIssuedAcks = filterTargetIssuedAcknowledgements(effective, target);
                    final Adaptable adaptableWithExternalHeaders = convertToExternalHeaders(adaptableWithoutTargetIssuedAcks);
                    final OutboundSignal.Mappable targetMappableSignal =
                            OutboundSignalFactory.newMappableOutboundSignal(source,
                                    List.of(target), mappableSignal.getPayloadMapping());
                    return mappers.stream()
                            .flatMap(mapper -> runMapper(targetMappableSignal, adaptableWithExternalHeaders, mapper, mappingTimer));
                });
    }
    
    /**
     * Determines if partial-access filtering should be applied for a target.
     * Filtering is applied when:
     * - Signal is a thing-related signal (THINGS group)
     * - Signal is an event or message (not commands)
     * - Either PARTIAL_ACCESS_PATHS header is present OR target has non-empty AuthorizationContext
     *
     * @param adaptable the adaptable to check
     * @param targetAuthContext the authorization context of the target (may be null)
     * @param rootHeaders the root signal headers (may contain PARTIAL_ACCESS_PATHS)
     * @return true if filtering should be applied
     */
    private static boolean shouldFilterForTarget(final Adaptable adaptable,
                                                 @Nullable final AuthorizationContext targetAuthContext,
                                                 final DittoHeaders rootHeaders) {
        final TopicPath topicPath = adaptable.getTopicPath();
        if (!TopicPath.Group.THINGS.equals(topicPath.getGroup())) {
            return false;
        }
        
        final TopicPath.Criterion criterion = topicPath.getCriterion();
        if (!(TopicPath.Criterion.EVENTS.equals(criterion) || TopicPath.Criterion.MESSAGES.equals(criterion))) {
            return false;
        }
        
        if (targetAuthContext != null && !targetAuthContext.isEmpty()) {
            return true;
        }
        
        return rootHeaders.containsKey(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey());
    }
    
    /**
     * Creates a base Adaptable with extra fields and correlation ID set.
     * This method centralizes the common logic for creating Adaptables to avoid duplication.
     */
    private Adaptable createBaseAdaptable(
            final Signal<?> source,
            @Nullable final AuthorizationContext targetAuthContext,
            final OutboundSignal outboundSignal,
            final MappingTimer mappingTimer) {
        final TopicPath.Channel channel = ProtocolAdapter.determineChannel(source);
        final Adaptable adaptableForTarget = mappingTimer.protocol(() ->
                protocolAdapter.toAdaptable(source, channel, targetAuthContext));
        final Adaptable adaptableWithExtra = outboundSignal.getExtra()
                .map(extra -> ProtocolFactory.setExtra(adaptableForTarget, extra))
                .orElse(adaptableForTarget);
        return setInternalCorrelationIdToAdaptable(adaptableWithExtra, source);
    }

    private static Adaptable setInternalCorrelationIdToAdaptable(final Adaptable adaptable,
            final WithDittoHeaders internalSignal) {
        final var optionalCorrelationId = internalSignal.getDittoHeaders().getCorrelationId();
        final Adaptable result;
        result = optionalCorrelationId.map(s -> adaptable.setDittoHeaders(
                adaptable.getDittoHeaders().toBuilder().correlationId(s).build())).orElse(adaptable);
        return result;
    }

    /**
     * Filters out target-issued acknowledgement requests from the adaptable headers.
     * Only source-declared acknowledgements should be published to external targets.
     * This filtering only applies to EVENTS, not to MESSAGES (live commands).
     *
     * @param adaptable the adaptable to filter
     * @param target the target that may issue acknowledgements
     * @return the adaptable with target-issued acknowledgement requests removed (for events only)
     */
    private Adaptable filterTargetIssuedAcknowledgements(final Adaptable adaptable, final Target target) {
        final TopicPath topicPath = adaptable.getTopicPath();
        if (!TopicPath.Criterion.EVENTS.equals(topicPath.getCriterion())) {
            return adaptable;
        }
        
        final DittoHeaders headers = adaptable.getDittoHeaders();
        final Set<AcknowledgementRequest> ackRequests = headers.getAcknowledgementRequests();
        if (ackRequests.isEmpty()) {
            return adaptable;
        }
        
        final Optional<AcknowledgementLabel> targetIssuedAckLabel = target.getIssuedAcknowledgementLabel();
        if (targetIssuedAckLabel.isEmpty()) {
            return adaptable;
        }
        
        final AcknowledgementLabel targetIssuedLabel = targetIssuedAckLabel.get();
        final Set<AcknowledgementRequest> filteredAckRequests = ackRequests.stream()
                .filter(ackRequest -> {
                    final AcknowledgementLabel label = ackRequest.getLabel();
                    if (label.equals(targetIssuedLabel)) {
                        return false;
                    }
                    if (isTargetIssuedAck(label)) {
                        return false;
                    }
                    return isSourceDeclaredAck(label);
                })
                .collect(Collectors.toSet());
        
        if (filteredAckRequests.size() == ackRequests.size()) {
            return adaptable;
        }
        
        final DittoHeaders filteredHeaders = headers.toBuilder()
                .acknowledgementRequests(filteredAckRequests)
                .build();
        
        return adaptable.setDittoHeaders(filteredHeaders);
    }

    /**
     * Converts internal headers in the Adaptable to external headers.
     * This removes internal headers like PARTIAL_ACCESS_PATHS before mapping to external messages.
     *
     * @param adaptable the Adaptable with internal headers
     * @return the Adaptable with external headers only
     */
    private Adaptable convertToExternalHeaders(final Adaptable adaptable) {
        final java.util.Map<String, String> externalHeaders = HEADER_TRANSLATOR.toExternalHeaders(
                adaptable.getDittoHeaders());
        return adaptable.setDittoHeaders(DittoHeaders.of(externalHeaders));
    }

    private Stream<MappingOutcome<OutboundSignal.Mapped>> runMapper(final OutboundSignal.Mappable outboundSignal,
            final Adaptable adaptable,
            final MessageMapper mapper,
            final MappingTimer timer) {

        try {
            if (shouldMapMessageByConditions(outboundSignal, mapper)) {
                logger.withCorrelationId(adaptable)
                        .debug("Applying mapper <{}> to message <{}>", mapper.getId(), adaptable);

                final List<ExternalMessage> messages =
                        timer.outboundPayload(mapper.getId(), outboundSignal, () -> checkForNull(mapper.map(adaptable)));

                logger.withCorrelationId(adaptable)
                        .debug("Mapping <{}> produced <{}> messages.", mapper.getId(), messages.size());

                if (messages.isEmpty()) {
                    return Stream.of(MappingOutcome.dropped(mapper.getId(), null));
                } else {
                    return messages.stream()
                            .map(em -> {
                                final ExternalMessage externalMessage =
                                        ExternalMessageFactory.newExternalMessageBuilder(em)
                                                .withTopicPath(adaptable.getTopicPath())
                                                .withInternalHeaders(outboundSignal.getSource().getDittoHeaders())
                                                .build();
                                final OutboundSignal.Mapped mapped =
                                        OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable,
                                                externalMessage);
                                return MappingOutcome.mapped(mapper.getId(), mapped, adaptable.getTopicPath(), null);
                            });
                }
            } else {
                logger.withCorrelationId(adaptable)
                        .debug("Not mapping message with mapper <{}> as outgoing conditions {} were not matched.",
                                mapper.getId(), mapper.getOutgoingConditions());
                return Stream.of(MappingOutcome.dropped(mapper.getId(), null));
            }
        } catch (final Exception e) {
            return Stream.of(
                    MappingOutcome.error(mapper.getId(), toDittoRuntimeException(e, mapper, adaptable),
                            adaptable.getTopicPath(), null)
            );
        }
    }

    private static DittoRuntimeException toDittoRuntimeException(final Throwable error, final MessageMapper mapper,
            final Adaptable adaptable) {
        return DittoRuntimeException.asDittoRuntimeException(error, e -> {
            final DittoHeaders headers = adaptable.getDittoHeaders();
            final String contentType = headers.getOrDefault(ExternalMessage.CONTENT_TYPE_HEADER, "");
            return buildMappingFailedException("outbound", contentType, mapper.getId(), headers, e);
        });
    }

    private boolean shouldMapMessageByConditions(final OutboundSignal.Mappable mappable,
            final MessageMapper mapper) {
        return resolveConditions(mapper.getOutgoingConditions().values(),
                Resolvers.forOutboundSignal(mappable, connectionId));
    }

    private static <T> List<T> checkForNull(@Nullable final List<T> messages) {
        return messages == null ? List.of() : messages;
    }

}
