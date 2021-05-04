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
package org.eclipse.ditto.base.model.acks;

import javax.annotation.concurrent.Immutable;

/**
 * Represents a request for a domain-specific acknowledgement.
 * Acknowledgement requests can be issued together with Ditto Commands.
 * Only if all requested acknowledgements are answered within a certain timeout the overall response for the associated
 * command is sent back to the issuer of the command.
 *
 * @since 1.1.0
 */
@Immutable
public interface AcknowledgementRequest {

    /**
     * Returns an instance of AcknowledgementRequest.
     *
     * @param acknowledgementLabel the label of the returned AcknowledgementRequest.
     * @return the instance.
     * @throws NullPointerException if {@code acknowledgementLabel} is {@code null}.
     */
    static AcknowledgementRequest of(final AcknowledgementLabel acknowledgementLabel) {
        return AcknowledgementRequests.newAcknowledgementRequest(acknowledgementLabel);
    }

    /**
     * Parses the given CharSequence argument as an AcknowledgementRequest.
     *
     * @param cs the AcknowledgementRequest representation to be parsed.
     * @return the AcknowledgementRequest represented by the CharSequence argument.
     * @throws NullPointerException if {@code cs} is {@code null}.
     * @throws org.eclipse.ditto.base.model.acks.AcknowledgementRequestParseException if the given CharSequence argument cannot be parsed to an
     * AcknowledgementRequest.
     */
    static AcknowledgementRequest parseAcknowledgementRequest(final CharSequence cs) {
        return AcknowledgementRequests.parseAcknowledgementRequest(cs);
    }

    /**
     * Returns the label identifying the requested Acknowledgement.
     * May be a a built-in Ditto ACK label as well as custom one emitted by an external application.
     *
     * @return the label identifying the requested Acknowledgement.
     */
    AcknowledgementLabel getLabel();

    /**
     * Returns the parsable String representation of this AcknowledgementRequest.
     *
     * @return the parsable String representation of this AcknowledgementRequest.
     */
    @Override
    String toString();

}
