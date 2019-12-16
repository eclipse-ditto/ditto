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
package org.eclipse.ditto.services.gateway.endpoints.utils;

import java.time.Duration;

import org.eclipse.ditto.services.models.things.ThingEnrichingFacade;
import org.eclipse.ditto.services.models.things.ThingEnrichingFacadeByRoundTrip;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.http.javadsl.model.HttpRequest;

/**
 * Creator of thing-enriching facade by round-trip.
 */
public final class ByRoundTripFacadeProvider implements ThingEnrichingFacadeProvider {

    private final ActorRef commandHandler;
    private final Duration askTimeout;

    public ByRoundTripFacadeProvider(final ActorRef commandHandler, final Config config) {
        this.commandHandler = commandHandler;
        askTimeout = config.getDuration("ask-timeout");
    }

    @Override
    public ThingEnrichingFacade createFacade(final HttpRequest request) {
        return ThingEnrichingFacadeByRoundTrip.of(commandHandler, askTimeout);
    }
}
