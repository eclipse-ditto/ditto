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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.function.Predicate;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Customizable converter from publisher exceptions to {@link Acknowledgement}s.
 *
 * @since 1.3.0
 */
public abstract class ExceptionToAcknowledgementConverter {

    private static final Predicate<JsonField> IS_NOT_STATUS_PREDICATE = jsonField -> {
        final JsonPointer statusFieldDefinitionPointer = DittoRuntimeException.JsonFields.STATUS.getPointer();
        final JsonPointer jsonFieldKeyAsPointer = jsonField.getKey().asPointer();
        return !statusFieldDefinitionPointer.equals(jsonFieldKeyAsPointer);
    };

    /**
     * Constructs a new ExceptionToAcknowledgementConverter object.
     */
    protected ExceptionToAcknowledgementConverter() {
        super();
    }

    /**
     * Converts the specified exception into an Acknowledgement.
     * This method converts the exception differently depending whether it is a {@link DittoRuntimeException} or not.
     *
     * @param exception the exception to be converted.
     * @param label the desired acknowledgement label.
     * @param entityId the entity ID.
     * @param dittoHeaders the DittoHeaders of the sending context.
     * @return the acknowledgement.
     */
    public final Acknowledgement convertException(final Throwable exception,
            final AcknowledgementLabel label,
            final EntityId entityId,
            final DittoHeaders dittoHeaders) {

        checkNotNull(exception, "exception");
        checkNotNull(label, "label");
        checkNotNull(entityId, "entityId");
        checkNotNull(dittoHeaders, "dittoHeaders");

        final Acknowledgement result;
        if (exception instanceof DittoRuntimeException dittoRuntimeException) {
            result = convertDittoRuntimeException(dittoRuntimeException, label, entityId, dittoHeaders);
        } else {
            result = convertGenericException(exception, label, entityId, dittoHeaders);
        }
        return result;
    }

    /**
     * Converts a DittoRuntimeException to an Acknowledgement.
     * By default, the payload is the JSON representation of the DittoRuntimeException excluding the field "status",
     * which is used as the status code of the acknowledgement itself.
     *
     * @param dittoRuntimeException the DittoRuntimeException.
     * @param label the desired acknowledgement label.
     * @param entityId the entity ID.
     * @param dittoHeaders the DittoHeaders of the sending context.
     * @return acknowledgement for the DittoRuntimeException.
     */
    private static Acknowledgement convertDittoRuntimeException(final DittoRuntimeException dittoRuntimeException,
            final AcknowledgementLabel label,
            final EntityId entityId,
            final DittoHeaders dittoHeaders) {

        final HttpStatus status = dittoRuntimeException.getHttpStatus();
        final JsonObject payload = getPayload(dittoRuntimeException);

        return Acknowledgement.of(label, entityId, status, dittoHeaders, payload);
    }

    private static JsonObject getPayload(final DittoRuntimeException dittoRuntimeException) {
        return dittoRuntimeException.toJson(IS_NOT_STATUS_PREDICATE);
    }

    /**
     * Converts a generic exception into an Acknowledgement.
     *
     * @param exception the generic exception (non-DittoRuntimeException).
     * @param label the desired acknowledgement label.
     * @param entityId the entity ID.
     * @param dittoHeaders the DittoHeaders of the sending context.
     * @return acknowledgement for the generic exception.
     */
    private Acknowledgement convertGenericException(final Throwable exception,
            final AcknowledgementLabel label,
            final EntityId entityId,
            final DittoHeaders dittoHeaders) {

        final HttpStatus status = getHttpStatusForGenericException(exception);
        final JsonObject payload = getPayload(exception);

        return Acknowledgement.of(label, entityId, status, dittoHeaders, payload);
    }

    /**
     * Gets the acknowledgement HTTP status for the specified exception.
     *
     * @param exception the generic exception (non-DittoRuntimeException).
     * @return the HTTP status of the converted acknowledgement.
     */
    protected abstract HttpStatus getHttpStatusForGenericException(Throwable exception);

    private static JsonObject getPayload(final Throwable exception) {
        return JsonObject.newBuilder()
                .set(DittoRuntimeException.JsonFields.MESSAGE, getMessageForGenericException(exception))
                .set(DittoRuntimeException.JsonFields.DESCRIPTION, getDescriptionForGenericException(exception))
                .build();
    }

    private static String getMessageForGenericException(final Throwable exception) {
        return MessageFormat.format("Encountered <{0}>.", exception.getClass().getSimpleName());
    }

    /**
     * Get the description of a generic exception.
     *
     * @param exception the generic exception (non-DittoRuntimeException).
     * @return the description in the payload of the converted acknowledgement.
     */
    private static String getDescriptionForGenericException(final Throwable exception) {
        var message = exception.getMessage();
        if (null == message) {
            message = "Unknown error.";
        }
        final var cause = exception.getCause();
        final String result;
        if (null != cause) {
            result = MessageFormat.format("{0} - Caused by <{1}>.", message, cause.getClass().getSimpleName());
        } else {
            result = message;
        }
        return result;
    }

}
