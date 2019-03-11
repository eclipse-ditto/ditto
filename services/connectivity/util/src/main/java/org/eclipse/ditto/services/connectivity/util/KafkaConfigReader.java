/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.util;

import org.eclipse.ditto.services.utils.config.AbstractConfigReader;

import com.typesafe.config.Config;

/**
 * Config reader for the protocol Kafka.
 */
public final class KafkaConfigReader extends AbstractConfigReader {

    /**
     * Creates a AbstractConfigReader.
     *
     * @param config the underlying Config object.
     */
    KafkaConfigReader(final Config config) {
        super(config);
    }

    // TODO: implement the required configurations, see https://doc.akka.io/docs/akka-stream-kafka/current/producer.html#settings
}
