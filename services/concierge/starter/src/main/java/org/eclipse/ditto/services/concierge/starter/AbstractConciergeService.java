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
import org.eclipse.ditto.services.concierge.util.config.ConciergeConfig;
import org.slf4j.Logger;

/**
 * Abstract base implementation for starting a concierge service with configurable actors.
 */
public abstract class AbstractConciergeService<C extends ConciergeConfig> extends DittoServiceTng<C> {

    /**
     * Name for the Akka actor system.
     */
    protected static final String SERVICE_NAME = "concierge";

    protected AbstractConciergeService(final Logger logger) {
        super(logger, SERVICE_NAME, ConciergeRootActor.ACTOR_NAME);
    }

}
