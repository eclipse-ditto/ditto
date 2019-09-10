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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.headers.conditional.ConditionalHeadersValidator;
import org.eclipse.ditto.services.utils.persistentactors.etags.AbstractConditionHeaderCheckingCommandStrategy;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * Responsible to check conditional (http) headers based on the thing's current eTag value.
 *
 * @param <C> The type of the handled command.
 */
@Immutable
public abstract class AbstractThingCommandStrategy<C extends Command<C>>
        extends AbstractConditionHeaderCheckingCommandStrategy<C, Thing, ThingId, ThingEvent> {

    private static final ConditionalHeadersValidator VALIDATOR =
            ThingsConditionalHeadersValidatorProvider.getInstance();

    protected AbstractThingCommandStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
    }

    @Override
    protected ConditionalHeadersValidator getValidator() {
        return VALIDATOR;
    }

    @Override
    public boolean isDefined(final C command) {
        throw new UnsupportedOperationException("This method is not supported by this implementation.");
    }

}
