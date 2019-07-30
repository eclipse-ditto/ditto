/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collector;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.common.ByteBufferUtils;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.placeholders.EnforcementFactoryFactory;
import org.eclipse.ditto.model.placeholders.EnforcementFilter;
import org.eclipse.ditto.model.placeholders.EnforcementFilterFactory;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.services.connectivity.messaging.BaseConsumerActor;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.alpakka.MqttClientActor;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3SubscribeBuilder;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3SubscribeBuilderBase;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscription;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck;
import com.hivemq.client.mqtt.mqtt3.message.unsubscribe.Mqtt3Unsubscribe;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.routing.ConsistentHashingRouter;

/**
 * TODO: comment and test
 * <p>
 * Actor which receives message from a MQTT broker and forwards them to a {@code MessageMappingProcessorActor}.
 */
public final class HiveMqtt3ConsumerActor extends BaseConsumerActor {

    private static final String MQTT_TOPIC_HEADER = "mqtt.topic";
    private static final MqttQos DEFAULT_QOS = MqttQos.AT_MOST_ONCE;
    static final String NAME = "HiveMqtt3ConsumerActor";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final boolean dryRun;
    private final Mqtt3AsyncClient client;
    private final Collection<String> topics;
    @Nullable private final Mqtt3Subscribe mqtt3Subscribe;
    @Nullable private final EnforcementFilterFactory<String, String> topicEnforcementFilterFactory;

    @SuppressWarnings("unused")
    private HiveMqtt3ConsumerActor(final String connectionId, final ActorRef messageMappingProcessor,
            final Source source, final boolean dryRun, final Mqtt3AsyncClient client) {
        super(connectionId, String.join(";", source.getAddresses()), messageMappingProcessor,
                source.getAuthorizationContext(), null);
        this.client = client;
        this.dryRun = dryRun;
        this.topics = source.getAddresses();

        topicEnforcementFilterFactory = source.getEnforcement()
                .map(enforcement -> EnforcementFactoryFactory
                        .newEnforcementFilterFactory(enforcement, PlaceholderFactory.newSourceAddressPlaceholder()))
                .orElse(null);

        this.mqtt3Subscribe = prepareSubscriptions(topics);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connectionId ID of the connection this consumer is belongs to
     * @param messageMappingProcessor the ActorRef to the {@code MessageMappingProcessor}
     * @param source the source from which this consumer is built
     * @param dryRun whether this is a dry-run/connection test or not
     * @return the Akka configuration Props object.
     */
    static Props props(final String connectionId, final ActorRef messageMappingProcessor,
            final Source source, final boolean dryRun, final Mqtt3Client client) {

        return Props.create(HiveMqtt3ConsumerActor.class, connectionId, messageMappingProcessor,
                source, dryRun, checkNotNull(client).toAsync());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(HiveMqtt3ClientActor.HiveMqttClientEvents.CONNECTED, this::onClientConnected)
                // TODO: implement 'onReconnected' to prevent unknown Success / Failure messages to the client actor
                .matchEquals(HiveMqtt3ClientActor.HiveMqttClientEvents.DISCONNECTED, this::onClientDisconnected)
                .match(Mqtt3Publish.class, this::isDryRun,
                        message -> log.info("Dropping message in dryRun mode: {}", message))
                .match(Mqtt3Publish.class, this::handleMqttMessage)
                .match(RetrieveAddressStatus.class, ram -> getSender().tell(getCurrentSourceStatus(), getSelf()))
                .matchAny(unhandled -> {
                    log.info("Unhandled message: {}", unhandled);
                    unhandled(unhandled);
                })
                .build();
    }

    private void handleMqttMessage(final Mqtt3Publish message) {
        log.info("Received message: {}", message);

        final Optional<ExternalMessage> externalMessageOptional = hiveToExternalMessage(message, connectionId);
        if (externalMessageOptional.isPresent()) {
            final Object msg = new ConsistentHashingRouter.ConsistentHashableEnvelope(externalMessageOptional.get(),
                    message.getTopic().toString());
            messageMappingProcessor.tell(msg, ActorRef.noSender());
        }
    }

    private Optional<ExternalMessage> hiveToExternalMessage(final Mqtt3Publish message, final String connectionId) {
        HashMap<String, String> headers = null;
        try {
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
            final ByteBuffer payload = message.getPayload().orElse(ByteBufferUtils.empty());
            final String topic = message.getTopic().toString();
            if (log.isDebugEnabled()) {
                log.debug("Received MQTT message on topic <{}>: {}", topic,
                        ByteBufferUtils.toUtf8String(payload));
            }
            headers = new HashMap<>();

            headers.put(MQTT_TOPIC_HEADER, topic);
            final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers)
                    // TODO: double check this. The payload gotten from Mqtt3Publish is readonly and throws ReadonlyBufferException...
                    .withBytes(ByteBufferUtils.clone(payload))
                    .withAuthorizationContext(authorizationContext)
                    .withEnforcement(getEnforcementFilter(topic))
                    .withSourceAddress(sourceAddress)
                    .build();
            inboundMonitor.success(externalMessage);


            return Optional.of(externalMessage);
        } catch (final DittoRuntimeException e) {
            log.info("Failed to handle MQTT message: {}", e.getMessage());
            inboundMonitor.failure(headers, e);
        } catch (final Exception e) {
            log.info("Failed to handle MQTT message: {}", e.getMessage());
            inboundMonitor.exception(headers, e);
        } finally {
            // TODO: cleanup LogUtil
        }
        return Optional.empty();
    }

