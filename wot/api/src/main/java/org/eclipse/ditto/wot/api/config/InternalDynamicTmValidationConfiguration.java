/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.api.config;

import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.things.model.DefinitionIdentifierInvalidException;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.wot.validation.ValidationContext;

import com.typesafe.config.Config;

/**
 * Internal helper in order to dynamically apply WoT ThingModel validation/enforcement configuration based on the
 * {@link ValidationContext} for individual API requests.
 */
final class InternalDynamicTmValidationConfiguration {

    private static final String CONFIG_KEY_VALIDATION_CONTEXT = "validation-context";
    private static final String CONFIG_KEY_DITTO_HEADERS_PATTERNS = "ditto-headers-patterns";
    private static final String CONFIG_KEY_THING_DEFINITION_PATTERNS = "thing-definition-patterns";
    private static final String CONFIG_KEY_FEATURE_DEFINITION_PATTERNS = "feature-definition-patterns";
    private static final String CONFIG_KEY_CONFIG_OVERRIDES = "config-overrides";
    private static final String CONFIG_KEY_SCOPE_ID = "scope-id";
    private static final String CONFIG_KEY_ALTERNATIVE_THING_DEFINITIONS = "alternative-thing-definitions";

    private final DynamicValidationContextConfiguration dynamicValidationContextConfiguration;
    private final Config configOverrides;
    private final List<ThingDefinition> alternativeThingDefinitions;
    private final String scopeId;


    InternalDynamicTmValidationConfiguration(final Config config) {
        final Config validationContext = config.getConfig(CONFIG_KEY_VALIDATION_CONTEXT);

        final List<Map<String, Pattern>> parsedDittoHeadersPatterns =
                validationContext.hasPath(CONFIG_KEY_DITTO_HEADERS_PATTERNS) ?
                        validationContext.getConfigList(CONFIG_KEY_DITTO_HEADERS_PATTERNS)
                                .stream()
                                .map(c -> c.entrySet()
                                        .stream()
                                        .map(e -> new AbstractMap.SimpleEntry<>(
                                                        e.getKey(),
                                                        Pattern.compile(e.getValue().unwrapped().toString())
                                                )
                                        )
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                                )
                                .toList() : List.of();
        final List<Pattern> thingDefinitionPatterns =
                validationContext.hasPath(CONFIG_KEY_THING_DEFINITION_PATTERNS) ?
                        validationContext.getStringList(CONFIG_KEY_THING_DEFINITION_PATTERNS)
                                .stream()
                                .map(Pattern::compile)
                                .toList() : List.of();
        final List<Pattern> featureDefinitionPatterns =
                validationContext.hasPath(CONFIG_KEY_FEATURE_DEFINITION_PATTERNS) ?
                        validationContext.getStringList(CONFIG_KEY_FEATURE_DEFINITION_PATTERNS)
                                .stream()
                                .map(Pattern::compile)
                                .toList() : List.of();

        dynamicValidationContextConfiguration = new DynamicValidationContextConfiguration(
                parsedDittoHeadersPatterns,
                thingDefinitionPatterns,
                featureDefinitionPatterns
        );
        configOverrides = config.getConfig(CONFIG_KEY_CONFIG_OVERRIDES);
        scopeId = config.hasPath(CONFIG_KEY_SCOPE_ID) ? config.getString(CONFIG_KEY_SCOPE_ID) : "ditto:static";
        alternativeThingDefinitions = parseAlternativeThingDefinitions(configOverrides, scopeId);
        if (!alternativeThingDefinitions.isEmpty() && thingDefinitionPatterns.isEmpty()) {
            throw new DittoConfigError("The dynamic config section <" + scopeId + "> defines <" +
                    CONFIG_KEY_ALTERNATIVE_THING_DEFINITIONS + "> but no <" + CONFIG_KEY_THING_DEFINITION_PATTERNS +
                    ">.");
        }
    }

