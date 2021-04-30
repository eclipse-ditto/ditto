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
package org.eclipse.ditto.concierge.service.starter;

import org.eclipse.ditto.base.service.DittoService;
import org.eclipse.ditto.concierge.service.common.ConciergeConfig;
import org.eclipse.ditto.concierge.service.common.DittoConciergeConfig;
import org.eclipse.ditto.concierge.service.starter.actors.ConciergeRootActor;
import org.eclipse.ditto.concierge.service.starter.proxy.DefaultEnforcerActorFactory;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * The Concierge service for Eclipse Ditto.
 */
public final class ConciergeService extends DittoService<ConciergeConfig> {

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
    protected Props getMainRootActorProps(final ConciergeConfig serviceSpecificConfig, final ActorRef pubSubMediator) {

        return ConciergeRootActor.props(serviceSpecificConfig, pubSubMediator, new DefaultEnforcerActorFactory());
    }

}
