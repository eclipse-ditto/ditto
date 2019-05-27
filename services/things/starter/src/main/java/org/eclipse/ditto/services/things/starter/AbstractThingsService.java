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

import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.base.config.DittoServiceConfigReader;
import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.slf4j.Logger;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.ActorMaterializer;

/**
 * Abstract base implementation for starting Things service with configurable actors.
 * <ul>
 * <li>Reads configuration, enhances it with cloud environment settings.</li>
 * <li>Sets up Akka actor system.</li>
 * <li>Wires up Akka HTTP Routes.</li>
 * </ul>
 */
public abstract class AbstractThingsService extends DittoService<ServiceConfigReader> {

    /**
     * Name for the Akka Actor System of the Things service.
     */
    private static final String SERVICE_NAME = "things";

    /**
     * Constructs a new {@code AbstractThingsService} object.
     *
     * @param logger the logger to be used.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected AbstractThingsService(final Logger logger) {
        super(logger, SERVICE_NAME, ThingsRootActor.ACTOR_NAME, DittoServiceConfigReader.from(SERVICE_NAME));
    }

    @Override
    protected Props getMainRootActorProps(final ServiceConfigReader configReader, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return ThingsRootActor.props(configReader, pubSubMediator, materializer,
                ThingPersistenceActorPropsFactory.getInstance(pubSubMediator));
    }

}
