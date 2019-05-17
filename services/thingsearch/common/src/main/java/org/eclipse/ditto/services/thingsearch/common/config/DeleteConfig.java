/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.thingsearch.common.config;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings of the "delete" section.
 * <p>
 * Java serialization is supported for {@code DeleteConfig}.
 * </p>
 */
public interface DeleteConfig {

    boolean isDeleteEvent();

    boolean isDeleteNamespace();

    enum DeleteConfigValue implements KnownConfigValue {

        EVENT("event", true),

        NAMESPACE("namespace", true);

        private final String path;
        private final Object defaultValue;

        private DeleteConfigValue(final String path, final Object defaultValue) {
            this.path = path;
            this.defaultValue = defaultValue;
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
