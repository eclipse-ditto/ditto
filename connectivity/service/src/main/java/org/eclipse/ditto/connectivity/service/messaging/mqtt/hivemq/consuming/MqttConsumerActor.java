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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.consuming;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.AcknowledgeableMessage;
import org.eclipse.ditto.connectivity.service.messaging.BaseConsumerActor;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.AcknowledgementUnsupportedException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.ManualAcknowledgementDisabledException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.MessageAlreadyAcknowledgedException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.MqttPublishTransformationException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.TransformationResult;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.health.RetrieveHealth;
import org.eclipse.ditto.internal.utils.health.RetrieveHealthResponse;
import org.eclipse.ditto.internal.utils.health.StatusInfo;

import com.hivemq.client.internal.mqtt.datatypes.MqttTopicFilterImpl;

import akka.NotUsed;
import akka.actor.Props;
import akka.japi.function.Predicate;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Actor that receives a stream of subscribed MQTT Publish messages, transforms them to {@link ExternalMessage}s and
 * forwards them to {@link org.eclipse.ditto.connectivity.service.messaging.InboundMappingSink}.
 */
public final class MqttConsumerActor extends BaseConsumerActor {

    private final OperationMode operationMode;
    private final MqttConfig mqttConfig;
    private final ThreadSafeDittoLoggingAdapter logger;
    private final MqttSpecificConfig mqttSpecificConfig;

    private KillSwitch killSwitch;
    private Source<GenericMqttPublish, NotUsed> mqttPublishSource;

