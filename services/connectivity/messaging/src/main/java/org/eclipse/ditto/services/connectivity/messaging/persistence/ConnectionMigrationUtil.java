/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.persistence;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;

/**
 * Utility for migrating already persisted {@link Connection} JSONs to the current JSON structure.
 */
final class ConnectionMigrationUtil {

    /**
     * JSON field containing the {@code Connection} authorization context (list of authorization subjects).
     * <br />
     * Was removed from the {@code Connection} and already persisted connections must be adapted accordingly.
     */
    static final JsonFieldDefinition<JsonArray> AUTHORIZATION_CONTEXT =
            JsonFactory.newJsonArrayFieldDefinition("authorizationContext", FieldType.REGULAR,
                    JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);


    private ConnectionMigrationUtil() {
        throw new AssertionError();
    }

    /**
     * Migrates the passed in {@code connectionJsonObject} into the current {@link Connection} structure.
     * <br />
     * If the {@code connectionJsonObject} is already in the current structure, simply build a {@link Connection}
     * instance from it.
     *
     * @param connectionJsonObject the Connection JSON to parse
     * @return the (potentially migrated) Connection instance
     */
    static Connection connectionFromJsonWithMigration(@Nonnull final JsonObject connectionJsonObject) {
        final Function<JsonObject, JsonObject> migrateSourceFilters = new MigrateSourceFilters();
        final Function<JsonObject, JsonObject> migrateAuthorizationContexts = new MigrateAuthorizationContexts();

        return ConnectivityModelFactory.connectionFromJson(
                migrateAuthorizationContexts
                        .andThen(migrateSourceFilters)
                        .apply(connectionJsonObject));
    }

    /**
     * Old JSON format:
     * <pre>
     * {
     *  ...
     *  "sources":[
     *    {
     *      "addresses" : ["source1"],
     *      "authorizationContext" : [...],
     *      <b>"filters" : ["{{thing:id}}", "{{thing:name}}"]</b>
     *    },
     *    {}
     *  ]
     * }
     * </pre>
     * is migrated to:
     * <pre>
     * {
     *   ...
     *   "sources":[
     *     {
     *       "addresses" : ["source1"],
     *       "authorizationContext" : [...],
     *       <b>"enforcement" : {
     *         "input" : "{{ mqtt:topic }}",
     *         "filters" : [ "{{thing:id}}", "{{thing:name}}" ]
     *       }</b>
     *     },
     *     {}
     *   ]
     * }
     * </pre>
     */
    static class MigrateSourceFilters implements Function<JsonObject, JsonObject> {

        public static final String DEFAULT_MQTT_SOURCE_FILTER_INPUT = "{{ mqtt:topic }}";

        @Override
        public JsonObject apply(final JsonObject connectionJsonObject) {
            final Optional<JsonArray> sources = connectionJsonObject.getValue(Connection.JsonFields.SOURCES);
            if (sources.isPresent() && needsSourceFilterMigration(connectionJsonObject)) {
                final JsonArray sourcesArray = sources.get().stream()
                        .filter(JsonValue::isObject)
                        .map(JsonValue::asObject)
                        .map(MigrateSourceFilters::migrateSourceFilters)
                        .collect(JsonCollectors.valuesToArray());
                return connectionJsonObject.toBuilder().set(Connection.JsonFields.SOURCES, sourcesArray).build();
            } else {
                return connectionJsonObject;
            }
        }

        private static boolean needsSourceFilterMigration(final JsonObject connectionJsonObject) {
            final Optional<JsonArray> sources = connectionJsonObject.getValue(Connection.JsonFields.SOURCES);
            return sources.isPresent() && sources.get().stream().filter(JsonValue::isObject)
                    .map(JsonValue::asObject).anyMatch(source -> source.contains("filters"));
        }

        static JsonObject migrateSourceFilters(final JsonObject source) {
            return source.getValue(Enforcement.JsonFields.FILTERS)
                    .filter(JsonValue::isArray)
                    .map(JsonValue::asArray)
                    .map(a -> JsonFactory.newObjectBuilder()
                            .set(Enforcement.JsonFields.INPUT, DEFAULT_MQTT_SOURCE_FILTER_INPUT)
                            .set(Enforcement.JsonFields.FILTERS, a)
                            .build())
                    .map(enforcement -> source.toBuilder()
                            .remove(Enforcement.JsonFields.FILTERS)
                            .set(Source.JsonFields.ENFORCEMENT, enforcement))
                    .map(JsonObjectBuilder::build)
                    .orElse(source);
        }
    }

    static class MigrateAuthorizationContexts implements Function<JsonObject, JsonObject> {

        @Override
        public JsonObject apply(final JsonObject connectionJsonObject) {
            final Optional<JsonArray> globalAuthorizationContext = connectionJsonObject.getValue(AUTHORIZATION_CONTEXT);
            if (globalAuthorizationContext.isPresent()) {
                // in the old, deprecated format, use the global authorization context as sources and targets contexts
                final JsonObjectBuilder migrationConnectionBuilder = connectionJsonObject.toBuilder();
                final JsonArray authContext = globalAuthorizationContext.get();

                MigrateAuthorizationContexts.migrateSources(connectionJsonObject, authContext)
                        .ifPresent(s -> migrationConnectionBuilder.set(Connection.JsonFields.SOURCES, s));
                MigrateAuthorizationContexts.migrateTargets(connectionJsonObject, authContext)
                        .ifPresent(t -> migrationConnectionBuilder.set(Connection.JsonFields.TARGETS, t));


                return migrationConnectionBuilder.build();
            } else {
                // no migration required
                return connectionJsonObject;
            }
        }

        static Optional<JsonArray> migrateSources(@Nonnull final JsonObject connectionJsonObject,
                final JsonArray authContext) {
            return connectionJsonObject.getValue(Connection.JsonFields.SOURCES)
                    .map(sourcesArray -> IntStream.range(0, sourcesArray.getSize())
                            .mapToObj(index -> sourcesArray.get(index)
                                    .filter(JsonValue::isObject)
                                    .map(JsonValue::asObject)
                                    .map(object -> {
                                        if (object.contains(Source.JsonFields.AUTHORIZATION_CONTEXT.getPointer())) {
                                            // keep the authContext if it was already set in the target
                                            return object;
                                        } else {
                                            return object.set(Source.JsonFields.AUTHORIZATION_CONTEXT, authContext);
                                        }
                                    })
                                    .orElse(null)
                            ).collect(JsonCollectors.valuesToArray())
                    );
        }

        static Optional<JsonArray> migrateTargets(@Nonnull final JsonObject connectionJsonObject,
                final JsonArray authContext) {
            return connectionJsonObject.getValue(Connection.JsonFields.TARGETS)
                    .map(targetsArray -> IntStream.range(0, targetsArray.getSize())
                            .mapToObj(index -> targetsArray.get(index)
                                    .filter(JsonValue::isObject)
                                    .map(JsonValue::asObject)
                                    .map(object -> {
                                        if (object.contains(Target.JsonFields.AUTHORIZATION_CONTEXT.getPointer())) {
                                            // keep the authContext if it was already set in the target
                                            return object;
                                        } else {
                                            return object.set(Target.JsonFields.AUTHORIZATION_CONTEXT, authContext);
                                        }
                                    })
                                    .orElse(null)
                            ).collect(JsonCollectors.valuesToArray())
                    );
        }
    }
}
