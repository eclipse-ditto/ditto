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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;

/**
 * This strategy handles the {@link RetrieveAttributes} command.
 */
@Immutable
final class RetrieveAttributesStrategy extends AbstractCommandStrategy<RetrieveAttributes> {

    /**
     * Constructs a new {@code RetrieveAttributesStrategy} object.
     */
    RetrieveAttributesStrategy() {
        super(RetrieveAttributes.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveAttributes command) {
        final String thingId = context.getThingId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return getThingOrThrow(thing).getAttributes()
                .map(attributes -> getAttributesJson(attributes, command))
                .map(attributesJson -> RetrieveAttributesResponse.of(thingId, attributesJson, dittoHeaders))
                .map(ResultFactory::newResult)
                .orElseGet(() -> ResultFactory.newResult(ExceptionFactory.attributesNotFound(thingId, dittoHeaders)));
    }

    private static JsonObject getAttributesJson(final Attributes attributes, final RetrieveAttributes command) {
        return command.getSelectedFields()
                .map(selectedFields -> attributes.toJson(command.getImplementedSchemaVersion(), selectedFields))
                .orElseGet(() -> attributes.toJson(command.getImplementedSchemaVersion()));
    }

}
