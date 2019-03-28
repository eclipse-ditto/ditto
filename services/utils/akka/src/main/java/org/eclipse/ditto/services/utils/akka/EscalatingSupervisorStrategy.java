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
