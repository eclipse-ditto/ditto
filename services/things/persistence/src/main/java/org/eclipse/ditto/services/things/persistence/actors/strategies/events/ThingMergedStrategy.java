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
package org.eclipse.ditto.services.things.persistence.actors.strategies.events;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.events.things.ThingMerged;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.events.things.ThingMerged} event.
 */
@Immutable
final class ThingMergedStrategy extends AbstractThingEventStrategy<ThingMerged> {

    protected ThingMergedStrategy() {
        super();
    }

    @Nullable
    @Override
    public Thing handle(final ThingMerged event, @Nullable final Thing thing, final long revision) {
        if (null != thing) {
            final ThingBuilder.FromCopy thingBuilder = thing.toBuilder()
                    .setRevision(revision)
                    .setModified(event.getTimestamp().orElse(null))
                    .setLifecycle(ThingLifecycle.ACTIVE)
                    .setMetadata(mergeMetadata(thing, event));

            final JsonObject existingJson = thing.toJson();

            // TODO move this to model?
            final JsonObject mergePatch;
            if (event.getResourcePath().isEmpty()) {
                if (event.getValue().isObject()) {
                    mergePatch = event.getValue().asObject();
                } else {
                    // TODO exception
                    throw new IllegalArgumentException("Patch must be a JsonObject if path is empty.");
                }
            } else {
                mergePatch = JsonObject.newBuilder().set(event.getResourcePath(), event.getValue()).build();
            }

            final JsonValue mergedJson = JsonFactory.newObjectWithoutNullValues(existingJson, mergePatch);
            final Thing mergedThing = ThingsModelFactory.newThing(mergedJson.asObject());

            mergedThing.getPolicyEntityId().ifPresent(thingBuilder::setPolicyId);
            mergedThing.getAttributes().ifPresent(thingBuilder::setAttributes);
            mergedThing.getDefinition().ifPresent(thingBuilder::setDefinition);
            mergedThing.getFeatures().ifPresent(thingBuilder::setFeatures);

            return thingBuilder.build();
        } else {
            return null;
        }
    }
}
