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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.ReplyTarget;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
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
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionEvent;

import akka.actor.AbstractActor;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Base class for publisher actors. Holds the map of configured targets.
 *
 * @param <T> the type of targets for this actor
 */
public abstract class BasePublisherActor<T extends PublishTarget> extends AbstractActor {

    protected final ConnectionId connectionId;
    protected final List<Target> targets;
    protected final Map<Target, ResourceStatus> resourceStatusMap;

    protected final ConnectionLogger connectionLogger;
    protected final ConnectionMonitor responsePublishedMonitor;
    protected final ConnectionMonitor responseDroppedMonitor;
    private final ConnectionMonitorRegistry<ConnectionMonitor> connectionMonitorRegistry;
    private final List<Optional<ReplyTarget>> replyTargets;

    protected BasePublisherActor(final Connection connection) {
        checkNotNull(connection, "connection");
        this.connectionId = connection.getId();
        this.targets = connection.getTargets();
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
        replyTargets = connection.getSources().stream().map(Source::getReplyTarget).collect(Collectors.toList());
    }

    private static String getInstanceIdentifier() {
        return InstanceIdentifierSupplier.getInstance().get();
    }

    @Override
    public Receive createReceive() {
        final ReceiveBuilder receiveBuilder = receiveBuilder();
        preEnhancement(receiveBuilder);

        receiveBuilder.match(OutboundSignal.Mapped.class, BasePublisherActor::isResponseOrErrorOrSearchEvent,
                outbound -> {
                    final ExternalMessage response = outbound.getExternalMessage();
                    final String correlationId = response.getHeaders().get(CORRELATION_ID.getKey());
                    ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log(), correlationId, connectionId);

                    final Optional<ReplyTarget> replyTargetOptional = outbound.getSource()
                            .getDittoHeaders()
                            .getReplyTarget()
                            .flatMap(this::getReplyTargetByIndex);
                    if (replyTargetOptional.isPresent()) {
                        catchHeaderMappingException(responsePublishedMonitor, outbound.getSource(), () -> {
                            final ReplyTarget replyTarget = replyTargetOptional.get();
                            final ExpressionResolver expressionResolver = Resolvers.forOutbound(outbound);
                            final String address = replyTarget.getAddress();
                            final Optional<T> resolvedAddress =
                                    resolveTargetAddress(expressionResolver, address).map(this::toPublishTarget);

                            if (resolvedAddress.isPresent()) {
                                final HeaderMapping headerMapping = replyTarget.getHeaderMapping().orElse(null);
                                final ExternalMessage responseWithMappedHeaders =
                                        applyHeaderMapping(expressionResolver, outbound, headerMapping, log());
                                publishResponseOrError(resolvedAddress.get(), outbound, responseWithMappedHeaders);
                            } else {
                                log().debug("Response dropped, reply-target address unresolved: <{}>", address);
                                responseDroppedMonitor.failure(outbound.getSource(),
                                        "Response dropped since its reply-target''s address " +
                                                "cannot be resolved to a value: {0}",
                                        address);
                            }
                        });
                    } else {
                        log().debug("Response dropped, missing reply-target: {}", response);
                        responseDroppedMonitor.failure(outbound.getSource(),
                                "Response dropped since it was missing a reply-target.");
                    }
                })
                .match(OutboundSignal.Mapped.class, outbound -> {
                    final ExpressionResolver resolver = Resolvers.forOutbound(outbound);
                    final ExternalMessage message = outbound.getExternalMessage();
                    final String correlationId = message.getHeaders().get(CORRELATION_ID.getKey());
                    ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log(), correlationId, connectionId);

                    final Signal<?> outboundSource = outbound.getSource();
                    log().debug("Publishing mapped message of type <{}> to targets <{}>: {}",
                            outboundSource.getType(), outbound.getTargets(), message);
                    outbound.getTargets().forEach(target -> {
                        log().debug("Publishing mapped message of type <{}> to target address <{}>",
                                outboundSource.getType(), target.getAddress());

                        final ConnectionMonitor publishedMonitor =
                                connectionMonitorRegistry.forOutboundPublished(connectionId,
                                        target.getOriginalAddress());
                        final HeaderMapping headerMapping = target.getHeaderMapping().orElse(null);
                        catchHeaderMappingException(publishedMonitor, outboundSource, () ->
                                resolveTargetAddress(resolver, target.getAddress())
                                        .map(this::toPublishTarget)
                                        .ifPresent(publishTarget -> {
                                            final ExternalMessage mappedMessage =
                                                    applyHeaderMapping(resolver, outbound, headerMapping, log());
                                            publishMessage(target, publishTarget, mappedMessage, publishedMonitor);
                                        }));
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

    private void catchHeaderMappingException(final ConnectionMonitor publishedMonitor, final Signal<?> outboundSource,
            final Runnable doPublish) {
        try {
            doPublish.run();
        } catch (final DittoRuntimeException e) {
            publishedMonitor.failure(outboundSource,
                    "Failed to publish signal: {0}",
                    e.getMessage());
            log().warning("Got unexpected DittoRuntimeException when publishing a signal: {} {}",
                    e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void publishResponseOrError(final T address, final OutboundSignal outbound,
            final ExternalMessage response) {

        log().debug("Publishing mapped response/error message of type <{}> to reply address <{}>: {}",
                outbound.getSource().getType(), address, response);
        publishMessage(null, address, response, responsePublishedMonitor);
    }

    private Optional<ReplyTarget> getReplyTargetByIndex(final int replyTargetIndex) {
        return 0 <= replyTargetIndex && replyTargetIndex < replyTargets.size()
                ? replyTargets.get(replyTargetIndex)
                : Optional.empty();
    }

    private Collection<ResourceStatus> getCurrentTargetStatus() {
        return resourceStatusMap.values();
    }

    /**
     * Provides the possibility to add custom matchers before applying the default matchers of the BasePublisherActor.
     *
     * @param receiveBuilder the ReceiveBuilder to add other matchers to.
     */
    protected abstract void preEnhancement(ReceiveBuilder receiveBuilder);

    /**
     * Provides the possibility to add custom matchers after applying the default matchers of the BasePublisherActor.
     *
     * @param receiveBuilder the ReceiveBuilder to add other matchers to.
     */
    protected abstract void postEnhancement(ReceiveBuilder receiveBuilder);

    /**
     * Converts the passed {@code address} to a {@link PublishTarget} of type {@code <T>}.
     *
     * @param address the address to convert to a {@link PublishTarget} of type {@code <T>}.
     * @return the instance of type {@code <T>}
     */
    protected abstract T toPublishTarget(final String address);

    /**
     * Publishes the passed {@code message} to the passed {@code publishTarget}.
     *
     * @param target the nullable Target for getting even more information about the configured Target to publish to.
     * @param publishTarget the {@link PublishTarget} to publish to.
     * @param message the {@link org.eclipse.ditto.services.models.connectivity.ExternalMessage} to publish.
     * @param publishedMonitor the monitor that can be used for monitoring purposes.
     */
    protected abstract void publishMessage(@Nullable Target target, T publishTarget,
            ExternalMessage message, ConnectionMonitor publishedMonitor);

    /**
     * @return the logger to use.
     */
    protected abstract DiagnosticLoggingAdapter log();

    /**
     * Checks whether the passed in {@code outboundSignal} is a response or an error or a search event.
     * Those messages are supposed to be published at the reply target of the source whence the original command came.
     *
     * @param outboundSignal the OutboundSignal to check.
     * @return {@code true} if the OutboundSignal is a response or an error, {@code false} otherwise
     */
    private static boolean isResponseOrErrorOrSearchEvent(final OutboundSignal.Mapped outboundSignal) {
        return outboundSignal.getExternalMessage().isResponse() ||
                outboundSignal.getExternalMessage().isError() ||
                outboundSignal.getSource() instanceof SubscriptionEvent;
    }

    /**
     * Applies the optional "header mapping" potentially configured on the passed {@code target} on the passed {@code
     * outboundSignal}.
     *
     * @param outboundSignal the OutboundSignal containing the {@link ExternalMessage} with headers potentially
     * containing placeholders.
     * @param mapping headerMappings to apply.
     * @param log the logger to use for logging.
     * @return the ExternalMessage with replaced headers
     */
    static ExternalMessage applyHeaderMapping(final OutboundSignal.Mapped outboundSignal,
            final @Nullable HeaderMapping mapping,
            final DiagnosticLoggingAdapter log) {

        return applyHeaderMapping(Resolvers.forOutbound(outboundSignal), outboundSignal, mapping, log);
    }

    private static ExternalMessage applyHeaderMapping(final ExpressionResolver expressionResolver,
            final OutboundSignal.Mapped outboundSignal,
            final @Nullable HeaderMapping mapping,
            final DiagnosticLoggingAdapter log) {

        final ExternalMessage originalMessage = outboundSignal.getExternalMessage();

        final ExternalMessageBuilder messageBuilder = ExternalMessageFactory.newExternalMessageBuilder(originalMessage);

        if (mapping != null) {
            final Signal<?> sourceSignal = outboundSignal.getSource();

            final Map<String, String> mappedHeaders = mapping.getMapping().entrySet().stream()
                    .flatMap(e -> mapHeaderByResolver(expressionResolver, e.getValue())
                            .map(resolvedValue -> Stream.of(new AbstractMap.SimpleEntry<>(e.getKey(), resolvedValue)))
                            .orElseGet(Stream::empty))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            LogUtil.enhanceLogWithCorrelationId(log, sourceSignal);
            log.debug("Result of header mapping <{}> are these headers to be published: {}", mapping, mappedHeaders);

            messageBuilder.withAdditionalHeaders(mappedHeaders);
        }

        return messageBuilder.build();
    }

    private static Optional<String> mapHeaderByResolver(final ExpressionResolver resolver, final String value) {
        return PlaceholderFilter.applyOrElseDelete(value, resolver);
    }

    /**
     * Resolve target address.
     * If not resolvable, the returned Optional will be empty.
     */
    private static Optional<String> resolveTargetAddress(final ExpressionResolver resolver, final String value) {
        return resolver.resolve(value).toOptional();
    }

}
