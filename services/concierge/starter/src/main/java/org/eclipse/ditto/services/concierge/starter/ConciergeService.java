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
package org.eclipse.ditto.services.concierge.starter;

import org.eclipse.ditto.services.concierge.starter.actors.ConciergeRootActor;
import org.eclipse.ditto.services.concierge.starter.proxy.DefaultEnforcerActorFactory;
import org.eclipse.ditto.services.concierge.util.config.ConciergeConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.ActorMaterializer;

/**
 * The Concierge service for Eclipse Ditto.
 */
public final class ConciergeService extends AbstractConciergeService<ConciergeConfigReader> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConciergeService.class);

    private ConciergeService() {
        super(LOGGER, ConciergeConfigReader.from(SERVICE_NAME));
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
    protected Props getMainRootActorProps(final ConciergeConfigReader configReader, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return ConciergeRootActor.props(configReader, pubSubMediator, new DefaultEnforcerActorFactory(),
                materializer);
    }

}
