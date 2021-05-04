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

import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;

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
                    JsonSchemaVersion.V_2);


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
        final UnaryOperator<JsonObject> migrateSourceFilters = new MigrateSourceFilters();
        final UnaryOperator<JsonObject> migrateTopicActionSubjectFilters = new MigrateTopicActionSubjectFilters();
        final UnaryOperator<JsonObject> migrateAuthorizationContexts = new MigrateAuthorizationContexts();
        final UnaryOperator<JsonObject> migrateTargetHeaderMappings = new MigrateTargetHeaderMappings();

        return ConnectivityModelFactory.connectionFromJson(
                migrateAuthorizationContexts
                        .andThen(migrateSourceFilters)
                        .andThen(migrateTopicActionSubjectFilters)
                        .andThen(migrateTargetHeaderMappings)
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
     *         "input" : "{{ source:address }}",
     *         "filters" : [ "{{thing:id}}", "{{thing:name}}" ]
     *       }</b>
     *     },
     *     {}
     *   ]
     * }
     * </pre>
     */
    static class MigrateSourceFilters implements UnaryOperator<JsonObject> {

        @Override
        public JsonObject apply(final JsonObject connectionJsonObject) {
            final Optional<JsonArray> sources = connectionJsonObject.getValue(Connection.JsonFields.SOURCES);
            if (sources.isPresent() && needsSourceFilterMigration(sources.get())) {
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

        private static boolean needsSourceFilterMigration(final JsonArray sources) {
            return sources.stream()
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .anyMatch(source -> source.contains("filters"));
        }

        static JsonObject migrateSourceFilters(final JsonObject source) {
            return source.getValue(Enforcement.JsonFields.FILTERS)
                    .filter(JsonValue::isArray)
                    .map(JsonValue::asArray)
                    .map(a -> JsonFactory.newObjectBuilder()
                            .set(Enforcement.JsonFields.INPUT, ConnectivityModelFactory.SOURCE_ADDRESS_ENFORCEMENT)
                            .set(Enforcement.JsonFields.FILTERS, a)
                            .build())
                    .map(enforcement -> source.toBuilder()
                            .remove(Enforcement.JsonFields.FILTERS)
                            .set(Source.JsonFields.ENFORCEMENT, enforcement))
                    .map(JsonObjectBuilder::build)
                    .orElse(source);
        }
    }

    /**
     * Migrates occurring {@code topic:action|subject} placeholders in the whole Connection document to the changed
     * format {@code topic:action-subject}.
     */
    static class MigrateTopicActionSubjectFilters implements UnaryOperator<JsonObject> {

        private static final String OLD_TOPIC_ACTION_SUBJECT = "topic:action|subject";
        private static final String NEW_TOPIC_ACTION_SUBJECT = "topic:action-subject";

        @Override
        public JsonObject apply(final JsonObject connectionJsonObject) {

            final JsonObjectBuilder connectionObjectBuilder = connectionJsonObject.toBuilder();

            final Optional<JsonArray> sources = connectionJsonObject.getValue(Connection.JsonFields.SOURCES);
            if (sources.isPresent()) {
                final JsonArray sourcesArray = migrateSource(sources.get());
                connectionObjectBuilder.set(Connection.JsonFields.SOURCES, sourcesArray);
            }

            final Optional<JsonArray> targets = connectionJsonObject.getValue(Connection.JsonFields.TARGETS);
            if (targets.isPresent()) {
                final JsonArray targetsArray = migrateTarget(targets.get());
                connectionObjectBuilder.set(Connection.JsonFields.TARGETS, targetsArray);
            }

            if (sources.isPresent() || targets.isPresent()) {
                return connectionObjectBuilder.build();
            } else {
                return connectionJsonObject;
            }
        }

        private JsonArray migrateSource(final JsonArray sources) {
            return sources.stream()
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .map(o -> MigrateTopicActionSubjectFilters.migrateHeaderMapping(o, Source.JsonFields.HEADER_MAPPING))
                    .collect(JsonCollectors.valuesToArray());
        }

        private JsonArray migrateTarget(final JsonArray targets) {
            return targets.stream()
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .map(MigrateTopicActionSubjectFilters::migrateTargetAddress)
                    .map(o -> MigrateTopicActionSubjectFilters.migrateHeaderMapping(o, Target.JsonFields.HEADER_MAPPING))
                    .collect(JsonCollectors.valuesToArray());
        }

        static JsonObject migrateTargetAddress(final JsonObject target) {

            final Optional<String> address = target.getValue(Target.JsonFields.ADDRESS);
            return address
                    .filter(a -> a.contains(OLD_TOPIC_ACTION_SUBJECT))
                    .map(a -> a.replaceAll(Pattern.quote(OLD_TOPIC_ACTION_SUBJECT), NEW_TOPIC_ACTION_SUBJECT))
                    .map(a -> target.set(Target.JsonFields.ADDRESS, a))
                    .orElse(target);
        }

        static JsonObject migrateHeaderMapping(final JsonObject containsHeaderMapping,
                final JsonFieldDefinition<JsonObject> jsonFieldDefinition) {
            final Optional<JsonObject> headerMapping = containsHeaderMapping.getValue(jsonFieldDefinition);
            return headerMapping
                    .map(o -> o.stream()
                            .map(f -> {
                                if (f.getValue().isString() &&
                                        f.getValue().asString().contains(OLD_TOPIC_ACTION_SUBJECT)) {
                                    return JsonField.newInstance(f.getKey(), JsonValue.of(f.getValue().asString()
                                            .replaceAll(Pattern.quote(OLD_TOPIC_ACTION_SUBJECT),
                                                    NEW_TOPIC_ACTION_SUBJECT)));
                                } else {
                                    return f;
                                }
                            })
                    )
                    .map(a -> containsHeaderMapping.set(jsonFieldDefinition, a.collect(JsonCollectors.fieldsToObject()))
                    )
                    .orElse(containsHeaderMapping);
        }
    }

    static class MigrateAuthorizationContexts implements UnaryOperator<JsonObject> {

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

    static class MigrateTargetHeaderMappings implements UnaryOperator<JsonObject> {

        private static final JsonObject LEGACY_DEFAULT_TARGET_HEADER_MAPPING = JsonObject.newBuilder()
                .set("correlation-id", "{{header:correlation-id}}")
                .set("reply-to", "{{header:reply-to}}")
                .build();

        @Override
        public JsonObject apply(final JsonObject connectionJsonObject) {
            final boolean shouldSkipMigration = connectionJsonObject.getValue(Connection.JsonFields.CONNECTION_TYPE)
                    .flatMap(ConnectionType::forName)
                    .filter(ConnectionType.MQTT::equals)
                    .isPresent();
            if (!shouldSkipMigration && containsTargets(connectionJsonObject)) {
                final JsonArray migratedTargets = connectionJsonObject.getValue(Connection.JsonFields.TARGETS)
                        .map(MigrateTargetHeaderMappings::migrateTargets)
                        .orElse(null);
                return connectionJsonObject
                        .set(Connection.JsonFields.TARGETS, migratedTargets);
            }
            return connectionJsonObject;

        }

        private static boolean containsTargets(@Nonnull final JsonObject connectionJsonObject) {
            return connectionJsonObject.getValue(Connection.JsonFields.TARGETS)
                    .map(targets -> !targets.isEmpty())
                    .orElse(Boolean.FALSE);
        }

        static JsonArray migrateTargets(@Nonnull final JsonArray targetsJsonArray) {
            return targetsJsonArray.stream()
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .map(targetJson -> {
                        if (!targetJson.contains(Target.JsonFields.HEADER_MAPPING.getPointer())) {
                            return targetJson
                                    .set(Target.JsonFields.HEADER_MAPPING, LEGACY_DEFAULT_TARGET_HEADER_MAPPING);
                        }
                        return targetJson;
                    })
                    .collect(JsonCollectors.valuesToArray());
        }
    }
}
