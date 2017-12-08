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
package org.eclipse.ditto.services.things.starter;

import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the Things Service.
 * <ul>
 * <li>Reads configuration, enhances it with cloud environment settings</li>
 * <li>Sets up ActorSystem</li>
 * <li>Wires up Akka HTTP Routes</li>
 * </ul>
 */
public final class ThingsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThingsService.class);

    /**
     * Name for the Cluster of the Things Service.
     */
    private static final String CLUSTER_NAME = "ditto-cluster";

    /**
     * Name for the Akka Actor System of the Things Service.
     */
    static final String SERVICE_NAME = "things";

    private ThingsService() {
        // no-op
    }

    /**
     * Starts the ThingsService.
     *
     * @param args CommandLine arguments
     */
    public static void main(final String... args) {
        new ThingsApplication(LOGGER, CLUSTER_NAME, SERVICE_NAME, DittoThingsActorsCreator::new)
                .start(ConfigUtil.determineConfig(SERVICE_NAME));
    }
}
