/* Copyright (c) 2011-2017 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;

/**
 * This strategy handles any messages for a previous deleted Thing.
 */
@NotThreadSafe
public final class ThingNotFoundStrategy extends AbstractReceiveStrategy<Object> {

    /**
     * Constructs a new {@code ThingNotFoundStrategy} object.
     */
    public ThingNotFoundStrategy() {
        super(Object.class);
    }

    @Override
    protected Result doApply(final Context context, final Object message) {
        final ThingNotAccessibleException.Builder builder =
                ThingNotAccessibleException.newBuilder(context.getThingId());
        if (message instanceof WithDittoHeaders) {
            builder.dittoHeaders(((WithDittoHeaders) message).getDittoHeaders());
        }
        return ImmutableResult.of(builder.build());
    }

}
