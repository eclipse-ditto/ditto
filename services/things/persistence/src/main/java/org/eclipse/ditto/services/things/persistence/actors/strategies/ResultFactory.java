/* Copyright (c) 2011-2017 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * A factory for creating
 * {@link org.eclipse.ditto.services.things.persistence.actors.strategies.ReceiveStrategy.Result} instances.
 */
final class ResultFactory {

    static ReceiveStrategy.Result newResult(final ThingEvent eventToPersist, final ThingCommandResponse response) {
        return ImmutableResult.of(eventToPersist, response);
    }

    static ReceiveStrategy.Result newResult(final DittoRuntimeException dittoRuntimeException) {
        return ImmutableResult.of(dittoRuntimeException);
    }

    static ReceiveStrategy.Result newResult(final ThingCommandResponse response) {
        return ImmutableResult.of(response);
    }

    static ReceiveStrategy.Result emptyResult() {
        return ImmutableResult.empty();
    }

    private ResultFactory() {
        throw new AssertionError();
    }
}
