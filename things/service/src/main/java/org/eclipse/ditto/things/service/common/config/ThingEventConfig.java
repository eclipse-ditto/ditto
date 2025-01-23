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

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.EventConfig;

/**
 * Extends {@link EventConfig} by providing ThingEvent specific additional configuration.
 */
public interface ThingEventConfig extends EventConfig {

    /**
     * Contains pre-defined (configured) {@code extraFields} to send along all thing (change) events.
     *
     * @return the pre-defined {@code extraFields} to send along.
     */
    List<PreDefinedExtraFieldsConfig> getPredefinedExtraFieldsConfigs();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code ThingEventConfig}.
     */
    enum ThingEventConfigValue implements KnownConfigValue {

        /**
         * The pre-defined (configured) {@code extraFields} to send along all events.
         */
        PRE_DEFINED_EXTRA_FIELDS("pre-defined-extra-fields", List.of());

        private final String path;
        private final Object defaultValue;

        ThingEventConfigValue(final String thePath, final Object theDefaultValue) {
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
