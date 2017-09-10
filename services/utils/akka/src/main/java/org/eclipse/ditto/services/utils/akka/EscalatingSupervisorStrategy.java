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
package org.eclipse.ditto.services.utils.akka;

import akka.actor.OneForOneStrategy;
import akka.actor.SupervisorStrategy;
import akka.actor.SupervisorStrategyConfigurator;

/**
 * SupervisorStrategyConfigurator which creates a SupervisorStrategy which escalates all Throwables.
 * If used as "guardian-supervisor-strategy" this causes a Shutdown of the complete ActorSystem when a Throwable is
 * escalated to the guardian actor.
 */
public class EscalatingSupervisorStrategy implements SupervisorStrategyConfigurator {

    /**
     * Creates a new {@code EscalatingSupervisorStrategy}.
     * Has to be a no-arg constructor!
     */
    public EscalatingSupervisorStrategy() {
        super();
    }

    @Override
    public SupervisorStrategy create() {
        return new OneForOneStrategy(SupervisorStrategy.makeDecider(throwable -> {
            return SupervisorStrategy.escalate();
        }));
    }
}
