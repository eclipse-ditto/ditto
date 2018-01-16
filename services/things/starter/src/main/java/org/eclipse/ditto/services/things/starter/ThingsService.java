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

import org.eclipse.ditto.services.things.persistence.snapshotting.DittoThingSnapshotter;
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
public final class ThingsService extends AbstractThingsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThingsService.class);

    private ThingsService() {
        super(LOGGER, DittoThingSnapshotter::getInstance);
    }

    /**
     * Starts the Things service.
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        final ThingsService thingsService = new ThingsService();
        thingsService.start();
    }

}
