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
package org.eclipse.ditto.services.models.things.facade;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;

import akka.actor.ActorRef;

/**
 * Asynchronous interface for retrieving fixed parts of things.
 * either by request-response, by caching or by any other source of information.
 */
public interface PartialThingFacade {

    /**
     * Retrieve parts of a thing.
     *
     * @param thingId ID of the thing.
     * @param dittoHeaders Ditto headers containing authorization information.
     * @return future that completes with the parts of a thing or fails with an error.
     */
    CompletionStage<JsonObject> retrievePartialThing(ThingId thingId, DittoHeaders dittoHeaders);

    /**
     * Create a new partial-thing-facade by round-trip.
     *
     * @param commandHandler the actor who handles retrieve-thing commands.
     * @param jsonFieldSelector the selected fields of the thing.
     * @param askTimeout how long to wait for a response.
     * @return the partial-thing-facade by round-trip.
     */
    static PartialThingFacade byRoundTrip(final ActorRef commandHandler, final JsonFieldSelector jsonFieldSelector,
            final Duration askTimeout) {
        return new PartialThingFacadeByRoundTrip(jsonFieldSelector, commandHandler, askTimeout);
    }
}
