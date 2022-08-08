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
package org.eclipse.ditto.rql.query.expression;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for field expressions.
 */
public final class FieldExpressionUtil {

    /**
     * ID field to be saved in field expressions.
     */
    public static final String FIELD_ID = "_id";

    /**
     * ThingId field name.
     */
    public static final String FIELD_NAME_THING_ID = "thingId";

    /**
     * Namespace field to be saved in field expressions.
     */
    public static final String FIELD_NAMESPACE = "_namespace";

    /**
     * Namespace field name.
     */
    public static final String FIELD_NAME_NAMESPACE = "namespace";

    /**
     * Definition field name.
     * @since 2.2.0
     */
    public static final String FIELD_NAME_DEFINITION = "definition";

    private static final String REGEX_FIELD_START = "^";
    private static final String REGEX_FIELD_END = "(/|\\z)";
    private static final String FIELD_NAME_ATTRIBUTES_PREFIX = "attributes/";
    private static final String FIELD_NAME_METADATA_PREFIX = "_metadata/";

    private FieldExpressionUtil() {
        throw new AssertionError();
    }

    /**
     * Wraps the given field in a regex with a startsWith and an '/' or end-of-line part.
     *
     * @param field the field to wrap
     * @return the wrapped field
     */
    public static String wrapExistsRegex(final String field) {
        return REGEX_FIELD_START + field + REGEX_FIELD_END;
    }

    /**
     * Checks if the given field name is a feature property field name.
     *
     * @param fieldName the field name
     * @return {@code true}, if the field name is a feature property field name
     */
    static Optional<FeatureField> parseFeatureField(final String fieldName) {
        final FeatureField field = new FeatureField(requireNonNull(fieldName));
        return field.isFeatureField() ? Optional.of(field) : Optional.empty();
    }

    /**
     * Checks if the given field name is an attribute field name.
     *
     * @param fieldName the field name
     * @return {@code true}, if the field name is an attribute field name
     */
    static boolean isAttributeFieldName(final String fieldName) {
        return requireNonNull(fieldName).startsWith(FIELD_NAME_ATTRIBUTES_PREFIX);
    }

    /**
     * Strip the attributes-prefix from the given field name.
     *
     * @param attributesFieldName the field name
     * @return the field name without prefix
     */
    static String stripAttributesPrefix(final String attributesFieldName) {
        return requireNonNull(attributesFieldName).substring(FIELD_NAME_ATTRIBUTES_PREFIX.length());
    }

    /**
     * Add the attributes prefix to the given field name.
     *
     * @param fieldName the field name
     * @return the field name with prefix
     */
    static String addAttributesPrefix(final String fieldName) {
        return FIELD_NAME_ATTRIBUTES_PREFIX + requireNonNull(fieldName);
    }

    /**
     * Checks if the given field name is a metadata field name.
     *
     * @param fieldName the field name
     * @return {@code true}, if the field name is a metadata field name
     */
    static boolean isMetadataFieldName(final String fieldName) {
        return requireNonNull(fieldName).startsWith(FIELD_NAME_METADATA_PREFIX);
    }

    /**
     * Strip the metadata-prefix from the given field name.
     *
     * @param metadataFieldName the field name
     * @return the field name without prefix
     */
    public static String stripMetadataPrefix(final String metadataFieldName) {
        return requireNonNull(metadataFieldName).substring(FIELD_NAME_METADATA_PREFIX.length());
    }

    /**
     * Helper class representing a feature field with optional featureId and/or property path.
     */
    public static final class FeatureField {

        private static final Pattern FIELD_NAME_PROPERTIES_PATTERN =
                Pattern.compile("^features/(?<featureId>[^/]++)/properties/?");

        private static final Pattern FIELD_NAME_DESIRED_PROPERTIES_PATTERN =
                Pattern.compile("^features/(?<featureId>[^/]++)/desiredProperties/?");

        private static final Pattern FIELD_NAME_FEATURE_PATTERN1 =
                Pattern.compile("^features/(?<featureId>[^/]++)/properties/(?<property>.+)");

