/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.GenericTarget;
import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.BasePublisherActor;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.SendResult;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttPublishingClient;
import org.eclipse.ditto.internal.utils.health.RetrieveHealth;
import org.eclipse.ditto.internal.utils.health.RetrieveHealthResponse;
import org.eclipse.ditto.internal.utils.health.StatusInfo;

import akka.actor.Props;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.BoundedSourceQueue;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.QueueOfferResult;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Actor responsible for converting outbound {@code Signal}s to a {@code GenericMqttPublish} and sending them to an
 * MQTT broker using the given {@link GenericMqttPublishingClient}.
 */
public final class MqttPublisherActor extends BasePublisherActor<MqttPublishTarget> {

    private final GenericMqttPublishingClient genericMqttPublishingClient;
    private final OperationMode operationMode;

    private BoundedSourceQueue<MqttPublishingContext> sourceQueue;
    private KillSwitch killSwitch;

    @SuppressWarnings("java:S1144")
    private MqttPublisherActor(final Connection connection,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig,
            final GenericMqttPublishingClient genericMqttPublishingClient,
            final OperationMode operationMode) {

        super(connection, connectivityStatusResolver, connectivityConfig);
        this.genericMqttPublishingClient = genericMqttPublishingClient;
        this.operationMode = operationMode;

        sourceQueue = null;
        killSwitch = null;
    }

