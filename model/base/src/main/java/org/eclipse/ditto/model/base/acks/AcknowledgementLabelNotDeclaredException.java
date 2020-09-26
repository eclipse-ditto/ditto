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

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;

/**
 * Thrown if a WS/connectivity connection sends an acknowledgement not declared for the connection.
 *
 * @since 1.3.0
 */
@Immutable
@JsonParsableException(errorCode = AcknowledgementLabelNotDeclaredException.ERROR_CODE)
public final class AcknowledgementLabelNotDeclaredException extends DittoRuntimeException
        implements AcknowledgementException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "label.not.declared";

    private static final String MESSAGE_TEMPLATE =
            "Cannot send acknowledgement with label <{0}>, which is not declared!";

    private static final String DEFAULT_DESCRIPTION =
            "Each connection may only send acknowledgements whose label matches one declared for the connection.";

    private AcknowledgementLabelNotDeclaredException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE,
                HttpStatusCode.BAD_REQUEST,
                dittoHeaders,
                message,
                description,
                cause,
                href);
    }

    /**
     * Create an {@code AcknowledgementLabelNotDeclaredException} object.
     *
     * @param label the invalid label.
     * @param dittoHeaders the headers of the exception.
     * @return the exception.
     */
    public static AcknowledgementLabelNotDeclaredException of(final CharSequence label,
            final DittoHeaders dittoHeaders) {

        return new AcknowledgementLabelNotDeclaredException(dittoHeaders, MessageFormat.format(MESSAGE_TEMPLATE, label),
                DEFAULT_DESCRIPTION, null, null);
    }

    /**
     * Constructs a new {@code AcknowledgementLabelNotDeclaredException} object with the exception message extracted
     * from the given JSON object.
     *
     * @param jsonObject the JSON object representation of the returned exception.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new exception.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} misses a required field.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contained an unexpected value type.
     */
    public static AcknowledgementLabelNotDeclaredException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new AcknowledgementLabelNotDeclaredException(dittoHeaders,
                readMessage(jsonObject),
                readDescription(jsonObject).orElse(DEFAULT_DESCRIPTION),
                null,
                readHRef(jsonObject).orElse(null));
    }

}
