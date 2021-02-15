/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.devops;

import org.eclipse.ditto.model.devops.LoggingFacade;

import akka.actor.Props;

/**
 * The implementation of {@link org.eclipse.ditto.services.utils.devops.DevOpsCommandsActor}.
 */
public final class DittoDevOpsCommandsActor extends DevOpsCommandsActor {

    @SuppressWarnings("unused")
    private DittoDevOpsCommandsActor(final LoggingFacade loggingFacade, final String serviceName, final String instance) {
        super(loggingFacade, serviceName, instance);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param loggingFacade a facade providing logging functionality.
     * @param serviceName name of the microservice.
     * @param instance instance number of the microservice instance.
     * @return the Akka configuration Props object.
     */
    public static Props props(final LoggingFacade loggingFacade, final String serviceName, final String instance) {
        return Props.create(DittoDevOpsCommandsActor.class, loggingFacade, serviceName, instance);
    }

}
