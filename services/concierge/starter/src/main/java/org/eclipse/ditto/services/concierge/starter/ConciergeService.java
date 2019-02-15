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

import org.eclipse.ditto.services.concierge.starter.actors.ConciergeRootActor;
import org.eclipse.ditto.services.concierge.starter.proxy.DefaultEnforcerActorFactoryTng;
import org.eclipse.ditto.services.concierge.util.config.ConciergeConfig;
import org.eclipse.ditto.services.concierge.util.config.DittoConciergeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.ActorMaterializer;

/**
 * The Concierge service for Eclipse Ditto.
 */
public final class ConciergeService extends AbstractConciergeService<ConciergeConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConciergeService.class);

    private ConciergeService() {
        super(LOGGER);
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
    protected ConciergeConfig getServiceSpecificConfig(final Config dittoConfig) {
        return DittoConciergeConfig.of(dittoConfig);
    }

    @Override
    protected Props getMainRootActorProps(final ConciergeConfig serviceSpecificConfig, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return ConciergeRootActor.props(serviceSpecificConfig, pubSubMediator, new DefaultEnforcerActorFactoryTng(),
                materializer);
    }

}
