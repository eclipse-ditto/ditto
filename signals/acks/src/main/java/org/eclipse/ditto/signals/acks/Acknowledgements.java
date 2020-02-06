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

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabelInvalidException;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabels;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * The entry point for creating instances around (end-to-end) acknowledgements.
 *
 * @since 1.1.0
 */
public final class Acknowledgements {

    private Acknowledgements() {
        throw new AssertionError();
    }

    /**
     * Returns a new AcknowledgementLabel for the given character sequence.
     *
     * @param label the character sequence value of the AcknowledgementLabel to be created.
     * @return a new AcknowledgementLabel with {@code label} as its value.
     * @throws NullPointerException if {@code label} is {@code null}.
     * @throws AcknowledgementLabelInvalidException if {@code label} did not match the regex
     * {@link AcknowledgementLabels#ACK_LABEL_PATTERN}.
     */
    public static AcknowledgementLabel newLabel(final CharSequence label) {
        return AcknowledgementLabels.newLabel(label);
    }

    /**
     * Returns a new {@code Acknowledgement} for the specified parameters.
     *
     * @param label the label of the new Acknowledgement.
     * @param entityId the ID of the affected entity being acknowledged.
     * @param statusCode the status code (HTTP semantics) of the Acknowledgement.
     * @param payload the optional payload of the Acknowledgement.
     * @param dittoHeaders the DittoHeaders.
     * @return the ImmutableAcknowledgement.
     * @throws NullPointerException if one of the required parameters was {@code null}.
     * @throws IllegalArgumentException if {@code entity} is empty.
     */
    public static Acknowledgement newAcknowledgement(final AcknowledgementLabel label,
            final CharSequence entityId,
            final int statusCode,
            @Nullable final JsonValue payload,
            final DittoHeaders dittoHeaders) {

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

}