    /**
     * Returns the {@code Props} for creating an {@code MqttPublisherActor} with the specified arguments.
     * The once created producer actor operates in 'dry-run' mode, i.e. it drops all outbound signals it receives for
     * sending.
     * The dropped messages will be logged.
     *
     * @param connection the connection the consumer actor belongs to.
     * @param connectivityStatusResolver resolves occurred exceptions to a connectivity status.
     * @param connectivityConfig the config of Connectivity service with potential overwrites.
     * @param genericMqttPublishingClient generic MQTT client for actual sending MQTT Publish messages to the broker.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Props propsDryRun(final Connection connection,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig,
            final GenericMqttPublishingClient genericMqttPublishingClient) {

        return Props.create(
                MqttPublisherActor.class,
                ConditionChecker.checkNotNull(connection, "connection"),
                ConditionChecker.checkNotNull(connectivityStatusResolver, "connectivityStatusResolver"),
                ConditionChecker.checkNotNull(connectivityConfig, "connectivityConfig"),
                ConditionChecker.checkNotNull(genericMqttPublishingClient, "genericMqttPublishingClient"),
                OperationMode.DRY_RUN
        );
    }

    /**
     * Returns the {@code Props} for creating an {@code MqttPublisherActor} with the specified arguments.
     * The once created producer actor operates in 'processing' mode, i.e. it actually sends all outbound signals it
     * receives as MQTT Publish to the broker.
     *
     * @param connection the connection the consumer actor belongs to.
     * @param connectivityStatusResolver resolves occurred exceptions to a connectivity status.
     * @param connectivityConfig the config of Connectivity service with potential overwrites.
     * @param genericMqttPublishingClient generic MQTT client for actual sending MQTT Publish messages to the broker.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Props propsProcessing(final Connection connection,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig,
            final GenericMqttPublishingClient genericMqttPublishingClient) {

        return Props.create(
                MqttPublisherActor.class,
                ConditionChecker.checkNotNull(connection, "connection"),
                ConditionChecker.checkNotNull(connectivityStatusResolver, "connectivityStatusResolver"),
                ConditionChecker.checkNotNull(connectivityConfig, "connectivityConfig"),
                ConditionChecker.checkNotNull(genericMqttPublishingClient, "genericMqttPublishingClient"),
                OperationMode.PROCESSING
        );
    }

    @Override
    public void preStart() throws Exception {
        final var sourceQueueWithCompleteUniqueKillSwitchPair = initializeSourceQueue();
        sourceQueue = sourceQueueWithCompleteUniqueKillSwitchPair.first();
        killSwitch = sourceQueueWithCompleteUniqueKillSwitchPair.second();
    }

    private Pair<BoundedSourceQueue<MqttPublishingContext>, UniqueKillSwitch> initializeSourceQueue() {
        final var connectionConfig = connectivityConfig.getConnectionConfig();
        final var mqttConfig = connectionConfig.getMqttConfig();

        return Source.<MqttPublishingContext>queue(mqttConfig.getMaxQueueSize())
                .viaMat(KillSwitches.single(), Keep.both())
                .to(Sink.foreach(this::sendMqttPublishMessageToBroker))
                .run(getContext().getSystem());
    }

    private void sendMqttPublishMessageToBroker(final MqttPublishingContext mqttPublishingContext) {
        final var genericMqttPublish = mqttPublishingContext.getGenericMqttPublish();
        if (logger.isDebugEnabled()) {
            logger.withCorrelationId(mqttPublishingContext.getSignalDittoHeaders())
                    .debug("Publishing MQTT message to topic <{}>: {}",
                            genericMqttPublish.getTopic(),
                            genericMqttPublish.getPayloadAsHumanReadable().orElse("<empty>"));
        }

        genericMqttPublishingClient.publish(genericMqttPublish)
                .thenAccept(genericMqttPublishResult -> {
                    if (genericMqttPublishResult.isSuccess()) {
                        handleGenericMqttPublishResultSuccess(mqttPublishingContext);
                    } else {
                        handleGenericMqttPublishResultFailure(mqttPublishingContext,
                                genericMqttPublishResult.getErrorOrThrow());
                    }
                });
    }

    private static void handleGenericMqttPublishResultSuccess(final MqttPublishingContext mqttPublishingContext) {
        final var sendResultCompletableFuture = mqttPublishingContext.getSendResultCompletableFuture();
        sendResultCompletableFuture.complete(getSuccessfulSendResult(mqttPublishingContext));
    }

    private static SendResult getSuccessfulSendResult(final MqttPublishingContext mqttPublishingContext) {
        return new SendResult(mqttPublishingContext.getAutoAcknowledgement().orElse(null),
                mqttPublishingContext.getSignalDittoHeaders());
    }

    private void handleGenericMqttPublishResultFailure(final MqttPublishingContext mqttPublishingContext,
            final Throwable error) {

        logger.withCorrelationId(mqttPublishingContext.getSignalDittoHeaders())
                .warning("Failed to send MQTT Publish message to broker. {}: {}",
                        error.getClass().getSimpleName(),
                        error.getMessage());

        final var sendResultCompletableFuture = mqttPublishingContext.getSendResultCompletableFuture();
        sendResultCompletableFuture.completeExceptionally(MessageSendingFailedException.newBuilder()
                .cause(error)
                .dittoHeaders(mqttPublishingContext.getSignalDittoHeaders())
                .build());
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        if (OperationMode.DRY_RUN == operationMode) {
            receiveBuilder.match(OutboundSignal.Mapped.class, this::logDroppedOutboundMessageInDryRunMode);
            receiveBuilder.match(OutboundSignal.MultiMapped.class, this::logDroppedOutboundMessageInDryRunMode);
        }
    }

    private void logDroppedOutboundMessageInDryRunMode(final OutboundSignal outboundSignal) {
        logger.withCorrelationId(outboundSignal.getSource())
                .info("Operating in 'dry-run' mode, thus dropping <{}>.", outboundSignal);
    }

    @Override
    protected void postEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder.match(RetrieveHealth.class, this::checkThatThisActorIsRunning);
    }

    private void checkThatThisActorIsRunning(final RetrieveHealth command) {
        final var sender = getSender();
        sender.tell(RetrieveHealthResponse.of(StatusInfo.fromStatus(StatusInfo.Status.UP), command.getDittoHeaders()),
                getSelf());
    }

    @Override
    protected MqttPublishTarget toPublishTarget(final GenericTarget target) {
        final var mqttPublishTargetTry = MqttPublishTarget.tryNewInstance(target);

        /*
         * The values provided by `target` should be valid because
         * `ConnectionPersistenceActor` already ensures that only
         * valid topics and QoS codes are persisted per connection target.
         * Thus, ignoring potential failure when converting from `target`
         * to `MqttPublishTarget`.
         */
        return mqttPublishTargetTry.get();
    }

    @Override
    protected CompletionStage<SendResult> publishMessage(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final MqttPublishTarget publishTarget,
            final ExternalMessage externalMessage,
            final int maxTotalMessageSize,
            final int ackSizeQuota,
            @Nullable final AuthorizationContext targetAuthorizationContext) {

        final CompletionStage<SendResult> result;
        final var transformationResult =
                ExternalMessageToMqttPublishTransformer.transform(externalMessage, publishTarget);
        if (transformationResult.isSuccess()) {
            result = offerToSourceQueue(MqttPublishingContext.newInstance(transformationResult.getSuccessValueOrThrow(),
                    signal,
                    autoAckTarget,
                    connectionIdResolver));
        } else {
            result = CompletableFuture.failedFuture(MessageSendingFailedException.newBuilder()
                    .cause(transformationResult.getErrorOrThrow())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .dittoHeaders(signal.getDittoHeaders())
                    .build());
        }
        return result;
    }

    private CompletionStage<SendResult> offerToSourceQueue(final MqttPublishingContext mqttPublishingContext) {
        final CompletionStage<SendResult> result;

        final var queueOfferResult = sourceQueue.offer(mqttPublishingContext);
        if (isDropped(queueOfferResult)) {
            throw getMessageSendingFailedExceptionBecauseDroppedBySourceQueue(mqttPublishingContext);
        } else if (isQueueClosed(queueOfferResult)) {
            throw getMessageSendingFailedExceptionBecauseSourceQueueClosed(mqttPublishingContext);
        } else if (queueOfferResult instanceof QueueOfferResult.Failure failure) {
            result = CompletableFuture.failedFuture(failure.cause());
        } else {
            result = mqttPublishingContext.getSendResultCompletableFuture();
        }

        return result.whenComplete((sendResult, error) -> {
            if (null != error) {
                final var errorDescription = MessageFormat.format(
                        "Failed to enqueue MQTT message to topic <{0}> for sending to broker.",
                        mqttPublishingContext.getGenericMqttPublish().getTopic()
                );
                logger.withCorrelationId(mqttPublishingContext.getSignalDittoHeaders()).error(error, errorDescription);
                escalate(error, errorDescription);
            }
        });
    }

    private static boolean isDropped(final QueueOfferResult queueOfferResult) {
        return Objects.equals(queueOfferResult, QueueOfferResult.dropped());
    }

    private MessageSendingFailedException getMessageSendingFailedExceptionBecauseDroppedBySourceQueue(
            final MqttPublishingContext mqttPublishingContext
    ) {
        return MessageSendingFailedException.newBuilder()
                .message(MessageFormat.format("""
                                Outgoing MQTT message to topic <{0}> dropped because there are too many \
                                in-flight requests.
                                """,
                        mqttPublishingContext.getGenericMqttPublish().getTopic()))
                .description(MessageFormat.format("""
                                This can have the following reasons:
                                  a) The MQTT broker does not consume the messages fast enough.
                                  b) The configured client count <{0,number,integer}> of connection <{1}> is too low.
                                """,
                        connection.getClientCount(),
                        connection.getId()))
                .dittoHeaders(mqttPublishingContext.getSignalDittoHeaders())
                .build();
    }

    private static boolean isQueueClosed(final QueueOfferResult queueOfferResult) {
        return queueOfferResult instanceof QueueOfferResult.QueueClosed$;
    }

    private static MessageSendingFailedException getMessageSendingFailedExceptionBecauseSourceQueueClosed(
            final MqttPublishingContext mqttPublishingContext
    ) {
        return MessageSendingFailedException.newBuilder()
                .message(MessageFormat.format("""
                                Outgoing MQTT message to topic <{0}> could not be sent because the stream to the \
                                MQTT client was already closed.
                                """,
                        mqttPublishingContext.getGenericMqttPublish().getTopic())
                )
                .dittoHeaders(mqttPublishingContext.getSignalDittoHeaders())
                .build();
    }

    @Override
    public void postStop() throws Exception {
        if (null != killSwitch) {
            killSwitch.shutdown();
        }
    }

    private enum OperationMode {
        DRY_RUN,
        PROCESSING
    }

}
