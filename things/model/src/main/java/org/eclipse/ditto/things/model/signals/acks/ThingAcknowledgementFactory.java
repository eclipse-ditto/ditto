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
package org.eclipse.ditto.things.model.signals.acks;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;

/**
 * This class provides factory methods for getting instances of {@link Acknowledgement} in the context of Thing entity.
 *
 * @since 1.1.0
 */
@Immutable
public final class ThingAcknowledgementFactory {

    private ThingAcknowledgementFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new {@link Acknowledgement} instance.
     *
     * @param label the label of the new Acknowledgement.
     * @param thingId the ID of the affected Thing being acknowledged.
     * @param httpStatus the HTTP status of the Acknowledgement.
     * @param dittoHeaders the DittoHeaders.
     * @param payload the optional payload of the Acknowledgement.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code thingId} is empty.
     * @since 2.0.0
     */
    public static Acknowledgement newAcknowledgement(final AcknowledgementLabel label,
            final ThingId thingId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonValue payload) {

        return Acknowledgement.of(label, thingId, httpStatus, dittoHeaders, payload);
    }

    /**
     * Returns a new {@link Acknowledgement} instance.
     *
     * @param label the label of the new Acknowledgement.
     * @param thingId the ID of the affected Thing being acknowledged.
     * @param httpStatus the HTTP status of the Acknowledgement.
     * @param dittoHeaders the DittoHeaders.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code thingId} is empty.
     * @since 2.0.0
     */
    public static Acknowledgement newAcknowledgement(final AcknowledgementLabel label,
            final ThingId thingId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return newAcknowledgement(label, thingId, httpStatus, dittoHeaders, null);
    }

}
