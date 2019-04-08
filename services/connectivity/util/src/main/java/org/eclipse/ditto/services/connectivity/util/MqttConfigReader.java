/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.util;

import org.eclipse.ditto.services.utils.config.AbstractConfigReader;

import com.typesafe.config.Config;

/**
 * Config reader for the protocol MQTT.
 */
public final class MqttConfigReader extends AbstractConfigReader {

    /**
     * Creates a AbstractConfigReader.
     *
     * @param config the underlying Config object.
     */
    MqttConfigReader(final Config config) {
        super(config);
    }

    /**
     * Maximum number of buffered messages for each MQTT source. The default value is 8.
     *
     * @return maximum number of buffered messages.
     */
    public int sourceBufferSize() {
        return getIfPresent("source-buffer-size", config::getInt).orElse(8);
    }
}
