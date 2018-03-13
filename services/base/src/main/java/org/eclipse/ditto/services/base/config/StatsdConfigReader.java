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
package org.eclipse.ditto.services.base.config;

import java.net.InetSocketAddress;
import java.util.Optional;

import com.typesafe.config.Config;

/**
 * Statsd configurations.
 */
public final class StatsdConfigReader extends AbstractConfigReader {

    StatsdConfigReader(final Config config) {
        super(config);
    }

    /**
     * Retrieve statsd hostname and port number.
     *
     * @return An unresolved address if both hostname and port number are configured and an empty optional otherwise.
     */
    public Optional<InetSocketAddress> getStatsd() {
        return getIfPresent("hostname", config::getString)
                .flatMap(hostname -> getIfPresent("port", config::getInt)
                        .map(port -> InetSocketAddress.createUnresolved(hostname, port)));
    }
}
