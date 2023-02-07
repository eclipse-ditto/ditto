/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the handling entity journal events.
 */
@Immutable
public interface EventConfig {

    /**
     * Returns the DittoHeader keys to additionally persist for events in the event journal, e.g. in order
     * to enable additional context/information for an audit log.
     *
     * @return the historical headers to persist into the event journal.
     */
    List<String> getHistoricalHeadersToPersist();


    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code SnapshotConfig}.
     */
    enum EventConfigValue implements KnownConfigValue {

        /**
         * The DittoHeaders to persist when persisting events to the journal.
         */
        HISTORICAL_HEADERS_TO_PERSIST("historical-headers-to-persist", List.of(
                DittoHeaderDefinition.ORIGINATOR.getKey(),
                DittoHeaderDefinition.CORRELATION_ID.getKey()
        ));

        private final String path;
        private final Object defaultValue;

        EventConfigValue(final String thePath, final Object theDefaultValue) {
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
