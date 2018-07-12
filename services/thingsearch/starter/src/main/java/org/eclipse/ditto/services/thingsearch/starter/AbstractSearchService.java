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
package org.eclipse.ditto.services.thingsearch.starter;

import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.base.config.DittoServiceConfigReader;
import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.eclipse.ditto.services.thingsearch.common.util.ConfigKeys;
import org.eclipse.ditto.services.thingsearch.starter.actors.SearchRootActor;
import org.slf4j.Logger;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.ActorMaterializer;

/**
 * Abstract base implementation for starting the Search service.
 * <ul>
 * <li>Reads configuration, enhances it with cloud environment settings.</li>
 * <li>Sets up Akka actor system.</li>
 * <li>Wires up Akka HTTP Routes.</li>
 * </ul>
 */
public abstract class AbstractSearchService extends DittoService<ServiceConfigReader> {

    /**
     * Name for the Search service.
     */
    private static final String SERVICE_NAME = ConfigKeys.SERVICE_NAME;

    /**
     * Constructs a new {@code AbstractSearchService} object.
     *
     * @param logger the logger to be used.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected AbstractSearchService(final Logger logger) {
        super(logger, SERVICE_NAME, SearchRootActor.ACTOR_NAME, DittoServiceConfigReader.from(SERVICE_NAME));
    }

    @Override
    protected Props getMainRootActorProps(final ServiceConfigReader configReader, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {
        return SearchRootActor.props(configReader, pubSubMediator, materializer);
    }
}
