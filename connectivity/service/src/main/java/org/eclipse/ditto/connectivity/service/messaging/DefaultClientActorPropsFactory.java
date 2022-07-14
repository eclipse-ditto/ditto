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

import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.HonoConfig;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.HonoAddressAlias;
import org.eclipse.ditto.connectivity.model.ImmutableHeaderMapping;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.service.messaging.amqp.AmqpClientActor;
import org.eclipse.ditto.connectivity.service.messaging.httppush.HttpPushClientActor;
import org.eclipse.ditto.connectivity.service.messaging.kafka.KafkaClientActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.MqttClientActor;
import org.eclipse.ditto.connectivity.service.messaging.rabbitmq.RabbitMQClientActor;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * The default implementation of {@link ClientActorPropsFactory}. Singleton which is created just once
 * and otherwise returns the already created instance.
 */
@Immutable
public final class DefaultClientActorPropsFactory implements ClientActorPropsFactory {

    @Nullable private static DefaultClientActorPropsFactory instance;

    private DefaultClientActorPropsFactory() {}

    /**
     * Returns an instance of {@code DefaultClientActorPropsFactory}. Creates a new one if not already done.
     *
     * @return the factory instance.
     */
    public static DefaultClientActorPropsFactory getInstance() {
        if (null == instance) {
            instance = new DefaultClientActorPropsFactory();
        }
        return instance;
    }

    @Override
    public Props getActorPropsForType(final Connection connection, final ActorRef proxyActor,
            final ActorRef connectionActor,
            final ActorSystem actorSystem,
            final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {

        return switch (connection.getConnectionType()) {
            case AMQP_091 -> RabbitMQClientActor.props(connection,
                    proxyActor,
                    connectionActor,
                    dittoHeaders,
                    connectivityConfigOverwrites);
            case AMQP_10 -> AmqpClientActor.props(connection,
                    proxyActor,
                    connectionActor,
                    connectivityConfigOverwrites,
                    actorSystem,
                    dittoHeaders);
            case MQTT, MQTT_5 -> MqttClientActor.props(connection,
                    proxyActor,
                    connectionActor,
                    dittoHeaders,
                    connectivityConfigOverwrites);
            case KAFKA -> KafkaClientActor.props(connection,
                    proxyActor,
                    connectionActor,
                    dittoHeaders,
                    connectivityConfigOverwrites);
            case HTTP_PUSH -> HttpPushClientActor.props(connection,
                    proxyActor,
                    connectionActor,
                    dittoHeaders,
                    connectivityConfigOverwrites);
            case HONO ->
                KafkaClientActor.props(getEnrichedConnection(actorSystem, connection),
                        proxyActor, connectionActor, dittoHeaders, connectivityConfigOverwrites);
        };
    }

    private Connection getEnrichedConnection(final ActorSystem actorSystem, final Connection connection) {
        var honoConfig = HonoConfig.get(actorSystem);
        final ConnectionId connectionId = connection.getId();
        return ConnectivityModelFactory.newConnectionBuilder(
                        connection.getId(),
                        connection.getConnectionType(),
                        connection.getConnectionStatus(),
                        honoConfig.getBaseUri().toString())
                .validateCertificate(honoConfig.isValidateCertificates())
                .specificConfig(Map.of(
                        "saslMechanism", honoConfig.getSaslMechanism().getValue(),
                        "bootstrapServers", honoConfig.getBootstrapServers(),
                        "groupId", honoConfig.getTenantId(connectionId) + "_" + connectionId))
                .credentials(honoConfig.getCredentials(connectionId))
                .sources(connection.getSources()
                        .stream()
                        .map(source -> ConnectivityModelFactory.sourceFromJson(
                                resolveSourceAliases(source, honoConfig.getTenantId(connectionId)), 1))
                        .toList())
                .targets(connection.getTargets()
                        .stream()
                        .map(target -> ConnectivityModelFactory.targetFromJson(
                                resolveTargetAlias(target, honoConfig.getTenantId(connectionId))))
                        .toList())
                .build();
    }

    private JsonObject resolveSourceAliases(final Source source, String tenantId) {
        JsonObjectBuilder sourceBuilder = JsonFactory.newObjectBuilder(source.toJson())
                .set(Source.JsonFields.ADDRESSES, JsonArray.of(source.getAddresses().stream()
                        .map(address -> HonoAddressAlias.resolve(address, tenantId))
                        .map(JsonValue::of)
                        .toList()));
        source.getReplyTarget().ifPresent(replyTarget -> {
            var headerMapping = replyTarget.getHeaderMapping().toJson()
                    .setValue("correlation-id", "{{ header:correlation-id }}");
            if (HonoAddressAlias.COMMAND.getName().equals(replyTarget.getAddress())) {
                headerMapping = headerMapping
                        .setValue("device_id", "{{ thing:id }}")
                        .setValue("subject",
                                "{{ header:subject | fn:default(topic:action-subject) | fn:default(topic:criterion) }}-response");
            }
            sourceBuilder.set("replyTarget", replyTarget.toBuilder()
                    .address(HonoAddressAlias.resolve(replyTarget.getAddress(), tenantId, true))
                    .headerMapping(ImmutableHeaderMapping.fromJson(headerMapping))
                    .build().toJson());
        });
        if (source.getAddresses().contains(HonoAddressAlias.COMMAND_RESPONSE.getName())) {
            sourceBuilder.set("headerMapping", source.getHeaderMapping().toJson()
                    .setValue("correlation-id", "{{ header:correlation-id }}")
                    .setValue("status", "{{ header:status }}"));
        }
        return sourceBuilder.build();
    }

    private JsonObject resolveTargetAlias(final Target target, String tenantId) {
        JsonObjectBuilder targetBuilder = JsonFactory.newObjectBuilder(target.toJson())
                .set(Target.JsonFields.ADDRESS, Optional.of(target.getAddress())
                        .map(address -> HonoAddressAlias.resolve(address, tenantId, true))
                        .orElse(null), jsonField -> !jsonField.getValue().asString().isEmpty());
        var headerMapping = target.getHeaderMapping().toJson()
                .setValue("device_id", "{{ thing:id }}")
                .setValue("correlation-id", "{{ header:correlation-id }}")
                .setValue("subject", "{{ header:subject | fn:default(topic:action-subject) }}");
        if (target.getTopics().stream()
                .anyMatch(topic -> topic.getTopic() == Topic.LIVE_MESSAGES || topic.getTopic() == Topic.LIVE_COMMANDS)) {
            headerMapping.setValue("response-required", "{{ header:response-required }}");
        }
        targetBuilder.set("headerMapping", headerMapping);
        return targetBuilder.build();
    }

}
