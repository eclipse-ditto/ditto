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
        thingsService.start().getWhenTerminated().toCompletableFuture().join();
    }

}