    private static List<ThingDefinition> parseAlternativeThingDefinitions(final Config configOverrides,
            final String scopeId) {
        if (!configOverrides.hasPath(CONFIG_KEY_ALTERNATIVE_THING_DEFINITIONS)) {
            return List.of();
        }
        return configOverrides.getStringList(CONFIG_KEY_ALTERNATIVE_THING_DEFINITIONS)
                .stream()
                .map(definition -> {
                    final ThingDefinition thingDefinition;
                    try {
                        thingDefinition = ThingsModelFactory.newDefinition(definition);
                    } catch (final DefinitionIdentifierInvalidException e) {
                        throw new DittoConfigError("The dynamic config section <" + scopeId +
                                "> contains the invalid alternative thing definition <" + definition + ">.", e);
                    }
                    if (thingDefinition.getUrl().isEmpty()) {
                        throw new DittoConfigError("The dynamic config section <" + scopeId +
                                "> contains the alternative thing definition <" + definition +
                                "> which is not a URL.");
                    }
                    return thingDefinition;
                })
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), List::copyOf));
    }

    /**
     * @return the config overrides of this dynamic config
     */
    public Config configOverrides() {
        return configOverrides;
    }

    /**
     * @return the configuration of the dynamic validation context
     */
    public DynamicValidationContextConfiguration getDynamicValidationContextConfiguration() {
        return dynamicValidationContextConfiguration;
    }

    /**
     * @return the alternative thing definitions of this dynamic config
     */
    List<ThingDefinition> getAlternativeThingDefinitions() {
        return alternativeThingDefinitions;
    }

    /**
     * @return the scope ID of this dynamic config
     */
    public String getScopeId() {
        return scopeId;
    }

    /**
     * Determines whether the configuration overrides of this dynamic config apply for the given
     * {@code validationContext}.
     *
     * @param validationContext the context to potentially apply a configuration overwrite for
     * @return whether this dynamic config applies to the given validation context
     */
    boolean matches(@Nullable final ValidationContext validationContext) {
        if (validationContext == null) {
            return false;
        } else {
            final boolean thingDefinitionMatches = validationContext.thingDefinition() != null &&
                    dynamicValidationContextConfiguration.thingDefinitionPatterns().stream()
                            // OR
                            .anyMatch(pattern -> pattern.matcher(validationContext.thingDefinition().toString())
                                    .matches());
            if (!dynamicValidationContextConfiguration.thingDefinitionPatterns().isEmpty() && !thingDefinitionMatches) {
                return false;
            }

            final boolean featureDefinitionMatches = validationContext.featureDefinition() != null &&
                    dynamicValidationContextConfiguration.featureDefinitionPatterns().stream()
                            // OR
                            .anyMatch(pattern -> pattern.matcher(validationContext.featureDefinition().toString())
                                    .matches());
            if (!dynamicValidationContextConfiguration.featureDefinitionPatterns().isEmpty() &&
                    !featureDefinitionMatches) {
                return false;
            }

            final boolean dittoHeadersMatch = dynamicValidationContextConfiguration.dittoHeadersPatterns()
                    .stream()
                    // OR
                    .anyMatch(headersMap -> headersMap.entrySet().stream()
                            // AND
                            .allMatch(headerEntry ->
                                    Optional.ofNullable(validationContext.dittoHeaders().get(headerEntry.getKey()))
                                            .filter(headerValue -> headerEntry.getValue()
                                                    .matcher(headerValue)
                                                    .matches())
                                            .isPresent()
                            )
                    );
            if (!dynamicValidationContextConfiguration.dittoHeadersPatterns().isEmpty() && !dittoHeadersMatch) {
                return false;
            }

            return true;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final InternalDynamicTmValidationConfiguration that)) {
            return false;
        }
        return Objects.equals(dynamicValidationContextConfiguration, that.dynamicValidationContextConfiguration) &&
                Objects.equals(configOverrides, that.configOverrides) &&
                Objects.equals(scopeId, that.scopeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dynamicValidationContextConfiguration, configOverrides, scopeId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "dynamicValidationContextConfiguration=" + dynamicValidationContextConfiguration +
                ", configOverrides=" + configOverrides +
                ", scopeId=" + scopeId +
                "]";
    }

    record DynamicValidationContextConfiguration(
            List<Map<String, Pattern>> dittoHeadersPatterns,
            List<Pattern> thingDefinitionPatterns,
            List<Pattern> featureDefinitionPatterns
    ) {
    }
}
