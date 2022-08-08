/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.service;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Root actor starter that does purposefully nothing.
 */
public final class NoOpRootActorStarter implements RootActorStarter {

    /**
     * @param actorSystem the actor system in which to load the extension.
     */
    public NoOpRootActorStarter(final ActorSystem actorSystem, final Config config) {
        //No-Op because extensions need a constructor accepting an actorSystem
    }

    @Override
    public void execute() {
        // Do nothing.
    }

}

