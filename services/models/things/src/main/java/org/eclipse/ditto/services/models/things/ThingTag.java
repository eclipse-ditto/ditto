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
package org.eclipse.ditto.services.models.things;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.things.id.ThingId;
import org.eclipse.ditto.services.models.streaming.AbstractEntityIdWithRevision;

/**
 * Represents the ID and revision of a Thing.
 */
@Immutable
public final class ThingTag extends AbstractEntityIdWithRevision<ThingId> {

    private ThingTag(final ThingId id, final long revision) {
        super(id, revision);
    }

    /**
     * Returns a new {@link ThingTag}.
     *
     * @param id the ID of the modified Thing.
     * @param revision the revision of the modified Thing.
     * @return a new {@link ThingTag}.
     */
    public static ThingTag of(final ThingId id, final long revision) {
        return new ThingTag(id, revision);
    }

    /**
     * Creates a new {@link ThingTag} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new {@link ThingTag} is to be created.
     * @return the {@link ThingTag} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} is not valid JSON.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected format.
     */
    public static ThingTag fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");
        final ThingId thingId = ThingId.of(jsonObject.getValueOrThrow(JsonFields.ID));
        final Long revision = jsonObject.getValueOrThrow(JsonFields.REVISION);

        return new ThingTag(thingId, revision);
    }

}
