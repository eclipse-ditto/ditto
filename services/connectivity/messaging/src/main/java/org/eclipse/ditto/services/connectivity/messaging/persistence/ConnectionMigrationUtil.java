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

        final Optional<JsonArray> globalAuthorizationContext = connectionJsonObject.getValue(AUTHORIZATION_CONTEXT);
        if (globalAuthorizationContext.isPresent()) {
            // in the old, deprecated format, use the global authorization context as sources and targets contexts
            final JsonObjectBuilder migrationConnectionBuilder = connectionJsonObject.toBuilder();
            final JsonArray authContext = globalAuthorizationContext.get();

            ConnectionMigrationUtil.migrateSources(connectionJsonObject, authContext)
                    .ifPresent(s -> migrationConnectionBuilder.set(Connection.JsonFields.SOURCES, s));
            ConnectionMigrationUtil.migrateTargets(connectionJsonObject, authContext)
                    .ifPresent(t -> migrationConnectionBuilder.set(Connection.JsonFields.TARGETS, t));

            final JsonObject migratedJsonObject = migrationConnectionBuilder.build();
            return ConnectivityModelFactory.connectionFromJson(migratedJsonObject);
        } else {
            // in the new format, simply parse the json:
            return ConnectivityModelFactory.connectionFromJson(connectionJsonObject);
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
