/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.common.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings for a single field within a custom search index.
 *
 * @since 3.9.0
 */
@Immutable
public interface CustomSearchIndexFieldConfig {

    /**
     * Returns the field name to include in the index.
     *
     * @return the field name.
     */
    String getName();

    /**
     * Returns the sort direction for this field in the index.
     *
     * @return the direction (ASC or DESC).
     */
    Direction getDirection();

    /**
     * Sort direction for an index field.
     */
    enum Direction {
        ASC(1),
        DESC(-1);

        private final int mongoValue;

        Direction(final int mongoValue) {
            this.mongoValue = mongoValue;
        }

        /**
         * Returns the MongoDB integer value for this direction.
         *
         * @return 1 for ASC, -1 for DESC.
         */
        public int getMongoValue() {
            return mongoValue;
        }

        /**
         * Parses a direction from a string, case-insensitively.
         *
         * @param value the string value to parse.
         * @return the parsed direction.
         * @throws IllegalArgumentException if the value is not ASC or DESC.
         */
        public static Direction fromString(final String value) {
            return Direction.valueOf(value.toUpperCase());
        }
    }

    /**
     * An enumeration of the known config path expressions and their associated default values.
     */
    enum CustomSearchIndexFieldConfigValue implements KnownConfigValue {

        /**
         * The field name.
         */
        NAME("name", ""),

        /**
         * The sort direction (ASC or DESC).
         */
        DIRECTION("direction", "ASC");

        private final String path;
        private final Object defaultValue;

        CustomSearchIndexFieldConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }
    }
}
