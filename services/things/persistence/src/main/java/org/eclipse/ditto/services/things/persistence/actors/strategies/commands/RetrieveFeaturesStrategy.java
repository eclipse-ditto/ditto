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

import java.util.Optional;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures} command.
 */
@ThreadSafe
final class RetrieveFeaturesStrategy extends AbstractCommandStrategy<RetrieveFeatures> {

    /**
     * Constructs a new {@code RetrieveFeaturesStrategy} object.
     */
    RetrieveFeaturesStrategy() {
        super(RetrieveFeatures.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final RetrieveFeatures command) {
        final CommandStrategy.Result result;

        final Optional<Features> optionalFeatures = context.getThing().getFeatures();
        if (optionalFeatures.isPresent()) {
            final Features features = optionalFeatures.get();
            final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
            final JsonObject featuresJson = selectedFields
                    .map(sf -> features.toJson(command.getImplementedSchemaVersion(), sf))
                    .orElseGet(() -> features.toJson(command.getImplementedSchemaVersion()));
            final RetrieveFeaturesResponse response =
                    RetrieveFeaturesResponse.of(context.getThingId(), featuresJson, command.getDittoHeaders());
            result = ImmutableResult.of(response);
        } else {
            final DittoRuntimeException exception =
                    featuresNotFound(context.getThingId(), command.getDittoHeaders());
            result = ImmutableResult.of(exception);
        }

        return result;
    }

}
