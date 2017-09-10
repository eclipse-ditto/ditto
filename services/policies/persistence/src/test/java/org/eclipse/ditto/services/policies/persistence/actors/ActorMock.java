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
package org.eclipse.ditto.services.policies.persistence.actors;

import akka.actor.Props;
import akka.actor.UntypedActor;

/**
 * Mock implementation of an Akka Actor for testing.
 */
public final class ActorMock extends UntypedActor {

    /**
     * Creates a new dummy actor which does nothing for testing purpose.
     *
     * @return the dummy actor.
     */
    public static Props props() {
        return Props.create(ActorMock.class, ActorMock::new);
    }

    @Override
    public void onReceive(final Object message) throws Exception {

    }
}
