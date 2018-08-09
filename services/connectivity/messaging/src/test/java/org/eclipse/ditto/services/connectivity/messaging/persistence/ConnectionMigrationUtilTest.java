/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.connectivity.messaging.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.connectivity.Topic.LIVE_COMMANDS;
import static org.eclipse.ditto.model.connectivity.Topic.LIVE_EVENTS;
import static org.eclipse.ditto.model.connectivity.Topic.LIVE_MESSAGES;
import static org.eclipse.ditto.model.connectivity.Topic.TWIN_EVENTS;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.junit.Test;

/**
 * Tests {@link ConnectionMigrationUtil}.
 */
public class ConnectionMigrationUtilTest {

    private static final String ID = UUID.randomUUID().toString();
    private static final String NAME = "my-connection";
    private static final ConnectionType TYPE = ConnectionType.AMQP_10;
    private static final ConnectionStatus STATUS = ConnectionStatus.OPEN;
    private static final String URI = "amqps://foo:bar@example.com:443";

    private static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            AuthorizationSubject.newInstance("mySolutionId:mySubject"));

    private static final AuthorizationContext ALT_AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            AuthorizationSubject.newInstance("altSolution:customSubject"));

    private static final JsonObject SOURCE1_JSON = JsonObject
            .newBuilder()
            .set(Source.JsonFields.ADDRESSES, JsonFactory.newArrayBuilder().add("amqp/source1").build())
            .set(Source.JsonFields.CONSUMER_COUNT, 2)
            .build();
    private static final JsonObject SOURCE2_JSON = JsonObject
            .newBuilder()
            .set(Source.JsonFields.ADDRESSES, JsonFactory.newArrayBuilder().add("amqp/source2").build())
            .set(Source.JsonFields.CONSUMER_COUNT, 1)
            .build();
    private static final JsonArray OLD_SOURCES_JSON = JsonArray.newBuilder().add(SOURCE1_JSON, SOURCE2_JSON).build();

    private static final JsonObject TARGET1_JSON = JsonObject
            .newBuilder()
            .set(Target.JsonFields.TOPICS, JsonFactory.newArrayBuilder()
                    .add(TWIN_EVENTS.getName())
                    .add(LIVE_EVENTS.getName())
                    .build()
            )
            .set(Target.JsonFields.ADDRESS, "amqp/target1")
            .build();
    private static final JsonObject TARGET2_JSON = JsonObject
            .newBuilder()
            .set(Target.JsonFields.TOPICS, JsonFactory.newArrayBuilder()
                    .add(LIVE_MESSAGES.getName())
                    .add(LIVE_MESSAGES.getName())
                    .add(LIVE_EVENTS.getName())
                    .build()
            )
            .set(Target.JsonFields.ADDRESS, "amqp/target2")
            .build();
    private static final JsonObject TARGET3_JSON = JsonObject
            .newBuilder()
            .set(Target.JsonFields.TOPICS, JsonFactory.newArrayBuilder()
                    .add(LIVE_MESSAGES.getName())
                    .add(LIVE_MESSAGES.getName())
                    .add(LIVE_COMMANDS.getName())
                    .build()
            )
            .set(Target.JsonFields.ADDRESS, "amqp/target3")
            .build();
    private static final JsonArray OLD_TARGETS_JSON =
            JsonArray.newBuilder().add(TARGET1_JSON, TARGET2_JSON, TARGET3_JSON).build();

    private static final Set<String> KNOWN_TAGS = Collections.singleton("HONO");

    private static final JsonObject KNOWN_CONNECTION_JSON = JsonObject.newBuilder()
            .set(Connection.JsonFields.ID, ID)
            .set(Connection.JsonFields.NAME, NAME)
            .set(ConnectionMigrationUtil.AUTHORIZATION_CONTEXT, AUTHORIZATION_CONTEXT.getAuthorizationSubjects()
                    .stream()
                    .map(AuthorizationSubject::getId)
                    .map(JsonValue::of)
                    .collect(JsonCollectors.valuesToArray())
            )
            .set(Connection.JsonFields.CONNECTION_TYPE, TYPE.getName())
            .set(Connection.JsonFields.CONNECTION_STATUS, STATUS.getName())
            .set(Connection.JsonFields.URI, URI)
            .set(Connection.JsonFields.SOURCES, OLD_SOURCES_JSON)
            .set(Connection.JsonFields.TARGETS, OLD_TARGETS_JSON)
            .set(Connection.JsonFields.CLIENT_COUNT, 2)
            .set(Connection.JsonFields.FAILOVER_ENABLED, true)
            .set(Connection.JsonFields.VALIDATE_CERTIFICATES, true)
            .set(Connection.JsonFields.PROCESSOR_POOL_SIZE, 5)
            .set(Connection.JsonFields.TAGS, KNOWN_TAGS.stream()
                    .map(JsonFactory::newValue)
                    .collect(JsonCollectors.valuesToArray()))
            .build();

    @Test
    public void migrateConnectionWithGlobalAuthorizationContext() {

        final Connection migratedConnection = ConnectionMigrationUtil.connectionFromJsonWithMigration(KNOWN_CONNECTION_JSON);
        assertThat(migratedConnection.getId()).isEqualTo(ID);
        assertThat(migratedConnection.getName()).contains(NAME);
        assertThat((Object) migratedConnection.getConnectionType()).isEqualTo(TYPE);
        assertThat(migratedConnection.getUri()).isEqualTo(URI);
        migratedConnection.getSources().forEach(source ->
                assertThat(source.getAuthorizationContext()).isEqualTo(AUTHORIZATION_CONTEXT));
        migratedConnection.getTargets().forEach(target ->
                assertThat(target.getAuthorizationContext()).isEqualTo(AUTHORIZATION_CONTEXT));
    }

    @Test
    public void migrateConnectionContainingSourcesAndTargetsWithOwnAuthContextWithGlobalAuthContext() {

        final JsonObjectBuilder builder = KNOWN_CONNECTION_JSON.toBuilder();
        builder.set(Connection.JsonFields.SOURCES,
                ConnectionMigrationUtil.migrateSources(KNOWN_CONNECTION_JSON, ALT_AUTHORIZATION_CONTEXT
                        .getAuthorizationSubjectIds()
                        .stream()
                        .map(JsonValue::of)
                        .collect(JsonCollectors.valuesToArray())).get());
        builder.set(Connection.JsonFields.TARGETS,
                ConnectionMigrationUtil.migrateTargets(KNOWN_CONNECTION_JSON, ALT_AUTHORIZATION_CONTEXT
                        .getAuthorizationSubjectIds()
                        .stream()
                        .map(JsonValue::of)
                        .collect(JsonCollectors.valuesToArray())).get());
        final Connection migratedConnection = ConnectionMigrationUtil.connectionFromJsonWithMigration(builder.build());

        assertThat(migratedConnection.getId()).isEqualTo(ID);
        assertThat(migratedConnection.getName()).contains(NAME);
        assertThat((Object) migratedConnection.getConnectionType()).isEqualTo(TYPE);
        assertThat(migratedConnection.getUri()).isEqualTo(URI);
        migratedConnection.getSources().forEach(source ->
                assertThat(source.getAuthorizationContext()).isEqualTo(ALT_AUTHORIZATION_CONTEXT));
        migratedConnection.getTargets().forEach(target ->
                assertThat(target.getAuthorizationContext()).isEqualTo(ALT_AUTHORIZATION_CONTEXT));
    }
}
