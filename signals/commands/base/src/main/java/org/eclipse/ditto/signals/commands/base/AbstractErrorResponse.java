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
package org.eclipse.ditto.signals.commands.base;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.base.ErrorRegistry;

/**
 * Abstract implementation of the {@link ErrorResponse} interface.
 *
 * @param <T> the type of the implementing class.
 */
public abstract class AbstractErrorResponse<T extends AbstractErrorResponse<T>>
        extends AbstractCommandResponse<T> implements ErrorResponse<T> {

    protected static final String FALLBACK_ID = "unknown:unknown";
    private static final String FALLBACK_ERROR_CODE = FALLBACK_ID;

    /**
     * Constructs a new {@code AbstractErrorResponse} object.
     *
     * @param responseType the type of this response.
     * @param statusCode the HTTP statusCode of this response.
     * @param dittoHeaders the headers of the CommandType which caused this CommandResponseType.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected AbstractErrorResponse(final String responseType,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {
        super(responseType, statusCode, dittoHeaders);
    }

    protected static DittoRuntimeException buildExceptionFromJson(ErrorRegistry<DittoRuntimeException> errorRegistry,
            JsonObject payload, final DittoHeaders dittoHeaders) {
        try {
            return errorRegistry.parse(payload, dittoHeaders);
        } catch (final Exception e) {
            final int status = payload.getValue(CommandResponse.JsonFields.STATUS).orElse(500);
            final String errorCode =
                    payload.getValue(DittoRuntimeException.JsonFields.ERROR_CODE).orElse(FALLBACK_ERROR_CODE);
            final String errorMessage =
                    payload.getValue(DittoRuntimeException.JsonFields.MESSAGE).orElse("An unknown error occurred");
            final String errorDescription = payload.getValue(DittoRuntimeException.JsonFields.DESCRIPTION).orElse("");
            return DittoRuntimeException.newBuilder(errorCode,
                    HttpStatusCode.forInt(status).orElse(HttpStatusCode.INTERNAL_SERVER_ERROR))
                    .message(errorMessage)
                    .description(errorDescription)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }
}
