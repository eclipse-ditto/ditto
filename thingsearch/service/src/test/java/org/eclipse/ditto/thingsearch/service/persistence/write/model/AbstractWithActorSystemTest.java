/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.model;

import javax.annotation.Nullable;

import org.junit.After;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * For tests that may create an actor system.
 */
abstract class AbstractWithActorSystemTest {

    @Nullable protected ActorSystem system;

    @After
    public void shutdown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }
}
