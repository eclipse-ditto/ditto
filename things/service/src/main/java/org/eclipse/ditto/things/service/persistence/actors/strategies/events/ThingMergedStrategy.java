/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.events;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.events.ThingMerged} event.
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
            final JsonObject jsonObject = thing.toJson(FieldType.all());
            final JsonObject mergePatch = JsonFactory.newObject(event.getResourcePath(), event.getValue());
            final JsonObject mergedJson = JsonFactory.mergeJsonValues(mergePatch, jsonObject).asObject();
            return ThingsModelFactory.newThingBuilder(mergedJson)
                    .setRevision(revision)
                    .setModified(event.getTimestamp().orElse(null))
                    .setLifecycle(ThingLifecycle.ACTIVE)
                    .setMetadata(mergeMetadata(thing, event))
                    .build();
        } else {
            return null;
        }
    }

}
