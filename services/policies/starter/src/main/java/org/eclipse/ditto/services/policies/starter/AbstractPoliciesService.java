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
package org.eclipse.ditto.services.policies.starter;

import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.base.config.DittoServiceConfigReader;
import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.slf4j.Logger;

/**
 * Abstract base implementation for starting Policies service with configurable actors.
 * <ul>
 * <li>Reads configuration, enhances it with cloud environment settings</li>
 * <li>Sets up ActorSystem</li>
 * </ul>
 */
public abstract class AbstractPoliciesService extends DittoService<ServiceConfigReader> {

    /**
     * Name for the Akka Actor System of the Policies Service.
     */
    private static final String SERVICE_NAME = "policies";

    /**
     * Constructs a new {@code AbstractPoliciesService}.
     *
     * @param logger the logger to use.
     */
    protected AbstractPoliciesService(final Logger logger) {
        super(logger, SERVICE_NAME, PoliciesRootActor.ACTOR_NAME, DittoServiceConfigReader.from(SERVICE_NAME));
    }

}
