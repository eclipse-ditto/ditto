/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.models.policies;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.services.models.streaming.AbstractEntityIdWithRevision;

/**
 * Represents the ID and revision of a Policy.
 */
@Immutable
public final class PolicyTag extends AbstractEntityIdWithRevision {

    private PolicyTag(final String id, final long revision) {
        super(id, revision);
    }

    private PolicyTag(final JsonObject jsonObject) {
        super(jsonObject);
    }

    /**
     * Returns a new {@link PolicyTag}.
     *
     * @param id the ID of the modified Policy.
     * @param revision the revision of the modified Policy.
     * @return a new {@link PolicyTag}.
     */
    public static PolicyTag of(final String id, final long revision) {
        return new PolicyTag(id, revision);
    }

    /**
     * Creates a new {@link PolicyTag} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new {@link PolicyTag} is to be created.
     * @return the {@link PolicyTag} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} is not valid JSON.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected format.
     */
    public static PolicyTag fromJson(final JsonObject jsonObject) {
        return new PolicyTag(jsonObject);
    }

}
