/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.integration.config;

import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * Provides configuration settings for WoT (Web of Things) integration regarding the Thing Description transformation
 * from Thing Models.
 *
 * @since 2.4.0
 */
@Immutable
public interface ToThingDescriptionConfig {

    /**
     * Returns the URI prefix of the WoT {@code "base"} to inject in generated TDs.
     * Will be prepended to {@code "/api/2/things/<thing:id>}.
     *
     * @return the URI prefix of the WoT {@code "base"} to inject in generated TDs.
     */
    String getBasePrefix();

    /**
     * Returns the JSON object template to additionally add to generated WoT TDs, e.g. containing additional
     * {@code "securityDefinitions"}.
     *
     * @return the JSON object template to additionally add to generated WoT TDs.
     */
    JsonObject getJsonTemplate();

    /**
     * Returns the map containing placeholder constants to use in order to resolve WoT TM placeholders.
     *
     * @return the map containing placeholder constants to use in order to resolve WoT TM placeholders.
     */
    Map<String, JsonValue> getPlaceholders();

    /**
     * Returns whether to add {@code "created"} to each TD reflecting the created timestamp of the Thing.
     *
     * @return whether to add {@code "created"} to each TD.
     */
    boolean addCreated();

    /**
     * Returns whether to add {@code "modified"} to each TD reflecting the last modified timestamp of the Thing.
     *
     * @return whether to add {@code "modified"} to each TD.
     */
    boolean addModified();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code ToThingDescriptionConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * The URI prefix of the WoT {@code "base"} to inject in generated TDs.
         */
        BASE_PREFIX("base-prefix", "http://localhost:8080"),

        /**
         * The JSON object template to additionally add to generated WoT TDs.
         */
        JSON_TEMPLATE("json-template", JsonObject.empty()),

        /**
         * A map containing placeholder constants to use in order to resolve WoT TM placeholders.
         */
        PLACEHOLDERS("placeholders", JsonObject.empty()),

        /**
         * Whether to add {@code "created"} to each TD.
         */
        ADD_CREATED("add-created", true),

        /**
         * Whether to add {@code "modified"} to each TD.
         */
        ADD_MODIFIED("add-modified", false);

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, final Object theDefaultValue) {
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
