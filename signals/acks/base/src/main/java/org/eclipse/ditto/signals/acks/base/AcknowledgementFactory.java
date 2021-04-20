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
package org.eclipse.ditto.signals.acks.base;

import java.util.Collection;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * The entry point for creating instances around (end-to-end) acknowledgements.
 *
 * @since 1.1.0
 */
@Immutable
final class AcknowledgementFactory {

    private AcknowledgementFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new {@code Acknowledgement} for the specified parameters.
     *
     * @param label the label of the new Acknowledgement.
     * @param entityId the ID of the affected entity being acknowledged.
     * @param httpStatus the HTTP status of the Acknowledgement.
     * @param dittoHeaders the DittoHeaders.
     * @param payload the optional payload of the Acknowledgement.
     * @return the Acknowledgement.
     * @throws NullPointerException if one of the required parameters was {@code null}.
     * @since 2.0.0
     */
    @SuppressWarnings("squid:S3252")
    public static Acknowledgement newAcknowledgement(final AcknowledgementLabel label,
            final EntityId entityId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonValue payload) {

        return ImmutableAcknowledgement.of(label, entityId, httpStatus, dittoHeaders, payload);
    }

    /**
     * Returns a new {@link Acknowledgements} combining several passed in {@link Acknowledgement}s.
     *
     * @param acknowledgements the map of acknowledgements to be included in the result.
     * @param dittoHeaders the headers of the returned Acknowledgements instance.
     * @return the Acknowledgements.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if the given {@code acknowledgements} are empty or if the entity IDs or entity
     * types of the given acknowledgements are not equal.
     */
    public static Acknowledgements newAcknowledgements(final Collection<? extends Acknowledgement> acknowledgements,
            final DittoHeaders dittoHeaders) {

        return ImmutableAcknowledgements.of(acknowledgements, dittoHeaders);
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
    public static Acknowledgements newAcknowledgements(final EntityId entityId,
            final Collection<? extends Acknowledgement> acknowledgements,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return ImmutableAcknowledgements.of(entityId, acknowledgements, httpStatus, dittoHeaders);
    }

    /**
     * Returns an empty instance of {@link Acknowledgements} for the given entity ID.
     *
     * @param entityId the entity ID for which no acknowledgements were received at all.
     * @param dittoHeaders the headers of the returned Acknowledgements instance.
     * @return the Acknowledgements.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Acknowledgements emptyAcknowledgements(final EntityId entityId,
            final DittoHeaders dittoHeaders) {

        return ImmutableAcknowledgements.empty(entityId, dittoHeaders);
    }

}
