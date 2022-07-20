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
package org.eclipse.ditto.gateway.service.starter;

import org.eclipse.ditto.base.service.DittoService;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.gateway.service.util.config.GatewayConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * The Gateway service for Eclipse Ditto.
 */
@AllParametersAndReturnValuesAreNonnullByDefault
public final class GatewayService extends DittoService<GatewayConfig> {

    /**
     * Name for the Akka actor system of the Gateway service.
     */
    public static final String SERVICE_NAME = "gateway";

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayService.class);

    private GatewayService() {
        super(LOGGER, SERVICE_NAME, GatewayRootActor.ACTOR_NAME);
    }

    /**
     * Starts the Gateway service.
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        final GatewayService gatewayService = new GatewayService();
        gatewayService.start().getWhenTerminated().toCompletableFuture().join();
    }

    @Override
    protected GatewayConfig getServiceSpecificConfig(final ScopedConfig dittoConfig) {
        return DittoGatewayConfig.of(dittoConfig);
    }

    @Override
    protected Props getMainRootActorProps(final GatewayConfig gatewayConfig, final ActorRef pubSubMediator) {

        return GatewayRootActor.props(gatewayConfig, pubSubMediator);
    }

}
