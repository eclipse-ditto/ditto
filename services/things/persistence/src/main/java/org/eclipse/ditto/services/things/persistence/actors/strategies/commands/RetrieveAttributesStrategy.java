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

import static org.eclipse.ditto.services.things.persistence.actors.strategies.commands.ResultFactory.newResult;

import java.util.Optional;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;

/**
 * This strategy handles the {@link RetrieveAttributes} command.
 */
@ThreadSafe
final class RetrieveAttributesStrategy extends AbstractCommandStrategy<RetrieveAttributes> {

    /**
     * Constructs a new {@code RetrieveAttributesStrategy} object.
     */
    RetrieveAttributesStrategy() {
        super(RetrieveAttributes.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final RetrieveAttributes command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThing();
        final Optional<Attributes> optionalAttributes = thing.getAttributes();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        if (optionalAttributes.isPresent()) {
            final Attributes attributes = optionalAttributes.get();
            final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
            final JsonObject attributesJson = selectedFields
                    .map(sf -> attributes.toJson(command.getImplementedSchemaVersion(), sf))
                    .orElseGet(() -> attributes.toJson(command.getImplementedSchemaVersion()));
            return newResult(RetrieveAttributesResponse.of(thingId, attributesJson, dittoHeaders));
        } else {
            return newResult(attributesNotFound(thingId, dittoHeaders));
        }
    }

}
