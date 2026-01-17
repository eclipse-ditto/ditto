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
package org.eclipse.ditto.gateway.service.util.config.endpoints;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.json.JsonObject;

/**
 * Provides configuration settings for WoT (Web of Things) Discovery "Thing Directory" served by the Ditto gateway.
 *
 * @since 3.9.0
 */
@Immutable
public interface WotDirectoryConfig {

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
     * An enumeration of the known config path expressions and their associated default values for
     * {@code WotDirectoryConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * The URI prefix of the WoT {@code "base"} to inject in the Thing Directory TD.
         */
        BASE_PREFIX("base-prefix", "http://localhost:8080"),

        /**
         * The JSON object template to additionally add to the Thing Directory TD.
         */
        JSON_TEMPLATE("json-template", JsonObject.empty());

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
