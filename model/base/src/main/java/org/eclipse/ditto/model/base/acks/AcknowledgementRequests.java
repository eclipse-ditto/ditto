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
package org.eclipse.ditto.model.base.acks;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Container class for AcknowledgementRequests.
 * Not part of the public API.
 *
 * @since 1.1.0
 */
@Immutable
final class AcknowledgementRequests {

    private AcknowledgementRequests() {
        throw new AssertionError();
    }

    /**
     * Returns a new instance of AcknowledgementRequest.
     *
     * @param acknowledgementLabel the label of the new AcknowledgementRequest.
     * @return the instance.
     * @throws NullPointerException if {@code acknowledgementLabel} is {@code null}.
     */
    public static ImmutableAcknowledgementRequest newAcknowledgementRequest(
            final AcknowledgementLabel acknowledgementLabel) {

        return ImmutableAcknowledgementRequest.getInstance(acknowledgementLabel);
    }

    /**
     * Parses the given CharSequence argument as an AcknowledgementRequest.
     *
     * @param ackRequestRepresentation the AcknowledgementRequest representation to be parsed.
     * @return the AcknowledgementRequest represented by the CharSequence argument.
     * @throws NullPointerException if {@code ackRequestRepresentation} is {@code null}.
     * @throws AcknowledgementRequestParseException if {@code ackRequestRepresentation} could not be parsed to an
     * AcknowledgementRequest.
     */
    public static ImmutableAcknowledgementRequest parseAcknowledgementRequest(
            final CharSequence ackRequestRepresentation) {

        checkNotNull(ackRequestRepresentation, "ackRequestRepresentation");
        return newAcknowledgementRequest(tryToParseAcknowledgementLabel(ackRequestRepresentation));
    }

    private static AcknowledgementLabel tryToParseAcknowledgementLabel(final CharSequence cs) {
        try {
            return AcknowledgementLabel.of(cs);
        } catch (final AcknowledgementLabelInvalidException e) {
            throw new AcknowledgementRequestParseException(cs, e, DittoHeaders.empty());
        }
    }

}
