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
package org.eclipse.ditto.services.concierge.starter;

import org.eclipse.ditto.services.base.DittoServiceTng;
import org.eclipse.ditto.services.concierge.starter.actors.ConciergeRootActor;
import org.eclipse.ditto.services.concierge.starter.proxy.DefaultEnforcerActorFactory;
import org.eclipse.ditto.services.concierge.starter.config.ConciergeConfig;
import org.eclipse.ditto.services.concierge.starter.config.DittoConciergeConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.ActorMaterializer;

/**
 * The Concierge service for Eclipse Ditto.
 */
public final class ConciergeService extends DittoServiceTng<ConciergeConfig> {

    /**
     * Name of Ditto's Concierge service.
     */
    public static final String SERVICE_NAME = "concierge";

    private static final Logger LOGGER = LoggerFactory.getLogger(ConciergeService.class);

    private ConciergeService() {
        super(LOGGER, SERVICE_NAME, ConciergeRootActor.ACTOR_NAME);
    }

    /**
     * Starts the service.
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        final ConciergeService service = new ConciergeService();
        service.start().getWhenTerminated().toCompletableFuture().join();
    }

    @Override
    protected ConciergeConfig getServiceSpecificConfig(final ScopedConfig dittoConfig) {
        return DittoConciergeConfig.of(dittoConfig);
    }

    @Override
    protected Props getMainRootActorProps(final ConciergeConfig serviceSpecificConfig, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return ConciergeRootActor.props(serviceSpecificConfig, pubSubMediator, new DefaultEnforcerActorFactory(),
                materializer);
    }

}
