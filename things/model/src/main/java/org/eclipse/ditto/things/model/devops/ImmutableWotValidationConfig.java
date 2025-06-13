/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.devops;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable value object representing a WoT validation configuration.
 * <p>
 * This class encapsulates all configuration options for WoT validation, including global enablement,
 * warning logging, Thing-level and Feature-level validation, and dynamic context-specific overrides.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
final class ImmutableWotValidationConfig implements WotValidationConfig {

    private static final JsonFieldDefinition<String> CONFIG_ID =
            JsonFactory.newStringFieldDefinition("configId", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> ENABLED_FIELD =
            JsonFactory.newBooleanFieldDefinition("enabled", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> LOG_WARNING_FIELD =
            JsonFactory.newBooleanFieldDefinition("logWarningInsteadOfFailingApiCalls", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<JsonObject> THING_FIELD =
            JsonFactory.newJsonObjectFieldDefinition("thing", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<JsonObject> FEATURE_FIELD =
            JsonFactory.newJsonObjectFieldDefinition("feature", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<JsonArray> DYNAMIC_CONFIG_FIELD =
            JsonFactory.newJsonArrayFieldDefinition("dynamicConfig", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final WotValidationConfigId configId;
    @Nullable private final Boolean enabled;
    @Nullable private final Boolean logWarningInsteadOfFailingApiCalls;
    @Nullable private final ThingValidationConfig thingConfig;
    @Nullable private final FeatureValidationConfig featureConfig;
    private final List<DynamicValidationConfig> dynamicConfig;
    @Nullable private final WotValidationConfigRevision revision;
    @Nullable private final Instant modified;
    @Nullable private final Instant created;
    @Nullable private final Boolean deleted;
    @Nullable private final Metadata metadata;

    private ImmutableWotValidationConfig(
            final WotValidationConfigId configId,
            @Nullable final Boolean enabled,
            @Nullable final Boolean logWarningInsteadOfFailingApiCalls,
            @Nullable final ThingValidationConfig thingConfig,
            @Nullable final FeatureValidationConfig featureConfig,
            final List<DynamicValidationConfig> dynamicConfig,
            @Nullable final WotValidationConfigRevision revision,
            @Nullable final Instant modified,
            @Nullable final Instant created,
            @Nullable final Boolean deleted,
            @Nullable final Metadata metadata) {
        this.configId = Objects.requireNonNull(configId, "configId");
        this.enabled = enabled;
        this.logWarningInsteadOfFailingApiCalls = logWarningInsteadOfFailingApiCalls;
        this.thingConfig = thingConfig;
        this.featureConfig = featureConfig;
        this.dynamicConfig = Collections.unmodifiableList(dynamicConfig);
        this.revision = revision;
        this.created = created;
        this.modified = modified;
        this.deleted = deleted;
        this.metadata = metadata;
    }

    /**
     * Creates a new instance of {@code ImmutableWotValidationConfig} with the specified values.
     *
     * @param configId the unique identifier of this configuration
     * @param enabled whether WoT validation is enabled globally
     * @param logWarningInsteadOfFailingApiCalls whether validation failures should only log warnings instead of failing
     * @param thingConfig optional configuration for Thing-level validation
     * @param featureConfig optional configuration for Feature-level validation
     * @param dynamicConfig list of dynamic validation configurations for specific contexts
     * @param revision the revision number of this configuration
     * @param created the timestamp when this configuration was created
     * @param modified the timestamp when this configuration was last modified
     * @param deleted whether this configuration is marked as deleted
     * @param metadata optional metadata associated with this configuration
     * @return a new instance with the specified values
     * @throws NullPointerException if {@code configId}, {@code dynamicConfig}, or any non-nullable argument is {@code null}
     */
    public static ImmutableWotValidationConfig of(
            final WotValidationConfigId configId,
            @Nullable final Boolean enabled,
            @Nullable final Boolean logWarningInsteadOfFailingApiCalls,
            @Nullable final ThingValidationConfig thingConfig,
            @Nullable final FeatureValidationConfig featureConfig,
            final List<DynamicValidationConfig> dynamicConfig,
            @Nullable final WotValidationConfigRevision revision,
            @Nullable final Instant created,
            @Nullable final Instant modified,
            @Nullable final Boolean deleted,
            @Nullable final Metadata metadata) {
        return new ImmutableWotValidationConfig(configId, enabled, logWarningInsteadOfFailingApiCalls, thingConfig,
                featureConfig, dynamicConfig, revision, created, modified, deleted, metadata);
    }

    public WotValidationConfigId getConfigId() {
        return configId;
    }

    public Optional<Boolean> isEnabled() {
        return Optional.ofNullable(enabled);
    }

    public Optional<Boolean> logWarningInsteadOfFailingApiCalls() {
        return Optional.ofNullable(logWarningInsteadOfFailingApiCalls);
    }

    @Override
    public Optional<ThingValidationConfig> getThingConfig() {
        return Optional.ofNullable(thingConfig);
    }

    @Override
    public Optional<FeatureValidationConfig> getFeatureConfig() {
        return Optional.ofNullable(featureConfig);
    }

    @Override
    public List<DynamicValidationConfig> getDynamicConfigs() {
        return dynamicConfig;
    }

    @Override
    public Optional<WotValidationConfigId> getEntityId() {
        return Optional.of(configId);
    }

    @Override
    public Optional<WotValidationConfigRevision> getRevision() {
        return Optional.ofNullable(revision);
    }

    public Optional<Instant> getModified() {
        return Optional.ofNullable(modified);
    }

    public Optional<Instant> getCreated() {
        return Optional.ofNullable(created);
    }

    public boolean isDeleted() {
        return deleted != null && deleted;
    }

    public Optional<Metadata> getMetadata() {
        return Optional.ofNullable(metadata);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ImmutableWotValidationConfig other = (ImmutableWotValidationConfig) obj;

        return Objects.equals(configId, other.configId)
                && Objects.equals(enabled, other.enabled)
                && Objects.equals(logWarningInsteadOfFailingApiCalls, other.logWarningInsteadOfFailingApiCalls)
                && Objects.equals(thingConfig, other.thingConfig)
                && Objects.equals(featureConfig, other.featureConfig)
                && Objects.equals(dynamicConfig, other.dynamicConfig)
                && Objects.equals(revision, other.revision)
                && Objects.equals(modified, other.modified)
                && Objects.equals(created, other.created)
                && Objects.equals(deleted, other.deleted)
                && Objects.equals(metadata, other.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configId, enabled, logWarningInsteadOfFailingApiCalls, thingConfig, featureConfig,
                dynamicConfig, revision, modified, created, deleted, metadata);
    }


    public ImmutableWotValidationConfig setRevision(final WotValidationConfigRevision revision) {
        return of(configId, enabled, logWarningInsteadOfFailingApiCalls, thingConfig, featureConfig, dynamicConfig,
                revision, created, modified, deleted, metadata);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "configId=" + configId +
                ", enabled=" + enabled +
                ", logWarningInsteadOfFailingApiCalls=" + logWarningInsteadOfFailingApiCalls +
                ", thingConfig=" + thingConfig +
                ", featureConfig=" + featureConfig +
                ", dynamicConfig=" + dynamicConfig +
                ", revision=" + revision +
                ", modified=" + modified +
                ", created=" + created +
                ", deleted=" + deleted +
                ", metadata=" + metadata +
                "]";
    }

    /**
     * Creates a new instance of {@code ImmutableWotValidationConfig} from a JSON object.
     * The JSON object should contain the following fields:
     * <ul>
     *     <li>{@code configId} (required): The unique identifier of the configuration</li>
     *     <li>{@code enabled} (optional): Whether WoT validation is enabled globally</li>
     *     <li>{@code logWarningInsteadOfFailingApiCalls} (optional): Whether validation failures should only log warnings</li>
     *     <li>{@code thing} (optional): Thing-level validation configuration</li>
     *     <li>{@code feature} (optional): Feature-level validation configuration</li>
     *     <li>{@code dynamicConfig} (optional): Array of dynamic validation configurations</li>
     * </ul>
     *
     * @param jsonObject the JSON object to create the configuration from
     * @return a new instance created from the JSON object
     * @throws NullPointerException if {@code jsonObject} is {@code null}
     */
    public static ImmutableWotValidationConfig fromJson(final JsonObject jsonObject) {

        final WotValidationConfigId configId = jsonObject.getValue(CONFIG_ID)
                .map(WotValidationConfigId::of)
                .orElse(WotValidationConfigId.GLOBAL);

        final Boolean enabled = jsonObject.getValue(ENABLED_FIELD)
                .orElse(null);

        final Boolean logWarningInsteadOfFailingApiCalls = jsonObject.getValue(LOG_WARNING_FIELD)
                .orElse(null);

        final ThingValidationConfig thingConfig = jsonObject.getValue(THING_FIELD)
                .map(ImmutableThingValidationConfig::fromJson)
                .orElse(null);

        final FeatureValidationConfig featureConfig = jsonObject.getValue(FEATURE_FIELD)
                .map(ImmutableFeatureValidationConfig::fromJson)
                .orElse(null);

        final List<DynamicValidationConfig> dynamicConfigs = jsonObject.getValue(DYNAMIC_CONFIG_FIELD)
                .map(array -> array.stream()
                        .filter(JsonValue::isObject)
                        .map(JsonValue::asObject)
                        .map(ImmutableDynamicValidationConfig::fromJson)
                        .map(DynamicValidationConfig.class::cast)
                        .collect(Collectors.toList()))
                .map(Collections::unmodifiableList)
                .orElse(Collections.emptyList());

        final WotValidationConfigRevision revision = jsonObject.getValue(JsonFields.REVISION)
                .map(WotValidationConfigRevision::of)
                .orElse(null);

        final Instant created = jsonObject.getValue(JsonFields.CREATED)
                .map(Instant::parse)
                .orElse(null);

        final Instant modified = jsonObject.getValue(JsonFields.MODIFIED)
                .map(Instant::parse)
                .orElse(null);

        final Boolean deleted = jsonObject.getValue(JsonFields.DELETED)
                .orElseGet(() -> false);

        final Metadata metadata = jsonObject.getValue(JsonFields.METADATA)
                .map(Metadata::newMetadata)
                .orElse(null);


        return of(configId, enabled, logWarningInsteadOfFailingApiCalls,
                thingConfig, featureConfig, dynamicConfigs, revision, created, modified, deleted, metadata);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        builder.set(CONFIG_ID, configId.toString());
        isEnabled().ifPresent(v -> builder.set(ENABLED_FIELD, v));
        logWarningInsteadOfFailingApiCalls().ifPresent(v -> builder.set(LOG_WARNING_FIELD, v));
        getThingConfig().ifPresent(config -> builder.set(THING_FIELD, config.toJson()));
        getFeatureConfig().ifPresent(config -> builder.set(FEATURE_FIELD, config.toJson()));
        if (!dynamicConfig.isEmpty()) {
            builder.set(DYNAMIC_CONFIG_FIELD, dynamicConfig.stream()
                    .map(DynamicValidationConfig::toJson)
                    .collect(JsonCollectors.valuesToArray()));
        }

        getRevision().ifPresent(revision -> builder.set(JsonFields.REVISION, revision.toLong()));
        getCreated().ifPresent(created -> builder.set(JsonFields.CREATED, created.toString()));
        getModified().ifPresent(modified -> builder.set(JsonFields.MODIFIED, modified.toString()));
        if (isDeleted()) {
            builder.set(JsonFields.DELETED, true);
        }
        getMetadata().ifPresent(metadata -> builder.set(JsonFields.METADATA, metadata.toJson()));
        return builder.build();
    }


    /**
     * An enumeration of the known JSON fields of a WoT validation config.
     */
    public static final class JsonFields {

        /**
         * JSON field containing the created timestamp.
         */
        public static final JsonFieldDefinition<String> CREATED =
                JsonFactory.newStringFieldDefinition("_created", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the created timestamp.
         */
        public static final JsonFieldDefinition<Long> REVISION =
                JsonFactory.newLongFieldDefinition("_revision", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the modified timestamp.
         */
        public static final JsonFieldDefinition<String> MODIFIED =
                JsonFactory.newStringFieldDefinition("_modified", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the deleted flag.
         */
        public static final JsonFieldDefinition<Boolean> DELETED =
                JsonFactory.newBooleanFieldDefinition("_deleted", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the metadata.
         */
        public static final JsonFieldDefinition<JsonObject> METADATA =
                JsonFactory.newJsonObjectFieldDefinition("_metadata", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}