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

import java.util.function.Function;

import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.concierge.starter.actors.ConciergeRootActor;
import org.eclipse.ditto.services.concierge.util.config.AbstractConciergeConfigReader;
import org.slf4j.Logger;

import com.typesafe.config.Config;

/**
 * Abstract base implementation for starting a concierge service with configurable actors.
 */
public abstract class AbstractConciergeService<C extends AbstractConciergeConfigReader> extends DittoService<C> {

    /**
     * Name for the Akka actor system.
     */
    protected static final String SERVICE_NAME = "concierge";

    protected AbstractConciergeService(final Logger logger, final Function<Config, C> configReaderCreator) {
        super(logger, SERVICE_NAME, ConciergeRootActor.ACTOR_NAME, configReaderCreator);
    }

}
