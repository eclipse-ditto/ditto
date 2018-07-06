/* Copyright (c) 2011-2017 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute} command.
 */
@NotThreadSafe
final class RetrieveAttributeStrategy extends AbstractCommandStrategy<RetrieveAttribute> {

    /**
     * Constructs a new {@code RetrieveAttributeStrategy} object.
     */
    RetrieveAttributeStrategy() {
        super(RetrieveAttribute.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final RetrieveAttribute command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final CommandStrategy.Result result;

        final Optional<Attributes> optionalAttributes = context.getThing().getAttributes();
        if (optionalAttributes.isPresent()) {
            final Attributes attributes = optionalAttributes.get();
            final JsonPointer jsonPointer = command.getAttributePointer();
            final Optional<JsonValue> jsonValue = attributes.getValue(jsonPointer);
            if (jsonValue.isPresent()) {
                final ThingQueryCommandResponse response =
                        RetrieveAttributeResponse.of(context.getThingId(), jsonPointer, jsonValue.get(), dittoHeaders);
                result = ImmutableResult.of(response);
            } else {
                result = ImmutableResult.of(attributeNotFound(context.getThingId(), jsonPointer, dittoHeaders));
            }
        } else {
            result = ImmutableResult.of(attributesNotFound(context.getThingId(), dittoHeaders));
        }

        return result;
    }

}
