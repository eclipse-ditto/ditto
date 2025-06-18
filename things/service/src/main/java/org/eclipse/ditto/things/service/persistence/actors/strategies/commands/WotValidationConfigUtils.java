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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationEnforceConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationForbidConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationEnforceConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationForbidConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Utility class for handling WoT (Web of Things) validation configuration operations.
 * This class provides methods for merging, mapping, and transforming validation configurations
 * between different formats (API, DevOps, and static configurations).
 * <p>
 * The class is immutable and thread-safe.
 *
 * @since 3.8.0
 */
@Immutable
public final class WotValidationConfigUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(WotValidationConfigUtils.class);

    private WotValidationConfigUtils() {
        throw new AssertionError();
    }

    public static ThingValidationConfig mapToDevopsThingValidationConfig(final org.eclipse.ditto.wot.validation.config.ThingValidationConfig apiConfig) {
        return ThingValidationConfig.of(
                ThingValidationEnforceConfig.of(
                        apiConfig.isEnforceThingDescriptionModification(),
                        apiConfig.isEnforceAttributes(),
                        apiConfig.isEnforceInboxMessagesInput(),
                        apiConfig.isEnforceInboxMessagesOutput(),
                        apiConfig.isEnforceOutboxMessages()
                ),
                ThingValidationForbidConfig.of(
                        apiConfig.isForbidThingDescriptionDeletion(),
                        apiConfig.isForbidNonModeledAttributes(),
                        apiConfig.isForbidNonModeledInboxMessages(),
                        apiConfig.isForbidNonModeledOutboxMessages()
                )
        );
    }

    public static FeatureValidationConfig mapToDevopsFeatureValidationConfig(final org.eclipse.ditto.wot.validation.config.FeatureValidationConfig apiConfig) {
        return FeatureValidationConfig.of(
                FeatureValidationEnforceConfig.of(
                        apiConfig.isEnforceFeatureDescriptionModification(),
                        apiConfig.isEnforcePresenceOfModeledFeatures(),
                        apiConfig.isEnforceProperties(),
                        apiConfig.isEnforceDesiredProperties(),
                        apiConfig.isEnforceInboxMessagesInput(),
                        apiConfig.isEnforceInboxMessagesOutput(),
                        apiConfig.isEnforceOutboxMessages()
                ),
                FeatureValidationForbidConfig.of(
                        apiConfig.isForbidFeatureDescriptionDeletion(),
                        apiConfig.isForbidNonModeledFeatures(),
                        apiConfig.isForbidNonModeledProperties(),
                        apiConfig.isForbidNonModeledDesiredProperties(),
                        apiConfig.isForbidNonModeledInboxMessages(),
                        apiConfig.isForbidNonModeledOutboxMessages()
                )
        );
    }

    /**
     * Merges a dynamic (entity) WoT validation configuration with a static configuration.
     * The dynamic configuration takes precedence over the static configuration for all fields.
     * <p>
     * The merge process:
     * <ul>
     *     <li>Combines top-level fields (enabled, logWarning)</li>
     *     <li>Merges thing validation configuration</li>
     *     <li>Merges feature validation configuration</li>
     *     <li>Combines dynamic configuration sections</li>
     * </ul>
     *
     * @param entity the dynamic WoT validation configuration, may be null
     * @param staticConfig the static WoT validation configuration, must not be null
     * @return the merged WoT validation configuration
     * @throws NullPointerException if staticConfig is null
     * @throws IllegalArgumentException if the merge operation fails
     */
    public static WotValidationConfig mergeConfigs(
            @Nullable final WotValidationConfig entity,
            @Nonnull final TmValidationConfig staticConfig) {
        Objects.requireNonNull(staticConfig, "staticConfig must not be null");

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Starting config merge - entity: {}, staticConfig enabled: {}",
                    entity != null ? entity.getEntityId() : "null",
                    staticConfig.isEnabled());
        }

        try {
            return entity == null ?
                    createConfigFromStatic(staticConfig) :
                    mergeWithEntity(entity, staticConfig);
        } catch (final Exception e) {
            LOGGER.error("Failed to merge WoT validation configs", e);
            throw new IllegalArgumentException("Failed to merge WoT validation configs: " + e.getMessage(), e);
        }
    }

    private static WotValidationConfig createConfigFromStatic(final TmValidationConfig staticConfig) {
        final ThingValidationConfig thingConfig = Optional.of(staticConfig.getThingValidationConfig())
                .map(WotValidationConfigUtils::mapToDevopsThingValidationConfig)
                .orElse(null);

        final FeatureValidationConfig featureConfig = Optional.of(staticConfig.getFeatureValidationConfig())
                .map(WotValidationConfigUtils::mapToDevopsFeatureValidationConfig)
                .orElse(null);

        final List<DynamicValidationConfig> dynamicConfigs = Optional.of(staticConfig.getDynamicConfigs())
                .orElse(Collections.emptyList());

        return WotValidationConfig.of(
                WotValidationConfigId.MERGED,
                staticConfig.isEnabled(),
                staticConfig.logWarningInsteadOfFailingApiCalls(),
                thingConfig,
                featureConfig,
                Collections.unmodifiableList(dynamicConfigs),
                null,
                null,
                null,
                false,
                null
        );
    }

    private static WotValidationConfig mergeWithEntity(
            @Nonnull final WotValidationConfig entity,
            @Nonnull final TmValidationConfig staticConfig) {

        // Merge top-level fields with dynamic config taking precedence
        final boolean enabled = entity.isEnabled().orElse(staticConfig.isEnabled());
        final boolean logWarning = entity.logWarningInsteadOfFailingApiCalls()
                .orElse(staticConfig.logWarningInsteadOfFailingApiCalls());

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Merging configs - entity: {}, enabled: {}, logWarning: {}",
                    entity.getConfigId(), enabled, logWarning);
        }

        final ThingValidationConfig mergedThingConfig = entity.getThingConfig()
                .map(dynamicConfig -> Optional.of(staticConfig.getThingValidationConfig())
                        .map(staticThingConfig -> mergeThingConfig(
                                dynamicConfig,
                                mapToDevopsThingValidationConfig(staticThingConfig)))
                        .orElse(dynamicConfig))
                .orElseGet(() -> Optional.of(staticConfig.getThingValidationConfig())
                        .map(WotValidationConfigUtils::mapToDevopsThingValidationConfig)
                        .orElse(null));

        final FeatureValidationConfig mergedFeatureConfig = entity.getFeatureConfig()
                .map(dynamicConfig -> Optional.of(staticConfig.getFeatureValidationConfig())
                        .map(staticFeatureConfig -> mergeFeatureConfig(
                                dynamicConfig,
                                mapToDevopsFeatureValidationConfig(staticFeatureConfig)))
                        .orElse(dynamicConfig))
                .orElseGet(() -> Optional.of(staticConfig.getFeatureValidationConfig())
                        .map(WotValidationConfigUtils::mapToDevopsFeatureValidationConfig)
                        .orElse(null));

        // Merge dynamic configs, preserving order with entity configs first
        final List<DynamicValidationConfig> mergedDynamicConfigs = new ArrayList<>();
        mergedDynamicConfigs.addAll(entity.getDynamicConfigs());
        mergedDynamicConfigs.addAll(staticConfig.getDynamicConfigs());

        return WotValidationConfig.of(
                WotValidationConfigId.MERGED,
                enabled,
                logWarning,
                mergedThingConfig,
                mergedFeatureConfig,
                Collections.unmodifiableList(mergedDynamicConfigs),
                entity.getRevision().orElse(null),
                entity.getModified().orElse(null),
                entity.getCreated().orElse(null),
                entity.isDeleted(),
                entity.getMetadata().orElse(null)
        );
    }

    private static ThingValidationConfig mergeThingConfig(
            @Nullable final ThingValidationConfig dynamicConfig,
            @Nullable final ThingValidationConfig staticConfig) {

        if (dynamicConfig == null && staticConfig == null) {
            return null;
        }

        // Merge enforce config
        final ThingValidationEnforceConfig mergedEnforce = mergeThingEnforceConfig(
                Optional.ofNullable(dynamicConfig).flatMap(ThingValidationConfig::getEnforce).orElse(null),
                Optional.ofNullable(staticConfig).flatMap(ThingValidationConfig::getEnforce).orElse(null));

        // Merge forbid config
        final ThingValidationForbidConfig mergedForbid = mergeThingForbidConfig(
                Optional.ofNullable(dynamicConfig).flatMap(ThingValidationConfig::getForbid).orElse(null),
                Optional.ofNullable(staticConfig).flatMap(ThingValidationConfig::getForbid).orElse(null));

        return ThingValidationConfig.of(mergedEnforce, mergedForbid);
    }

    private static FeatureValidationConfig mergeFeatureConfig(
            @Nullable final FeatureValidationConfig dynamicConfig,
            @Nullable final FeatureValidationConfig staticConfig) {

        if (dynamicConfig == null && staticConfig == null) {
            return null;
        }

        final FeatureValidationEnforceConfig mergedEnforce = mergeFeatureEnforceConfig(
                Optional.ofNullable(dynamicConfig).flatMap(FeatureValidationConfig::getEnforce).orElse(null),
                Optional.ofNullable(staticConfig).flatMap(FeatureValidationConfig::getEnforce).orElse(null));

        final FeatureValidationForbidConfig mergedForbid = mergeFeatureForbidConfig(
                Optional.ofNullable(dynamicConfig).flatMap(FeatureValidationConfig::getForbid).orElse(null),
                Optional.ofNullable(staticConfig).flatMap(FeatureValidationConfig::getForbid).orElse(null));

        return FeatureValidationConfig.of(mergedEnforce, mergedForbid);
    }

    private static ThingValidationEnforceConfig mergeThingEnforceConfig(
            @Nullable final ThingValidationEnforceConfig dynamicConfig,
            @Nullable final ThingValidationEnforceConfig staticEnforce) {

        if (dynamicConfig == null && staticEnforce == null) {
            throw new IllegalArgumentException("Both dynamic and static enforce configs are empty");
        }

        if (staticEnforce == null) {
            return dynamicConfig;
        } else if (dynamicConfig == null) {
            return staticEnforce;
        }

        return ThingValidationEnforceConfig.of(
                mergeBoolean(dynamicConfig.isThingDescriptionModification(), staticEnforce.isThingDescriptionModification()),
                mergeBoolean(dynamicConfig.isAttributes(), staticEnforce.isAttributes()),
                mergeBoolean(dynamicConfig.isInboxMessagesInput(), staticEnforce.isInboxMessagesInput()),
                mergeBoolean(dynamicConfig.isInboxMessagesOutput(), staticEnforce.isInboxMessagesOutput()),
                mergeBoolean(dynamicConfig.isOutboxMessages(), staticEnforce.isOutboxMessages())
        );
    }

    private static ThingValidationForbidConfig mergeThingForbidConfig(
            @Nullable final ThingValidationForbidConfig dynamicConfig,
            @Nullable final ThingValidationForbidConfig staticForbid) {

        if (dynamicConfig == null && staticForbid == null) {
            throw new IllegalArgumentException("Both dynamic and static forbid configs are empty");
        }

        if (staticForbid == null) {
            return dynamicConfig;
        } else if (dynamicConfig == null) {
            return staticForbid;
        }

        return ThingValidationForbidConfig.of(
                mergeBoolean(dynamicConfig.isThingDescriptionDeletion(), staticForbid.isThingDescriptionDeletion()),
                mergeBoolean(dynamicConfig.isNonModeledAttributes(), staticForbid.isNonModeledAttributes()),
                mergeBoolean(dynamicConfig.isNonModeledInboxMessages(), staticForbid.isNonModeledInboxMessages()),
                mergeBoolean(dynamicConfig.isNonModeledOutboxMessages(), staticForbid.isNonModeledOutboxMessages())
        );
    }

    private static FeatureValidationEnforceConfig mergeFeatureEnforceConfig(
            @Nullable final FeatureValidationEnforceConfig dynamicConfig,
            @Nullable final FeatureValidationEnforceConfig staticEnforce) {

        if (dynamicConfig == null && staticEnforce == null) {
            throw new IllegalArgumentException("Both dynamic and static enforce configs are empty");
        }

        if (staticEnforce == null) {
            return dynamicConfig;
        } else if (dynamicConfig == null) {
            return staticEnforce;
        }

        return FeatureValidationEnforceConfig.of(
                mergeBoolean(dynamicConfig.isFeatureDescriptionModification(), staticEnforce.isFeatureDescriptionModification()),
                mergeBoolean(dynamicConfig.isPresenceOfModeledFeatures(), staticEnforce.isPresenceOfModeledFeatures()),
                mergeBoolean(dynamicConfig.isProperties(), staticEnforce.isProperties()),
                mergeBoolean(dynamicConfig.isDesiredProperties(), staticEnforce.isDesiredProperties()),
                mergeBoolean(dynamicConfig.isInboxMessagesInput(), staticEnforce.isInboxMessagesInput()),
                mergeBoolean(dynamicConfig.isInboxMessagesOutput(), staticEnforce.isInboxMessagesOutput()),
                mergeBoolean(dynamicConfig.isOutboxMessages(), staticEnforce.isOutboxMessages())
        );
    }

    private static FeatureValidationForbidConfig mergeFeatureForbidConfig(
            @Nullable final FeatureValidationForbidConfig dynamicConfig,
            @Nullable final FeatureValidationForbidConfig staticForbid) {

        if (dynamicConfig == null && staticForbid == null) {
            throw new IllegalArgumentException("Both dynamic and static forbid configs are empty");
        }

        if (staticForbid == null) {
            return dynamicConfig;
        } else if (dynamicConfig == null) {
            return staticForbid;
        }

        return FeatureValidationForbidConfig.of(
                mergeBoolean(dynamicConfig.isFeatureDescriptionDeletion(), staticForbid.isFeatureDescriptionDeletion()),
                mergeBoolean(dynamicConfig.isNonModeledFeatures(), staticForbid.isNonModeledFeatures()),
                mergeBoolean(dynamicConfig.isNonModeledProperties(), staticForbid.isNonModeledProperties()),
                mergeBoolean(dynamicConfig.isNonModeledDesiredProperties(), staticForbid.isNonModeledDesiredProperties()),
                mergeBoolean(dynamicConfig.isNonModeledInboxMessages(), staticForbid.isNonModeledInboxMessages()),
                mergeBoolean(dynamicConfig.isNonModeledOutboxMessages(), staticForbid.isNonModeledOutboxMessages())
        );
    }

    private static Boolean mergeBoolean(
            @Nonnull final Optional<Boolean> dynamicValue,
            @Nonnull final Optional<Boolean> staticValue) {
        return dynamicValue.orElseGet(() -> staticValue.orElse(false));
    }

    /**
     * Merges a dynamic and static WoT validation configuration and returns the result as TmValidationConfig.
     * This method now merges the configs, converts the merged config to HOCON, and returns a DefaultTmValidationConfig.
     *
     * @param entity the dynamic WoT validation configuration, may be null
     * @param staticConfig the static WoT validation configuration
     * @return the merged configuration in TmValidationConfig format
     */
    public static TmValidationConfig mergeConfigsToTmValidationConfig(
            @Nullable final WotValidationConfig entity,
            final TmValidationConfig staticConfig) {
        // 1. Merge the configs using your existing logic
        final WotValidationConfig merged = mergeConfigs(entity, staticConfig);
        LOGGER.info("[mergeConfigsToTmValidationConfig] Merged ImmutableWotValidationConfig: {}", merged);

        // 2. Convert merged config to HOCON Config (using toJson, then toString, then parse)
        final String hoconString = jsonToHocon(merged.toJson());
        final Config config = ConfigFactory.parseString(hoconString);

        // 3. Build the DefaultTmValidationConfig
        TmValidationConfig result = org.eclipse.ditto.wot.api.config.DefaultTmValidationConfig.of(config);
        LOGGER.info("[mergeConfigsToTmValidationConfig] Resulting DefaultTmValidationConfig: {}", result);
        return result;
    }

    /**
     * Helper to convert a Ditto JsonObject to a HOCON string for ConfigFactory.parseString().
     * This is a simple implementation; for more complex cases, consider a library or more robust conversion.
     */
    private static String jsonToHocon(final org.eclipse.ditto.json.JsonObject json) {
        JsonObject kebabJson = convertKeysToKebabCase(json);

        JsonObjectBuilder configBuilder = org.eclipse.ditto.json.JsonFactory.newObjectBuilder();
        boolean hasDynamicConfig = false;

        // First pass: collect all non-dynamic-config fields
        for (JsonKey key : kebabJson.getKeys()) {
            if (!key.toString().equals("dynamic-config")) {
                configBuilder.set(key.toString(), kebabJson.getValue(key).get());
            }
        }

        // Second pass: handle dynamic-config specifically
        if (kebabJson.contains("dynamic-config")) {
            hasDynamicConfig = true;
            JsonValue dynamicConfig = kebabJson.getValue("dynamic-config").get();
            // Ensure we're setting the dynamic-configuration key with the dynamic config value
            configBuilder.set("dynamic-configuration", dynamicConfig);
        }

        // Always ensure dynamic-configuration exists
        if (!hasDynamicConfig) {
            configBuilder.set("dynamic-configuration", org.eclipse.ditto.json.JsonFactory.newArrayBuilder().build());
        }

        // Wrap in tm-model-validation
        org.eclipse.ditto.json.JsonObjectBuilder rootBuilder = org.eclipse.ditto.json.JsonFactory.newObjectBuilder();
        rootBuilder.set("tm-model-validation", configBuilder.build());

        String hocon = rootBuilder.build().toString();
        LOGGER.debug("[jsonToHocon] Final HOCON string: {}", hocon);
        return hocon;
    }

    private static JsonObject convertKeysToKebabCase(final JsonObject json) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        for (final JsonKey key : json.getKeys()) {
            final String kebabKey = camelToKebab(key.toString());
            final JsonValue value = json.getValue(key).orElseThrow();
            if (value.isObject()) {
                builder.set(kebabKey, convertKeysToKebabCase(value.asObject()));
            } else if (value.isArray()) {
                // Recursively handle arrays if needed
                builder.set(kebabKey, convertArrayKeysToKebabCase(value.asArray()));
            } else {
                builder.set(kebabKey, value);
            }
        }
        return builder.build();
    }

    private static org.eclipse.ditto.json.JsonArray convertArrayKeysToKebabCase(final org.eclipse.ditto.json.JsonArray array) {
        org.eclipse.ditto.json.JsonArrayBuilder arrayBuilder = org.eclipse.ditto.json.JsonFactory.newArrayBuilder();
        for (org.eclipse.ditto.json.JsonValue value : array) {
            if (value.isObject()) {
                arrayBuilder.add(convertKeysToKebabCase(value.asObject()));
            } else if (value.isArray()) {
                arrayBuilder.add(convertArrayKeysToKebabCase(value.asArray()));
            } else {
                arrayBuilder.add(value);
            }
        }
        return arrayBuilder.build();
    }

    private static String camelToKebab(String input) {
        return input.replaceAll("([a-z])([A-Z]+)", "$1-$2").toLowerCase();
    }
}