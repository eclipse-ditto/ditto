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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.connectivity.model.Topic.LIVE_COMMANDS;
import static org.eclipse.ditto.connectivity.model.Topic.LIVE_EVENTS;
import static org.eclipse.ditto.connectivity.model.Topic.LIVE_MESSAGES;
import static org.eclipse.ditto.connectivity.model.Topic.TWIN_EVENTS;

import java.util.Collections;
import java.util.Set;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.junit.Test;

/**
 * Tests {@link ConnectionMigrationUtil}.
 */
public class ConnectionMigrationUtilTest {

    private static final ConnectionId ID = ConnectionId.generateRandom();
    private static final String NAME = "my-connection";
    private static final ConnectionType TYPE = ConnectionType.AMQP_10;
    private static final ConnectivityStatus STATUS = ConnectivityStatus.OPEN;
    private static final String URI = "amqps://foo:bar@example.com:443";
    private static final String LEGACY_FIELD_FILTERS = "filters";

    private static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION, AuthorizationSubject.newInstance("myIssuer:mySubject"));

    private static final AuthorizationContext ALT_AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION, AuthorizationSubject.newInstance("myAltIssuer:customSubject"));

    private static final JsonArray FILTERS =
            JsonFactory.newArrayBuilder().add("{{thing:id}}").add("{{thing:name}}").build();
    private static final JsonObject SOURCE1_JSON = JsonObject
            .newBuilder()
            .set(Source.JsonFields.ADDRESSES, JsonFactory.newArrayBuilder().add("amqp/source1").build())
            .set(Source.JsonFields.CONSUMER_COUNT, 2)
            .set(LEGACY_FIELD_FILTERS, FILTERS)
            .build();
    private static final JsonObject SOURCE2_JSON = JsonObject
            .newBuilder()
            .set(Source.JsonFields.ADDRESSES, JsonFactory.newArrayBuilder().add("amqp/source2").build())
            .set(Source.JsonFields.CONSUMER_COUNT, 1)
            .set(Source.JsonFields.HEADER_MAPPING, JsonObject.newBuilder()
                    .set("source-action", JsonValue.of("source/{{ topic:action }}"))
                    .set("source-subject", JsonValue.of("source/{{topic:action|subject }}"))
                    .set("source-subject-next-gen", JsonValue.of("source/{{    topic:action-subject }}"))
                    .set("source-some-header", JsonValue.of("source/{{ topic:full | fn:substring-before('/') }}"))
                    .build()
            )
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
            .set(Target.JsonFields.HEADER_MAPPING, JsonObject.newBuilder()
                    .set("target-action", JsonValue.of("target/{{ topic:action }}"))
                    .set("target-subject", JsonValue.of("target/{{topic:action|subject }}"))
                    .set("target-subject-next-gen", JsonValue.of("target/{{    topic:action-subject }}"))
                    .set("target-some-header", JsonValue.of("target/{{ topic:full | fn:substring-before('/') }}"))
                    .build()
            )
            .build();
    private static final JsonObject TARGET3_JSON = JsonObject
            .newBuilder()
            .set(Target.JsonFields.TOPICS, JsonFactory.newArrayBuilder()
                    .add(LIVE_MESSAGES.getName())
                    .add(LIVE_MESSAGES.getName())
                    .add(LIVE_COMMANDS.getName())
                    .build()
            )
            .set(Target.JsonFields.ADDRESS, "amqp/target3/{{topic:action|subject}}")
            .build();
    private static final JsonArray OLD_TARGETS_JSON =
            JsonArray.newBuilder().add(TARGET1_JSON, TARGET2_JSON, TARGET3_JSON).build();

    private static final Set<String> KNOWN_TAGS = Collections.singleton("HONO");

    private static final JsonObject KNOWN_CONNECTION_JSON = JsonObject.newBuilder()
            .set(Connection.JsonFields.ID, ID.toString())
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
    private static final HeaderMapping LEGACY_TARGET_HEADER_MAPPING =
            ConnectivityModelFactory.newHeaderMapping(JsonObject.newBuilder()
                    .set("correlation-id", "{{header:correlation-id}}")
                    .set("reply-to", "{{header:reply-to}}")
                    .build());

    @Test
    public void migrateConnectionWithGlobalAuthorizationContext() {

        final Connection migratedConnection =
                ConnectionMigrationUtil.connectionFromJsonWithMigration(KNOWN_CONNECTION_JSON);
        assertThat((CharSequence) migratedConnection.getId()).isEqualTo(ID);
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
                ConnectionMigrationUtil.MigrateAuthorizationContexts.migrateSources(KNOWN_CONNECTION_JSON, ALT_AUTHORIZATION_CONTEXT
                        .getAuthorizationSubjectIds()
                        .stream()
                        .map(JsonValue::of)
                        .collect(JsonCollectors.valuesToArray())).get());
        builder.set(Connection.JsonFields.TARGETS,
                ConnectionMigrationUtil.MigrateAuthorizationContexts.migrateTargets(KNOWN_CONNECTION_JSON, ALT_AUTHORIZATION_CONTEXT
                        .getAuthorizationSubjectIds()
                        .stream()
                        .map(JsonValue::of)
                        .collect(JsonCollectors.valuesToArray())).get());
        final Connection migratedConnection = ConnectionMigrationUtil.connectionFromJsonWithMigration(builder.build());

        assertThat((CharSequence) migratedConnection.getId()).isEqualTo(ID);
        assertThat(migratedConnection.getName()).contains(NAME);
        assertThat((Object) migratedConnection.getConnectionType()).isEqualTo(TYPE);
        assertThat(migratedConnection.getUri()).isEqualTo(URI);
        migratedConnection.getSources().forEach(source ->
                assertThat(source.getAuthorizationContext()).isEqualTo(ALT_AUTHORIZATION_CONTEXT));
        migratedConnection.getTargets().forEach(target ->
                assertThat(target.getAuthorizationContext()).isEqualTo(ALT_AUTHORIZATION_CONTEXT));
    }

    @Test
    public void migrateSourceWithFilters() {
        final Connection migratedConnection =
                ConnectionMigrationUtil.connectionFromJsonWithMigration(KNOWN_CONNECTION_JSON);
        assertThat(migratedConnection.getSources()).hasSize(2);
        assertThat(migratedConnection.getSources().get(0).getEnforcement()).isPresent();
        // the second source had no filters
        assertThat(migratedConnection.getSources().get(1).getEnforcement()).isEmpty();
        final Enforcement enforcement = migratedConnection.getSources().get(0).getEnforcement().get();
        // the filters field was implicitly matched against the mqtt topic
        assertThat(enforcement.getInput()).isEqualTo("{{ source:address }}");
        assertThat(enforcement.getFilters()).hasSize(2);
        assertThat(enforcement.getFilters()).contains("{{thing:id}}", "{{thing:name}}");
    }

    @Test
    public void migrateSourcesWithoutFiltersJsonIsNotTouched() {
        final JsonObject noFilters = KNOWN_CONNECTION_JSON.toBuilder().set(Connection.JsonFields.SOURCES,
                JsonArray.newBuilder().add(SOURCE1_JSON.toBuilder().remove(LEGACY_FIELD_FILTERS).build(),
                        SOURCE2_JSON).build()).build();
        assertThat(ConnectionMigrationUtil.MigrateSourceFilters.migrateSourceFilters(noFilters)).isSameAs(noFilters);
    }

    @Test
    public void migratePlaceholderTopicActionSubject() {
        final Connection migratedConnection =
                ConnectionMigrationUtil.connectionFromJsonWithMigration(KNOWN_CONNECTION_JSON);

        assertThat(migratedConnection.getSources().get(1).getHeaderMapping().getMapping()).isNotEmpty();
        final HeaderMapping sourceHeaderMapping = migratedConnection.getSources().get(1).getHeaderMapping();
        assertThat(sourceHeaderMapping.getMapping().get("source-action"))
                .isEqualTo("source/{{ topic:action }}");
        assertThat(sourceHeaderMapping.getMapping().get("source-subject"))
                .isEqualTo("source/{{topic:action-subject }}");
        assertThat(sourceHeaderMapping.getMapping().get("source-subject-next-gen"))
                .isEqualTo("source/{{    topic:action-subject }}");
        assertThat(sourceHeaderMapping.getMapping().get("source-some-header"))
                .isEqualTo("source/{{ topic:full | fn:substring-before('/') }}");

        assertThat(migratedConnection.getTargets().get(1).getHeaderMapping().getMapping()).isNotEmpty();
        final HeaderMapping targetHeaderMapping = migratedConnection.getTargets().get(1).getHeaderMapping();
        assertThat(targetHeaderMapping.getMapping().get("target-action"))
                .isEqualTo("target/{{ topic:action }}");
        assertThat(targetHeaderMapping.getMapping().get("target-subject"))
                .isEqualTo("target/{{topic:action-subject }}");
        assertThat(targetHeaderMapping.getMapping().get("target-subject-next-gen"))
                .isEqualTo("target/{{    topic:action-subject }}");
        assertThat(targetHeaderMapping.getMapping().get("target-some-header"))
                .isEqualTo("target/{{ topic:full | fn:substring-before('/') }}");

        assertThat(migratedConnection.getTargets().get(2).getAddress())
                .isEqualTo("amqp/target3/{{topic:action-subject}}");
    }

    @Test
    public void migrateMissingHeaderMappingForTargets() {
        final JsonObject connectionJsonWithoutHeaderMapping = KNOWN_CONNECTION_JSON.toBuilder()
                .set(Connection.JsonFields.TARGETS, JsonArray.of(
                        TARGET1_JSON.remove(Target.JsonFields.HEADER_MAPPING.getPointer())
                ))
                .build();
        final Connection migratedConnection =
                ConnectionMigrationUtil.connectionFromJsonWithMigration(connectionJsonWithoutHeaderMapping);

        migratedConnection.getTargets()
                .forEach(target -> assertThat(target.getHeaderMapping()).isEqualTo(LEGACY_TARGET_HEADER_MAPPING));

    }

}
