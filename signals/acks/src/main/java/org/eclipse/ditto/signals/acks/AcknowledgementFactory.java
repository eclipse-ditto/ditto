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
package org.eclipse.ditto.signals.acks;

import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * The entry point for creating instances around (end-to-end) acknowledgements.
 *
 * @since 1.1.0
 */
final class AcknowledgementFactory {

    private AcknowledgementFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new {@code Acknowledgement} for the specified parameters.
     *
     * @param label the label of the new Acknowledgement.
     * @param entityId the ID of the affected entity being acknowledged.
     * @param statusCode the status code (HTTP semantics) of the Acknowledgement.
     * @param dittoHeaders the DittoHeaders.
     * @param payload the optional payload of the Acknowledgement.
     * @return the Acknowledgement.
     * @throws NullPointerException if one of the required parameters was {@code null}.
     * @throws IllegalArgumentException if {@code entityId} is empty.
     */
    public static Acknowledgement newAcknowledgement(final AcknowledgementLabel label,
            final CharSequence entityId,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonValue payload) {

        return ImmutableAcknowledgement.of(label, entityId, statusCode, payload, dittoHeaders);
    }

    /**
     * Returns a new {@code Acknowledgement} parsed from the specified {@code jsonObject}.
     *
     * @param jsonObject the JSON object.
     * @return the Acknowledgement.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected 'Acknowledgement' format.
     */
    public static Acknowledgement acknowledgementFromJson(final JsonObject jsonObject) {
        return ImmutableAcknowledgement.fromJson(jsonObject);
    }

    /**
     * Returns a new {@code Acknowledgements} combining several passed in {@code Acknowledgement}s with a combined
     * {@code statusCode}.
     *
     * @param entityId the ID of the affected entity being acknowledged.
     * @param statusCode the aggregated status code (HTTP semantics) of the Acknowledgements.
     * @param acknowledgements the map of {@link Acknowledgement}s to be included in the aggregated Acknowledgements.
     * @param dittoHeaders the DittoHeaders of the Acknowledgements.
     * @return the Acknowledgements.
     * @throws NullPointerException if one of the required parameters was {@code null}.
     * @throws IllegalArgumentException if {@code entityId} is empty.
     */
    public static Acknowledgements newAcknowledgements(final CharSequence entityId,
            final HttpStatusCode statusCode,
            final Map<AcknowledgementLabel, Acknowledgement> acknowledgements,
            final DittoHeaders dittoHeaders) {

        return ImmutableAcknowledgements.of(entityId, statusCode, acknowledgements, dittoHeaders);
    }

    /**
     * Returns a new {@code Acknowledgements} parsed from the specified {@code jsonObject}.
     *
     * @param jsonObject the JSON object.
     * @return the Acknowledgements.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected 'Acknowledgements' format.
     */
    public static Acknowledgements acknowledgementsFromJson(final JsonObject jsonObject) {
        return ImmutableAcknowledgements.fromJson(jsonObject);
    }

}
