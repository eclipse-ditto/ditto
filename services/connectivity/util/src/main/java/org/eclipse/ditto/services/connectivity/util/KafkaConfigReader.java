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

    /**
     * Configuration for producers needed by akka-stream-kafka.
     *
     * @see <a href="https://doc.akka.io/docs/akka-stream-kafka/current/producer.html#settings">akka-stream-kafka Producer settings</a>
     * @return internal producer configuration needed by akka-stream-kafka client.
     */
    public Config internalProducerSettings(){
        return getChildOrEmpty("producer.internal");
    }

}
