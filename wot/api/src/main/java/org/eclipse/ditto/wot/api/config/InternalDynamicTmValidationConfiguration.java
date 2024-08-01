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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

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

    private final DynamicValidationContextConfiguration dynamicValidationContextConfiguration;
    private final Config configOverrides;

    InternalDynamicTmValidationConfiguration(final Config config) {
        final Config validationContext = config.getConfig(CONFIG_KEY_VALIDATION_CONTEXT);
        final List<Map<String, Pattern>> parsedDittoHeadersPatterns = validationContext
                .getConfigList(CONFIG_KEY_DITTO_HEADERS_PATTERNS)
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
                .toList();
        final List<Pattern> thingDefinitionPatterns = validationContext
                .getStringList(CONFIG_KEY_THING_DEFINITION_PATTERNS)
                .stream()
                .map(Pattern::compile)
                .toList();
        final List<Pattern> featureDefinitionPatterns = validationContext
                .getStringList(CONFIG_KEY_FEATURE_DEFINITION_PATTERNS)
                .stream()
                .map(Pattern::compile)
                .toList();

        dynamicValidationContextConfiguration = new DynamicValidationContextConfiguration(
                parsedDittoHeadersPatterns,
                thingDefinitionPatterns,
                featureDefinitionPatterns
        );
        configOverrides = config.getConfig(CONFIG_KEY_CONFIG_OVERRIDES);
    }

    /**
     * Calculates an optional configuration override to apply for the given {@code validationContext} or an empty
     * optional if there should not be specific configuration overrides for the given context.
     *
     * @param validationContext the context to potentially apply a configuration overwrite for
     * @return the optional configuration override to apply for the given validation context
     */
    Optional<Config> calculateDynamicTmValidationConfigOverrides(
            @Nullable final ValidationContext validationContext
    ) {
        if (validationContext == null) {
            return Optional.empty();
        } else {
            final boolean thingDefinitionMatches = validationContext.thingDefinition() != null &&
                    dynamicValidationContextConfiguration.thingDefinitionPatterns().stream()
                            // OR
                            .anyMatch(pattern -> pattern.matcher(validationContext.thingDefinition().toString())
                                    .matches());
            if (!dynamicValidationContextConfiguration.thingDefinitionPatterns().isEmpty() && !thingDefinitionMatches) {
                return Optional.empty();
            }

            final boolean featureDefinitionMatches = validationContext.featureDefinition() != null &&
                    dynamicValidationContextConfiguration.featureDefinitionPatterns().stream()
                            // OR
                            .anyMatch(pattern -> pattern.matcher(validationContext.featureDefinition().toString())
                                    .matches());
            if (!dynamicValidationContextConfiguration.featureDefinitionPatterns().isEmpty() && !featureDefinitionMatches) {
                return Optional.empty();
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
                return Optional.empty();
            }

            return Optional.ofNullable(configOverrides);
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
                Objects.equals(configOverrides, that.configOverrides);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dynamicValidationContextConfiguration, configOverrides);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "dynamicValidationContextConfiguration=" + dynamicValidationContextConfiguration +
                ", configOverrides=" + configOverrides +
                "]";
    }

    record DynamicValidationContextConfiguration(
            List<Map<String, Pattern>> dittoHeadersPatterns,
            List<Pattern> thingDefinitionPatterns,
            List<Pattern> featureDefinitionPatterns
    ) {
    }
}
