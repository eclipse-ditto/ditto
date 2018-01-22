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
package org.eclipse.ditto.services.thingsearch.querymodel.expression;

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
     * Owner field name.
     */
    public static final String FIELD_NAME_OWNER = "owner";

    /**
     * namespace field to be saved in field expressions.
     */
    public static final String FIELD_NAMESPACE = "_namespace";

    /**
     * Namespace field name.
     */
    public static final String FIELD_NAME_NAMESPACE = "namespace";

    private static final String REGEX_FIELD_START = "^";
    private static final String REGEX_FIELD_END = "(/|\\z)";
    private static final String FIELD_NAME_ATTRIBUTES_PREFIX = "attributes/";

    private FieldExpressionUtil() {
        throw new AssertionError();
    }

    /**
     * Wraps the given field in an regex with a startsWith and an '/' or end-of-line part.
     *
     * @param field the field to wrap
     * @return the wrapped field
     */
    public static String wrapExistsRegex(final String field) {
        return REGEX_FIELD_START + field + REGEX_FIELD_END;
    }

    /**
     * Checks if the given field name is an feature property field name.
     *
     * @param fieldName the field name
     * @return {@code true}, if the field name is a feature property field name
     */
    public static Optional<FeatureField> parseFeatureField(final String fieldName) {
        final FeatureField field = new FeatureField(requireNonNull(fieldName));
        return field.isFeatureField() ? Optional.of(field) : Optional.empty();
    }

    /**
     * Checks if the given field name is an attribute field name.
     *
     * @param fieldName the field name
     * @return {@code true}, if the field name is an attribute field name
     */
    public static boolean isAttributeFieldName(final String fieldName) {
        return requireNonNull(fieldName).startsWith(FIELD_NAME_ATTRIBUTES_PREFIX);
    }

    /**
     * Strip the attributes-prefix from the given field name.
     *
     * @param attributesFieldName the field name
     * @return the field name without prefix
     */
    public static String stripAttributesPrefix(final String attributesFieldName) {
        return requireNonNull(attributesFieldName).substring(FIELD_NAME_ATTRIBUTES_PREFIX.length());
    }

    /**
     * Add the attributes prefix to the given field name.
     *
     * @param fieldName the field name
     * @return the field name with prefix
     */
    public static String addAttributesPrefix(final String fieldName) {
        return FIELD_NAME_ATTRIBUTES_PREFIX + requireNonNull(fieldName);
    }

    /**
     * Helper class representing a feature field with optional featureId and/or property path.
     */
    public static final class FeatureField {

        private static final Pattern FIELD_NAME_FEATURE_PATTERN1 = Pattern.compile("^features/" +
                "(?<featureId>[^\\*][^/]*)/properties/(?<property>.+)");

        private static final Pattern FIELD_NAME_FEATURE_PATTERN2 =
                Pattern.compile("^features/(?<featureId>[^\\*][^/]*)");

        private static final Pattern FIELD_NAME_FEATURE_PATTERN3 =
                Pattern.compile("^features/\\*/properties/(?<property>.+)");

        private final boolean matches;
        private final String featureId;
        private final String property;

        private FeatureField(final String fieldName) {
            Matcher matcher = FIELD_NAME_FEATURE_PATTERN1.matcher(fieldName);

            if (matcher.matches()) {
                this.matches = true;
                this.featureId = matcher.group("featureId");
                this.property = matcher.group("property");
            } else {
                matcher = FIELD_NAME_FEATURE_PATTERN2.matcher(fieldName);

                if (matcher.matches()) {
                    this.matches = true;
                    this.featureId = matcher.group("featureId");
                    this.property = null;
                } else {
                    matcher = FIELD_NAME_FEATURE_PATTERN3.matcher(fieldName);

                    if (matcher.matches()) {
                        this.matches = true;
                        this.featureId = null;
                        this.property = matcher.group("property");
                    } else {
                        this.matches = false;
                        this.featureId = null;
                        this.property = null;
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

    }
}
