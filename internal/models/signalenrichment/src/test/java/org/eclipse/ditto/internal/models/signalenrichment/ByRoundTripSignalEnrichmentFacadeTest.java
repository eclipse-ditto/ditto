/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.models.signalenrichment;

import java.time.Duration;

import akka.actor.ActorSelection;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link ByRoundTripSignalEnrichmentFacade}
 */
public final class ByRoundTripSignalEnrichmentFacadeTest extends AbstractSignalEnrichmentFacadeTest {

    @Override
    protected SignalEnrichmentFacade createSignalEnrichmentFacadeUnderTest(final TestKit kit, final Duration duration) {
        final ActorSelection commandHandler = ActorSelection.apply(kit.getRef(), "");
        return ByRoundTripSignalEnrichmentFacade.of(commandHandler, duration);
    }
}
