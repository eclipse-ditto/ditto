/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.acks.things;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.acks.base.AcknowledgementsJsonParser;

/**
 * This class provides factory methods for getting instances of {@link Acknowledgements} in the context of Thing entity.
 *
 * @since 1.1.0
 */
@Immutable
public final class ThingAcknowledgementsFactory {

    private ThingAcknowledgementsFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new instance of {@code Acknowledgements} combining several passed in acknowledgements with a combined
     * status code.
     *
     * @param acknowledgements the acknowledgements to be included in the result.
     * @param dittoHeaders the DittoHeaders of the result.
     * @return the Acknowledgements.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if the given {@code acknowledgements} are empty or if the entity IDs or entity
     * types of the given acknowledgements are not equal.
     */
    public static Acknowledgements newAcknowledgements(final Collection<Acknowledgement> acknowledgements,
            final DittoHeaders dittoHeaders) {

        return Acknowledgements.of(acknowledgements, dittoHeaders);
    }

    /**
     * Returns a new {@link Acknowledgements} based on the passed params, including the contained
     * {@link Acknowledgement}s.
     * <p>
     * <em>Should only be used for deserializing from a JSON representation, as
     * {@link #newAcknowledgements(Collection, DittoHeaders)} does e.g. the calculation of the correct
     * {@code httpStatus}.</em>
     * </p>
     *
     * @param entityId the ID of the affected entity being acknowledged.
     * @param acknowledgements the map of acknowledgements to be included in the result.
     * @param httpStatus the HTTP status of the combined Acknowledgements.
     * @param dittoHeaders the headers of the returned Acknowledgements instance.
     * @return the Acknowledgements.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if the given {@code acknowledgements} are empty or if the entity IDs or entity
     * types of the given acknowledgements are not equal.
     * @since 2.0.0
     */
    public static Acknowledgements newAcknowledgements(final EntityIdWithType entityId,
            final Collection<? extends Acknowledgement> acknowledgements,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return Acknowledgements.of(entityId, acknowledgements, httpStatus, dittoHeaders);
    }

    /**
     * Returns a new {@code Acknowledgements} parsed from the given JSON object.
     *
     * @param jsonObject the JSON object to be parsed.
     * @return the Acknowledgements.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} misses a required field.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contained an unexpected value type.
     */
    public static Acknowledgements fromJson(final JsonObject jsonObject) {
        final AcknowledgementsJsonParser<ThingId> jsonParser =
                AcknowledgementsJsonParser.getInstance(ThingAcknowledgementFactory.getJsonParser());
        return jsonParser.apply(jsonObject);
    }

}
