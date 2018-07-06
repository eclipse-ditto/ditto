/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * A factory for creating
 * {@link CommandStrategy.Result} instances.
 */
final class ResultFactory {

    static CommandStrategy.Result newResult(final ThingModifiedEvent eventToPersist,
            final ThingCommandResponse response) {
        return ImmutableResult.of(eventToPersist, response);
    }

    static CommandStrategy.Result newResult(final DittoRuntimeException dittoRuntimeException) {
        return ImmutableResult.of(dittoRuntimeException);
    }

    static CommandStrategy.Result newResult(final WithDittoHeaders response) {
        return ImmutableResult.of(response);
    }

    static CommandStrategy.Result emptyResult() {
        return ImmutableResult.empty();
    }

    private ResultFactory() {
        throw new AssertionError();
    }

    static CommandStrategy.Result newResult(final ThingModifiedEvent eventToPersist,
            final ThingCommandResponse response, final boolean becomeDeleted) {
        return ImmutableResult.of(eventToPersist, response, null, becomeDeleted);
    }
}
