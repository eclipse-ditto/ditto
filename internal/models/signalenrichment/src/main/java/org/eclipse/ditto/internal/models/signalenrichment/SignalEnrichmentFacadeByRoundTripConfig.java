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
package org.eclipse.ditto.internal.models.signalenrichment;

import java.time.Duration;

import javax.annotation.Nonnull;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Retrieve fixed parts of things by asking an actor.
 */
public interface SignalEnrichmentFacadeByRoundTripConfig {

    /**
     * Relative path of the provider config inside signal-enrichment config.
     */
    String CONFIG_PATH = "provider-config";

    /**
     * Returns the duration to wait for retrievals by roundtrip.
     *
     * @return the internal ask timeout duration.
     */
    Duration getAskTimeout();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code SignalEnrichmentFacadeByRoundTripConfig}.
     */
    enum SignalEnrichmentFacadeByRoundTripConfigValue implements KnownConfigValue {

        /**
         * The ask timeout duration: the duration to wait for retrievals by roundtrip.
         */
        ASK_TIMEOUT("ask-timeout", Duration.ofSeconds(10));

        private final String path;
        private final Object defaultValue;

        SignalEnrichmentFacadeByRoundTripConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        @Nonnull
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        @Nonnull
        public String getConfigPath() {
            return path;
        }

    }
}
