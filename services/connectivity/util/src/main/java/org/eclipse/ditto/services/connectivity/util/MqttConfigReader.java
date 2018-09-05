/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.connectivity.util;

import org.eclipse.ditto.services.base.config.AbstractConfigReader;

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
