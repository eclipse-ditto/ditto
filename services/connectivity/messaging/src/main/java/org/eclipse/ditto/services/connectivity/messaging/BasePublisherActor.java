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

import java.time.Instant;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
import org.eclipse.ditto.model.placeholders.ThingPlaceholder;
import org.eclipse.ditto.model.placeholders.TopicPathPlaceholder;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.MonitoringConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitorRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.DefaultConnectionMonitorRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLoggerRegistry;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageBuilder;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.signals.base.Signal;

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
    private static final TopicPathPlaceholder TOPIC_PLACEHOLDER = PlaceholderFactory.newTopicPathPlaceholder();

    protected final String connectionId;
    protected final List<Target> targets;
    protected final Map<Target, ResourceStatus> resourceStatusMap;

    protected final ConnectionLogger connectionLogger;
    protected final ConnectionMonitor responsePublishedMonitor;
    private final ConnectionMonitorRegistry<ConnectionMonitor> connectionMonitorRegistry;
    private final ConnectionMonitor responseDroppedMonitor;

    protected BasePublisherActor(final String connectionId, final List<Target> targets) {
        this.connectionId = checkNotNull(connectionId, "connectionId");
        this.targets = checkNotNull(targets, "targets");
        resourceStatusMap = new HashMap<>();
        final Instant now = Instant.now();
        targets.forEach(target ->
                resourceStatusMap.put(target,
                        ConnectivityModelFactory.newTargetStatus(getInstanceIdentifier(), ConnectivityStatus.OPEN,
                                target.getAddress(), "Started at " + now)));

        final MonitoringConfig monitoringConfig = DittoConnectivityConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        ).getMonitoringConfig();
        connectionMonitorRegistry = DefaultConnectionMonitorRegistry.fromConfig(monitoringConfig);
        responseDroppedMonitor = connectionMonitorRegistry.forResponseDropped(this.connectionId);
        responsePublishedMonitor = connectionMonitorRegistry.forResponsePublished(this.connectionId);
        connectionLogger =
                ConnectionLoggerRegistry.fromConfig(monitoringConfig.logger()).forConnection(this.connectionId);
    }

    private static String getInstanceIdentifier() {
        return InstanceIdentifierSupplier.getInstance().get();
    }

    @Override
    public Receive createReceive() {
        final ReceiveBuilder receiveBuilder = receiveBuilder();
        preEnhancement(receiveBuilder);

        receiveBuilder
                .match(OutboundSignal.WithExternalMessage.class, BasePublisherActor::isResponseOrError, outbound -> {
                    final ExternalMessage response = outbound.getExternalMessage();
                    final String correlationId = response.getHeaders().get(CORRELATION_ID.getKey());
                    ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log(), correlationId, connectionId);

                    final String replyToFromHeader = response.getHeaders().get(ExternalMessage.REPLY_TO_HEADER);
                    if (replyToFromHeader != null) {
                        final T replyTarget = toReplyTarget(replyToFromHeader);
                        log().info("Publishing mapped response/error message of type <{}> to reply target <{}>",
                                outbound.getSource().getType(), replyTarget);
                        log().debug("Publishing mapped response/error message of type <{}> to reply target <{}>: {}",
                                outbound.getSource().getType(), replyTarget, response);
                        publishMessage(null, replyTarget, response, responsePublishedMonitor);
                    } else {
                        log().info("Response dropped, missing replyTo address: {}", response);
                        responseDroppedMonitor.failure(outbound.getSource(),
                                "Response dropped since it was missing a replyTo address.");
                    }
                })
                .match(OutboundSignal.WithExternalMessage.class, outbound -> {
                    final ExternalMessage message = outbound.getExternalMessage();
                    final String correlationId = message.getHeaders().get(CORRELATION_ID.getKey());
                    ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log(), correlationId, connectionId);

                    final Signal<?> outboundSource = outbound.getSource();
                    log().debug("Publishing mapped message of type <{}> to targets <{}>: {}",
                            outboundSource.getType(), outbound.getTargets(), message);
                    outbound.getTargets().forEach(target -> {
                        log().info("Publishing mapped message of type <{}> to target address <{}>",
                                outboundSource.getType(), target.getAddress());

                        final ConnectionMonitor publishedMonitor =
                                connectionMonitorRegistry.forOutboundPublished(connectionId,
                                        target.getOriginalAddress());
                        try {
                            final T publishTarget = toPublishTarget(target.getAddress());
                            final ExternalMessage messageWithMappedHeaders =
                                    applyHeaderMapping(outbound, target, log());
                            publishMessage(target, publishTarget, messageWithMappedHeaders, publishedMonitor);
                        } catch (final DittoRuntimeException e) {
                            // TODO: might there be private information in the exception message so we shouldn't be allowed to see them?
                            publishedMonitor.failure(outboundSource,
                                    "Ran into a failure when applying header mapping: {0}",
                                    e.getMessage());
                            log().warning("Got unexpected DittoRuntimeException when applying header mapping - " +
                                            "thus NOT publishing the message: {} {}",
                                    e.getClass().getSimpleName(), e.getMessage());
                        }
                    });
                })
                .match(RetrieveAddressStatus.class, ram -> getCurrentTargetStatus().forEach(rs ->
                        getSender().tell(rs, getSelf())))
                .matchAny(m -> {
                    log().warning("Unknown message: {}", m);
                    unhandled(m);
                });

        postEnhancement(receiveBuilder);
        return receiveBuilder.build();
    }

    private Collection<ResourceStatus> getCurrentTargetStatus() {
        if (resourceStatusMap.isEmpty()) {
            return Collections.singletonList(
                    ConnectivityModelFactory.newTargetStatus(getInstanceIdentifier(), ConnectivityStatus.UNKNOWN, null,
                            null));
        }
        return resourceStatusMap.values();
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
     * @param message the {@link org.eclipse.ditto.services.models.connectivity.ExternalMessage} to publish.
     * @param publishedMonitor the monitor that can be used for monitoring purposes.
     */
    protected abstract void publishMessage(@Nullable final Target target, final T publishTarget,
            final ExternalMessage message, final ConnectionMonitor publishedMonitor);

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
    private static boolean isResponseOrError(final OutboundSignal.WithExternalMessage outboundSignal) {
        return outboundSignal.getExternalMessage().isResponse() || outboundSignal.getExternalMessage().isError();
    }

    /**
     * Applies the optional "header mapping" potentially configured on the passed {@code target} on the passed {@code
     * outboundSignal}.
     *
     * @param outboundSignal the OutboundSignal containing the {@link ExternalMessage} with headers potentially
     * containing placeholders.
     * @param target the {@link Target} to extract the {@link org.eclipse.ditto.model.connectivity.HeaderMapping} from.
     * @param log the logger to use for logging.
     * @return the ExternalMessage with replaced headers
     */
    static ExternalMessage applyHeaderMapping(final OutboundSignal.WithExternalMessage outboundSignal,
            final Target target, final DiagnosticLoggingAdapter log) {

        final ExternalMessage originalMessage = outboundSignal.getExternalMessage();
        final Map<String, String> originalHeaders = new HashMap<>(originalMessage.getHeaders());

        // clear all existing headers in the builder which is used for building the ExternalMessage to be returned:
        final ExternalMessageBuilder messageBuilder = ExternalMessageFactory.newExternalMessageBuilder(originalMessage)
                .clearHeaders();

        // keep correlation-id, content-type and reply-to:
        Optional.ofNullable(originalHeaders.get(DittoHeaderDefinition.CORRELATION_ID.getKey()))
                .ifPresent(c ->
                        messageBuilder.withAdditionalHeaders(DittoHeaderDefinition.CORRELATION_ID.getKey(), c));
        Optional.ofNullable(originalHeaders.get(ExternalMessage.CONTENT_TYPE_HEADER))
                .ifPresent(c ->
                        messageBuilder.withAdditionalHeaders(ExternalMessage.CONTENT_TYPE_HEADER, c));
        Optional.ofNullable(originalHeaders.get(ExternalMessage.REPLY_TO_HEADER))
                .ifPresent(r ->
                        messageBuilder.withAdditionalHeaders(ExternalMessage.REPLY_TO_HEADER, r));

        return target.getHeaderMapping().map(mapping -> {
            if (mapping.getMapping().isEmpty()) {
                return messageBuilder.build();
            }
            final Signal<?> sourceSignal = outboundSignal.getSource();

            final ExpressionResolver expressionResolver = PlaceholderFactory.newExpressionResolver(
                    PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER, originalHeaders),
                    PlaceholderFactory.newPlaceholderResolver(THING_PLACEHOLDER, sourceSignal.getId()),
                    PlaceholderFactory.newPlaceholderResolver(TOPIC_PLACEHOLDER,
                            originalMessage.getTopicPath().orElse(null))
            );

            final Map<String, String> mappedHeaders = mapping.getMapping().entrySet().stream()
                    .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(),
                            PlaceholderFilter.apply(e.getValue(), expressionResolver, true))
                    )
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            LogUtil.enhanceLogWithCorrelationId(log, sourceSignal);
            log.debug("Result of header mapping <{}> are these headers to be published: {}", mapping, mappedHeaders);

            // only explicitly re-add the mapped headers:
            return messageBuilder
                    .withAdditionalHeaders(mappedHeaders)
                    .build();
        }).orElseGet(messageBuilder::build);
    }

}
