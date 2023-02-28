/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.coap;

import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.config.UdpConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.http.javadsl.server.Route;
import akka.japi.pf.ReceiveBuilder;

/**
 * Wraps the {@link DittoCoapServer} created via Eclipse Californium.
 */
public final class DittoCoapServerActor extends AbstractActor {

    /**
     * The name of this Actor.
     */
    public static final String ACTOR_NAME = "coapServerActor";

    private final ThreadSafeDittoLoggingAdapter logger = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);

    private final DittoCoapServer coapServer;

    @SuppressWarnings("unused")
    private DittoCoapServerActor(final Route rootRoute) {
        Configuration.createStandardWithoutFile();
        coapServer = new DittoCoapServer(getContext().getSystem(),
                rootRoute,
                Configuration.getStandard()); // TODO TJ provide Coap Configuration via HOCON conf..
    }

    /**
     * Creates props for {@code DittoCoapServerActor}.
     *
     * @param rootRoute the root Route for delegating CoAP requests to.
     * @return the props.
     */
    public static Props props(final Route rootRoute) {
        return Props.create(DittoCoapServerActor.class, rootRoute);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchAny(m -> logger.warning("Received unknown message: <{}>", m))
                .build();
    }

    @Override
    public void preStart() {
        CoapConfig.register();
        UdpConfig.register();

        logger.info("Starting CoAP server ..");
        coapServer.start();
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        coapServer.destroy();
    }
}
