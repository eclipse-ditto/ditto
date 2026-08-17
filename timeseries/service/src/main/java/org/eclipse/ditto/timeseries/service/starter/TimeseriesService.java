/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.service.starter;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.eclipse.ditto.base.service.DittoService;
import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.timeseries.api.TimeseriesMessagingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the Timeseries service.
 */
public final class TimeseriesService extends DittoService<DittoServiceConfig> {

    /**
     * Name of the Timeseries service (used for {@code ditto.service-name} HOCON and logging).
     */
    public static final String SERVICE_NAME = TimeseriesMessagingConstants.SERVICE_NAME;

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeseriesService.class);

    private TimeseriesService() {
        super(LOGGER, SERVICE_NAME, TimeseriesRootActor.ACTOR_NAME);
    }

    /**
     * Starts the Timeseries service.
     *
     * @param args command-line arguments (currently ignored).
     */
    public static void main(final String[] args) {
        final TimeseriesService timeseriesService = new TimeseriesService();
        timeseriesService.start();
    }

    @Override
    protected DittoServiceConfig getServiceSpecificConfig(final ScopedConfig dittoConfig) {
        return DittoServiceConfig.of(dittoConfig, SERVICE_NAME);
    }

    @Override
    protected Props getMainRootActorProps(final DittoServiceConfig timeseriesConfig,
            final ActorRef pubSubMediator) {

        return TimeseriesRootActor.props(timeseriesConfig, pubSubMediator);
    }
}