    @SuppressWarnings("java:S1144")
    private MqttConsumerActor(final Connection connection,
            final Sink<Object, ?> inboundMappingSink,
            final org.eclipse.ditto.connectivity.model.Source source,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig,
            final Source<GenericMqttPublish, NotUsed> mqttPublishSource,
            final OperationMode operationMode) {

        super(connection,
                String.join(";", source.getAddresses()),
                inboundMappingSink,
                source,
                connectivityStatusResolver,
                connectivityConfig);

        this.operationMode = operationMode;

        final var connectionConfig = connectivityConfig.getConnectionConfig();
        mqttConfig = connectionConfig.getMqttConfig();

        logger = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);
        logger.withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connection.getId());
        logger.withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_TYPE, connection.getConnectionType());

        mqttSpecificConfig = MqttSpecificConfig.fromConnection(connection, mqttConfig);

        killSwitch = null;
        this.mqttPublishSource = mqttPublishSource;
    }

    /**
     * Returns the {@code Props} for creating an {@code MqttConsumerActor} with the specified arguments.
     * The once created consumer actor operates in 'dry-run' mode, i.e. it drops all MQTT publish messages it receives.
     * The dropped messages will be logged.
     *
     * @param connection the connection the consumer actor belongs to.
     * @param inboundMappingSink the mapping sink where received messages are forwarded to.
     * @param connectionSource the connection source of the consumer actor.
     * @param connectivityStatusResolver resolves occurred exceptions to a connectivity status.
     * @param connectivityConfig the config of Connectivity service with potential overwrites.
     * @param mqttPublishSource stream of received MQTT publish messages logs and drops.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Props propsDryRun(final Connection connection,
            final Sink<Object, ?> inboundMappingSink,
            final org.eclipse.ditto.connectivity.model.Source connectionSource,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig,
            final Source<GenericMqttPublish, NotUsed> mqttPublishSource) {

        return Props.create(
                MqttConsumerActor.class,
                ConditionChecker.checkNotNull(connection, "connection"),
                ConditionChecker.checkNotNull(inboundMappingSink, "inboundMappingSink"),
                ConditionChecker.checkNotNull(connectionSource, "connectionSource"),
                ConditionChecker.checkNotNull(connectivityStatusResolver, "connectivityStatusResolver"),
                ConditionChecker.checkNotNull(connectivityConfig, "connectivityConfig"),
                ConditionChecker.checkNotNull(mqttPublishSource, "mqttPublishSource"),
                OperationMode.DRY_RUN
        );
    }

    /**
     * Returns the {@code Props} for creating an {@code MqttConsumerActor} with the specified arguments.
     * The once created consumer actor operates in 'processing' mode, i.e. it actually processes all MQTT publish
     * messages it receives.
     *
     * @param connection the connection the consumer actor belongs to.
     * @param inboundMappingSink the mapping sink where received messages are forwarded to.
     * @param connectionSource the connection source of the consumer actor.
     * @param connectivityStatusResolver resolves occurred exceptions to a connectivity status.
     * @param connectivityConfig the config of Connectivity service with potential overwrites.
     * @param mqttPublishSource stream of received MQTT publish messages the consumer actor processes.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Props propsProcessing(final Connection connection,
            final Sink<Object, ?> inboundMappingSink,
            final org.eclipse.ditto.connectivity.model.Source connectionSource,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig,
            final Source<GenericMqttPublish, NotUsed> mqttPublishSource) {

        return Props.create(
                MqttConsumerActor.class,
                ConditionChecker.checkNotNull(connection, "connection"),
                ConditionChecker.checkNotNull(inboundMappingSink, "inboundMappingSink"),
                ConditionChecker.checkNotNull(connectionSource, "connectionSource"),
                ConditionChecker.checkNotNull(connectivityStatusResolver, "connectivityStatusResolver"),
                ConditionChecker.checkNotNull(connectivityConfig, "connectivityConfig"),
                ConditionChecker.checkNotNull(mqttPublishSource, "mqttPublishSource"),
                OperationMode.PROCESSING
        );
    }

    @Override
    public void preStart() throws Exception {
        throttleMqttPublishSourceIfThrottlingEnabled();
        if (OperationMode.DRY_RUN == operationMode) {
            killSwitch = dropAndLogMqttPublishes();
        } else {
            killSwitch = processMqttPublishes();
        }
    }

    private void throttleMqttPublishSourceIfThrottlingEnabled() {
        final var throttlingConfig = mqttConfig.getConsumerThrottlingConfig();
        if (throttlingConfig.isEnabled()) {
            mqttPublishSource = mqttPublishSource.throttle(throttlingConfig.getLimit(), throttlingConfig.getInterval());
        }
    }

    private KillSwitch dropAndLogMqttPublishes() {
        return mqttPublishSource.viaMat(KillSwitches.single(), Keep.right())
                .to(Sink.foreach(publish -> logger.info("Operating in 'dry-run' mode, thus dropping <{}>.", publish)))
                .run(getContext().getSystem());
    }

    private KillSwitch processMqttPublishes() {
        final var mqttPublishTransformer = MqttPublishToExternalMessageTransformer.newInstance(sourceAddress, source);

        return mqttPublishSource.viaMat(KillSwitches.single(), Keep.right())
                .filter(this::messageHasRightTopicPath)
                .map(mqttPublishTransformer::transform)
                .divertTo(getTransformationFailureSink(), TransformationResult::isFailure)
                .to(getTransformationSuccessSink())
                .run(getContext().getSystem());
    }

    /**
     * Filters out messages which don't match the sources topics. This is done because the HiveMQ API makes it hard
     * to consume only messages which match specific topics in the first place.
     *
     * @param genericMqttPublish a consumed MQTT message.
     * @return whether the message matches the topics of this source.
     */
    private boolean messageHasRightTopicPath(final GenericMqttPublish genericMqttPublish) {
        return super.source.getAddresses()
                .stream()
                .map(MqttTopicFilterImpl::of)
                .anyMatch(topicFilter -> topicFilter.matches(genericMqttPublish.getTopic()));
    }

    private <T extends TransformationResult<GenericMqttPublish, ExternalMessage>> Sink<T, ?> getTransformationFailureSink() {
        final Predicate<MqttPublishTransformationException> isCausedByDittoRuntimeException = exception -> {
            final var cause = exception.getCause();
            return cause instanceof DittoRuntimeException;
        };

        return Flow.<T, MqttPublishTransformationException>fromFunction(TransformationResult::getErrorOrThrow)
                .divertTo(Flow.fromFunction(MqttConsumerActor::appendMqttPublishHeadersToDittoRuntimeException)
                                .to(getDittoRuntimeExceptionSink()),
                        isCausedByDittoRuntimeException)
                .to(Sink.foreach(this::recordTransformationException));
    }

    private static DittoRuntimeException appendMqttPublishHeadersToDittoRuntimeException(
            final MqttPublishTransformationException transformationException
    ) {
        final var dittoRuntimeException = (DittoRuntimeException) transformationException.getCause();
        return dittoRuntimeException.setDittoHeaders(dittoRuntimeException.getDittoHeaders()
                .toBuilder()
                .putHeaders(transformationException.getMqttPublishHeaders())
                .build());
    }

    private void recordTransformationException(final MqttPublishTransformationException transformationException) {
        final var mqttPublishHeaders = transformationException.getMqttPublishHeaders();
        if (mqttPublishHeaders.isEmpty()) {
            inboundMonitor.exception(transformationException.getCause());
        } else {
            inboundMonitor.exception(mqttPublishHeaders, transformationException.getCause());
        }
    }

    private <T extends TransformationResult<GenericMqttPublish, ExternalMessage>> Sink<T, ?> getTransformationSuccessSink() {
        return Flow.<T>create()
                .alsoTo(Flow.<T, ExternalMessage>fromFunction(TransformationResult::getSuccessValueOrThrow)
                        .to(Sink.foreach(inboundMonitor::success)))
                .map(this::getAcknowledgeableMessageForTransformationResult)
                .to(getMessageMappingSink());
    }

    private AcknowledgeableMessage getAcknowledgeableMessageForTransformationResult(
            final TransformationResult<GenericMqttPublish, ExternalMessage> transformationResult
    ) {
        final var externalMessage = transformationResult.getSuccessValueOrThrow();
        final var genericMqttPublish = transformationResult.getTransformationInput();

        return AcknowledgeableMessage.of(externalMessage,
                () -> tryToAcknowledgePublish(genericMqttPublish, externalMessage),
                shouldRedeliver -> rejectIncomingMessage(shouldRedeliver, externalMessage, genericMqttPublish));
    }

    private void tryToAcknowledgePublish(final GenericMqttPublish mqttPublish, final ExternalMessage externalMessage) {
        try {
            acknowledgePublish(mqttPublish, externalMessage);
        } catch (final ManualAcknowledgementDisabledException e) {
            inboundAcknowledgedMonitor.exception(externalMessage, """
                    Manual acknowledgement of incoming message at topic <{0}> failed because manual acknowledgement \
                    is disabled for this source's MQTT subscription.\
                    """, mqttPublish.getTopic());
        } catch (final MessageAlreadyAcknowledgedException e) {
            inboundAcknowledgedMonitor.exception(externalMessage, """
                    Acknowledgement of incoming message at topic <{0}> failed because it was acknowledged already by \
                    another source.\
                    """, mqttPublish.getTopic());
        } catch (final AcknowledgementUnsupportedException e) {
            inboundAcknowledgedMonitor.exception(externalMessage,
                    "Manual acknowledgement of incoming message at topic <{0}> failed: {1}",
                    mqttPublish.getTopic(),
                    e.getMessage());
        }
    }

    private void acknowledgePublish(final GenericMqttPublish mqttPublish, final ExternalMessage externalMessage)
            throws ManualAcknowledgementDisabledException, MessageAlreadyAcknowledgedException,
            AcknowledgementUnsupportedException {

        mqttPublish.acknowledge();
        inboundAcknowledgedMonitor.success(externalMessage, "Sending success acknowledgement.");
    }

    private void rejectIncomingMessage(final boolean shouldRedeliver,
            final ExternalMessage externalMessage,
            final GenericMqttPublish mqttPublish) {

        if (shouldRedeliver) {
            if (mqttSpecificConfig.reconnectForRedelivery()) {
                inboundAcknowledgedMonitor.exception(externalMessage, "Unfulfilled acknowledgements are present," +
                        " restarting consumer client in order to get redeliveries.");
                reconnectConsumerClientForRedelivery();
            } else {

                /*
                 * Strictly speaking one should not acknowledge a message for which
                 * a redelivery was asked for.
                 * The MQTT spec, however, does not define that a MQTT broker
                 * should redeliver messages if an acknowledgement was not
                 * received – *unless* the client reconnects – see option
                 * `reconnectForRedelivery` for getting reconnects.
                 */
                tryToAcknowledgePublish(mqttPublish, externalMessage);
                inboundAcknowledgedMonitor.exception(externalMessage, """
                        Unfulfilled acknowledgements are present. \
                        Acknowledging the MQTT message despite redelivery was requested because MQTT broker would not \
                        redeliver the message without a reconnect from the client.\
                        """);
            }
        } else {

            /*
             * Acknowledge messages for which redelivery does not make sense
             * (e.g. 400 bad request or 403 forbidden) as redelivering them
             * will not solve any problem.
             */
            tryToAcknowledgePublish(mqttPublish, externalMessage);
            inboundAcknowledgedMonitor.exception(externalMessage, """
                    Unfulfilled acknowledgements are present. \
                    Acknowledging the MQTT message because redelivery was not requested.\
                    """);

        }
    }

    private void reconnectConsumerClientForRedelivery() {
        final var context = getContext();
        final var parent = context.getParent();
        parent.tell(ReconnectConsumerClient.of(mqttSpecificConfig.getReconnectForDeliveryDelay()), getSelf());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(RetrieveAddressStatus.class, msg -> getSender().tell(getCurrentSourceStatus(), getSelf()))
                .match(GracefulStop.class, gracefulStop -> shutdown())
                .match(RetrieveHealth.class, this::checkThatThisActorIsRunning)
                .build();
    }

    private void checkThatThisActorIsRunning(final RetrieveHealth command) {
        final var sender = getSender();
        sender.tell(RetrieveHealthResponse.of(StatusInfo.fromStatus(StatusInfo.Status.UP), command.getDittoHeaders()),
                getSelf());
    }

    private void shutdown() {
        if (null != killSwitch) {
            killSwitch.shutdown();
        }
        final var context = getContext();
        context.stop(self());
    }

    @Override
    public void unhandled(final Object message) {
        logger.info("Received unhandled message <{}>.", message);
        super.unhandled(message);
    }

    @Override
    protected ThreadSafeDittoLoggingAdapter log() {
        return logger;
    }

    private enum OperationMode {
        DRY_RUN,
        PROCESSING
    }

}
