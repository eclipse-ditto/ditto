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
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.HonoAddressAlias;
import org.eclipse.ditto.connectivity.model.ReplyTarget;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.messaging.amqp.AmqpClientActor;
import org.eclipse.ditto.connectivity.service.messaging.httppush.HttpPushClientActor;
import org.eclipse.ditto.connectivity.service.messaging.kafka.KafkaClientActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.HiveMqtt3ClientActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.HiveMqtt5ClientActor;
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

        final ConnectionType connectionType = connection.getConnectionType();
        final Props result;
        switch (connectionType) {
            case AMQP_091:
                result = RabbitMQClientActor.props(connection, proxyActor, connectionActor, dittoHeaders,
                        connectivityConfigOverwrites);
                break;
            case AMQP_10:
                result = AmqpClientActor.props(connection, proxyActor, connectionActor, connectivityConfigOverwrites,
                        actorSystem, dittoHeaders);
                break;
            case MQTT:
                result = HiveMqtt3ClientActor.props(connection, proxyActor, connectionActor, dittoHeaders,
                        connectivityConfigOverwrites);
                break;
            case MQTT_5:
                result = HiveMqtt5ClientActor.props(connection, proxyActor, connectionActor, dittoHeaders,
                        connectivityConfigOverwrites);
                break;
            case KAFKA:
                result = KafkaClientActor.props(connection, proxyActor, connectionActor, dittoHeaders,
                        connectivityConfigOverwrites);
                break;
            case HONO:
                result = KafkaClientActor.props(getEnrichedConnection(actorSystem, connection),
                        proxyActor, connectionActor, dittoHeaders, connectivityConfigOverwrites);
                break;
            case HTTP_PUSH:
                result = HttpPushClientActor.props(connection, proxyActor, connectionActor, dittoHeaders,
                        connectivityConfigOverwrites);
                break;
            default:
                throw new IllegalArgumentException("ConnectionType <" + connectionType + "> is not supported.");
        }
        return result;
    }

    private Connection getEnrichedConnection(final ActorSystem actorSystem, final Connection connection) {
        var honoConfig = HonoConfig.get(actorSystem);
        final ConnectionId connectionId = connection.getId();
        return ConnectivityModelFactory.newConnectionBuilder(
                        connection.getId(),
                        connection.getConnectionType(),
                        connection.getConnectionStatus(),
                        honoConfig.getBaseUri())
                .validateCertificate(honoConfig.getValidateCertificates())
                .specificConfig(Map.of(
                        "saslMechanism", honoConfig.getSaslMechanism().getValue(),
                        "bootstrapServers", honoConfig.getBootstrapServers(),
                        "groupId", honoConfig.getTenantId(connectionId) + "_" + connectionId))
                .credentials(honoConfig.getCredentials(connectionId))
                .sources(connection.getSources()
                        .stream()
                        .map(source -> ConnectivityModelFactory.sourceFromJson(
                                resolveSourceAliases2(source, honoConfig.getTenantId(connectionId)), 1))
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
        source.getReplyTarget().ifPresent(replyTarget ->
                Optional.of(replyTarget.getAddress()).ifPresent(address -> {
                    final JsonObjectBuilder replyTargetBuilder = JsonFactory.newObjectBuilder(replyTarget.toJson())
                            .set(ReplyTarget.JsonFields.ADDRESS, HonoAddressAlias.resolve(address, tenantId, true));
                    Optional.of(replyTarget.getHeaderMapping()).ifPresent(mapping -> {
                        Map<String, String> newMapping = mapping.getMapping();
                        switch (HonoAddressAlias.fromName(address)) {
                            case COMMAND -> {
                                newMapping.put("device_id", "{{ thing:id }}");
                                newMapping.put("subject",
                                        "{{ header:subject | fn:default(topic:action-subject) | fn:default(topic:criterion) }}-response");
                            }
                            case COMMAND_RESPONSE -> newMapping.put("status", "{{ header:status }}");
                        }
                        newMapping.put("correlation-id", "{{ header:correlation-id }}");
                        replyTargetBuilder.set(ReplyTarget.JsonFields.HEADER_MAPPING,
                                ConnectivityModelFactory.newHeaderMapping(newMapping).toJson());
                    });
                    sourceBuilder.set(Source.JsonFields.REPLY_TARGET, replyTargetBuilder.build());
                }));
        return sourceBuilder.build();
    }

    private JsonObject resolveSourceAliases2(final Source source, String tenantId) {
        JsonObjectBuilder sourceBuilder = JsonFactory.newObjectBuilder(source.toJson())
                .set(Source.JsonFields.ADDRESSES, JsonArray.of(source.getAddresses().stream()
                        .map(address -> HonoAddressAlias.resolve(address, tenantId))
                        .map(JsonValue::of)
                        .toList()));
        source.getReplyTarget().ifPresent(replyTarget -> {
            sourceBuilder.set("replyTarget/address", HonoAddressAlias.resolve(replyTarget.getAddress(), tenantId, true));
            switch (HonoAddressAlias.fromName(replyTarget.getAddress())) {
                case COMMAND -> {
                    sourceBuilder.set("replyTarget/headerMapping/device_id", "{{ thing:id }}");
                    sourceBuilder.set("replyTarget/headerMapping/subject",
                            "{{ header:subject | fn:default(topic:action-subject) | fn:default(topic:criterion) }}-response");
                }
                case COMMAND_RESPONSE -> sourceBuilder.set("replyTarget/headerMapping/status", "{{ header:status }}");
            }
            sourceBuilder.set("replyTarget/headerMapping/correlation-id", "{{ header:correlation-id }}");
        });
        return sourceBuilder.build();
    }

    private JsonObject resolveTargetAlias(final Target target, String tenantId) {
        JsonObjectBuilder targetBuilder = JsonFactory.newObjectBuilder(target.toJson())
                .set(Target.JsonFields.ADDRESS, Optional.of(target.getAddress())
                        .map(address -> HonoAddressAlias.resolve(address, tenantId, true))
                        .orElse(null), jsonField -> !target.getAddress().isEmpty());
        return targetBuilder.build();
    }

}
