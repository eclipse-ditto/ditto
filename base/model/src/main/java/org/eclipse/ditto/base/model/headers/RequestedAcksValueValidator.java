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
package org.eclipse.ditto.base.model.headers;

import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabelInvalidException;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;

/**
 * This validator checks if a CharSequence is a JSON array of {@link AcknowledgementLabel}.
 * If validation fails, a {@link DittoHeaderInvalidException} is thrown.
 *
 * @since 1.4.0
 */
@Immutable
final class RequestedAcksValueValidator extends AbstractHeaderValueValidator {

    private static final RequestedAcksValueValidator INSTANCE = new RequestedAcksValueValidator();

    private RequestedAcksValueValidator() {
        super(JsonArray.class::equals);
    }

    /**
     * Returns an instance of {@code RequestedAcksValueValidator}.
     *
     * @return the instance.
     * @throws NullPointerException if {@code ackRequestValidator} is {@code null}.
     */
    static RequestedAcksValueValidator getInstance() {
        return INSTANCE;
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        final JsonArray jsonArray;
        try {
            jsonArray = JsonArray.of(value.toString());
        } catch (final JsonParseException e) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value, "JSON array").build();
        }

        for (final JsonValue jsonValue : jsonArray) {
            if (jsonValue.isString()) {
                try {
                    AcknowledgementLabel.of(jsonValue.asString());
                } catch (final AcknowledgementLabelInvalidException e) {
                    throw DittoHeaderInvalidException
                            .newInvalidTypeBuilder(definition, value, "JSON array of acknowledgement labels")
                            .cause(e)
                            .build();
                }
            } else {
                final String msgTemplate = "JSON array for <{0}> contained invalid acknowledgement labels.";
                final String invalidHeaderKey = definition.getKey();
                throw DittoHeaderInvalidException.newBuilder()
                        .withInvalidHeaderKey(invalidHeaderKey)
                        .message(MessageFormat.format(msgTemplate, invalidHeaderKey))
                        .build();
            }
        }

    }

}
