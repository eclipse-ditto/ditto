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
package org.eclipse.ditto.things.service.common.config;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.json.JsonFieldSelector;

/**
 * Provides a configuration entry for Thing event pre-defined {@code extraFields} injection.
 */
@Immutable
public interface PreDefinedExtraFieldsConfig {

    /**
     * The list of namespace {@link Pattern}s this entry applies to.
     * An empty list would match any. The pattern must match the full string.
     *
     * @return the list of values
     */
    List<Pattern> getNamespace();

    /**
     * The optional RQL condition which - when evaluating to {@code true} - will apply sending the {@code extraFields}.
     * Extra fields will not be injected when the condition evaluates to {@code false}.
     *
     * @return the optional RQL condition under which circumstances to inject extra fields.
     */
    Optional<String> getCondition();

    /**
     * The extra fields in form of {@link JsonFieldSelector} to send along all events in the matching namespaces
     * whenever the optional condition matches.
     *
     * @return the extra fields to send along for thing events.
     */
    JsonFieldSelector getExtraFields();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code PreDefinedExtraFieldsConfig}.
     */
    enum ConfigValues implements KnownConfigValue {
        /**
         * Matching namespaces, supports wildcards.
         */
        NAMESPACES("namespaces", List.of()),

        /**
         * Optional RQL condition.
         */
        CONDITION("condition", null),

        /**
         * Matching auth subjects.
         */
        EXTRA_FIELDS("extra-fields", List.of());

        private final String path;
        private final Object defaultValue;

        ConfigValues(final String thePath, @Nullable final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

    }
}
