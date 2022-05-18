/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.util.config.streaming;

import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of SSE.
 */
public interface SseConfig {

    /**
     * Config path relative to its parent.
     */
    String CONFIG_PATH = "sse";

    /**
     * Returns the throttling config for SSE.
     *
     * @return the throttling config.
     */
    ThrottlingConfig getThrottlingConfig();

    enum SseConfigValue implements KnownConfigValue {
        ;
        private final String path;
        private final Object defaultValue;

        SseConfigValue(final String thePath, final Object theDefaultValue) {
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