        private static final Pattern FIELD_NAME_DESIRED_FEATURE_PATTERN =
                Pattern.compile("^features/(?<featureId>[^/]++)/desiredProperties/(?<desiredProperty>.+)");

        private static final Pattern FIELD_NAME_FEATURE_PATTERN2 =
                Pattern.compile("^features/(?<featureId>[^/]++)");

        private static final Pattern FIELD_NAME_FEATURE_DEFINITION_PATTERN =
                Pattern.compile("^features/(?<featureId>[^/]++)/definition/?");

        private static final String FEATURE_ID = "featureId";

        private final boolean matches;
        private final String featureId;
        private final String property;
        private final boolean isProperties;
        private final String desiredProperty;
        private final boolean isDesiredProperties;
        private final boolean isDefinition;

        private FeatureField(final CharSequence fieldName) {
            Matcher matcher = FIELD_NAME_FEATURE_PATTERN1.matcher(fieldName);
            if (matcher.matches()) {
                matches = true;
                featureId = matcher.group(FEATURE_ID);
                isProperties = false;
                property = matcher.group("property");
                isDesiredProperties = false;
                isDefinition = false;
                desiredProperty = null;
            } else {

                matcher = FIELD_NAME_DESIRED_FEATURE_PATTERN.matcher(fieldName);
                if (matcher.matches()) {
                    matches = true;
                    featureId = matcher.group(FEATURE_ID);
                    isProperties = false;
                    property = null;
                    isDesiredProperties = false;
                    isDefinition = false;
                    desiredProperty = matcher.group("desiredProperty");
                } else {

                    matcher = FIELD_NAME_PROPERTIES_PATTERN.matcher(fieldName);
                    if (matcher.matches()) {
                        matches = true;
                        featureId = matcher.group(FEATURE_ID);
                        isProperties = true;
                        property = null;
                        isDesiredProperties = false;
                        isDefinition = false;
                        desiredProperty = null;
                    } else {

                        matcher = FIELD_NAME_DESIRED_PROPERTIES_PATTERN.matcher(fieldName);
                        if (matcher.matches()) {
                            matches = true;
                            featureId = matcher.group(FEATURE_ID);
                            isProperties = false;
                            property = null;
                            isDesiredProperties = true;
                            isDefinition = false;
                            desiredProperty = null;
                        } else {

                            matcher = FIELD_NAME_FEATURE_DEFINITION_PATTERN.matcher(fieldName);
                            if (matcher.matches()) {
                                matches = true;
                                featureId = matcher.group(FEATURE_ID);
                                isProperties = false;
                                property = null;
                                isDesiredProperties = false;
                                isDefinition = true;
                                desiredProperty = null;
                            } else {

                                matcher = FIELD_NAME_FEATURE_PATTERN2.matcher(fieldName);
                                if (matcher.matches()) {
                                    matches = true;
                                    featureId = matcher.group(FEATURE_ID);
                                    isProperties = false;
                                    property = null;
                                    isDesiredProperties = false;
                                    isDefinition = false;
                                    desiredProperty = null;
                                } else {

                                    matches = false;
                                    featureId = null;
                                    isProperties = false;
                                    property = null;
                                    isDesiredProperties = false;
                                    isDefinition = false;
                                    desiredProperty = null;
                                }
                            }
                        }
                    }
                }
            }
        }

        private boolean isFeatureField() {
            return matches;
        }

        /**
         * @return the optional feature id.
         */
        public Optional<String> getFeatureId() {
            return Optional.ofNullable(featureId);
        }

        /**
         * @return the optional feature property path.
         */
        public Optional<String> getProperty() {
            return Optional.ofNullable(property);
        }

        /**
         * @return the optional feature desiredProperty path.
         */
        public Optional<String> getDesiredProperty() {
            return Optional.ofNullable(desiredProperty);
        }

        /**
         * @return whether the field matches the properties.
         */
        public boolean isProperties() {
            return isProperties;
        }

        /**
         * @return whether the field matches the desired properties.
         */
        public boolean isDesiredProperties() {
            return isDesiredProperties;
        }

        public boolean isDefinition() {
            return isDefinition;
        }
    }

}