    @Nullable
    private EnforcementFilter getEnforcementFilter(final String topic) {
        if (topicEnforcementFilterFactory != null) {
            return topicEnforcementFilterFactory.getFilter(topic);
        } else {
            return null;
        }
    }

    @SuppressWarnings("unused") // ignored for speed - method reference is much faster than lambda
    private void onClientConnected(final HiveMqtt3ClientActor.HiveMqttClientEvents connected) {
        log.debug("Client connected, going to subscribe.");
        final ActorRef sender = getSender();
        final ActorRef self = getSelf();
        this.unsubscribe()
                .exceptionally(throwable -> {
                    log.info("Ignoring unsubscription error and continuing to subscribe again.");
                    return null;
                })
                .thenCompose(this::subscribe)
                .whenComplete((ignored, throwable) -> {
                    if (throwable == null) {
                        sender.tell(new Status.Success("Successfully subscribed"), self);
                    } else {
                        sender.tell(new ImmutableConnectionFailure(null, throwable, "subscriptions"), self);
                    }
                });
    }

    @SuppressWarnings("unused") // ignored for speed - method reference is much faster than lambda
    private void onClientDisconnected(final HiveMqtt3ClientActor.HiveMqttClientEvents disconnected) {
        log.debug("Client disconnected, ignoring.");
        // TODO: currently we don't care about unsubscribing because we have cleanSession=true. Should we care? At least
        //  we cannot unsubscribe when already disconnected ...
//        this.unsubscribe();
    }

    @Nullable
    private Mqtt3Subscribe prepareSubscriptions(final Collection<String> topics) {
        if (topics.isEmpty()) {
            return null;
        }

        final Mqtt3SubscribeBuilder.Start subscribeBuilder = topics.stream()
                .map(topic -> Mqtt3Subscription.builder().topicFilter(topic).qos(DEFAULT_QOS).build())
                .collect(this.collectMqtt3Subscribption());
        if (subscribeBuilder instanceof Mqtt3SubscribeBuilder.Complete) {
            return ((Mqtt3SubscribeBuilder.Complete) subscribeBuilder).build();
        }
        return null;
    }

    private Collector<Mqtt3Subscription, Mqtt3SubscribeBuilder.Start, Mqtt3SubscribeBuilder.Start> collectMqtt3Subscribption() {
        return Collector.of(
                Mqtt3Subscribe::builder,
                Mqtt3SubscribeBuilderBase::addSubscription,
                (builder1, builder2) -> {throw new UnsupportedOperationException("parallel execution not allowed");});
    }

    @SuppressWarnings("unused") // ignored for speed - method reference is much faster than lambda
    private CompletableFuture<Mqtt3SubAck> subscribe(final Void unused) {
        if (mqtt3Subscribe != null) {
            final ActorRef self = self();
            final Iterable<String> localTopics = topics;
            return client.subscribe(mqtt3Subscribe, msg -> self.tell(msg, ActorRef.noSender()))
                    .whenComplete((mqtt3SubAck, throwable) -> {
                        if (throwable != null) {
                            // Handle failure to subscribe
                            log.error(throwable, "Error while subscribing to topics: <{}>", localTopics);
                        } else {
                            // Handle successful subscription, e.g. logging or incrementing a metric
                            log.info("Successfully subscribed to <{}>", localTopics);
                        }
                    });
        }
        return CompletableFuture.completedFuture(null);

    }

    private CompletableFuture<Void> unsubscribe() {
        if (null != mqtt3Subscribe) {
            return client.unsubscribe(Mqtt3Unsubscribe.builder().reverse(mqtt3Subscribe).build())
                    .whenComplete((unused, throwable) -> {
                        if (null == throwable) {
                            log.info("Successfully unsubscribed.");
                        } else {
                            log.warning("Error while unsubscribing from topics: {}", throwable);
                        }
                    });
        }
        return CompletableFuture.completedFuture(null);
    }

    @SuppressWarnings("unused") // ignored for speed - method reference is much faster than lambda
    private boolean isDryRun(final Object message) {
        return dryRun;
    }

}
