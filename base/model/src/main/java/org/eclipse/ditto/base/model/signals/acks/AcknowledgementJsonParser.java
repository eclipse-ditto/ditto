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
package org.eclipse.ditto.base.model.signals.acks;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabelInvalidException;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.common.HttpStatusCodeOutOfRangeException;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;

/**
 * Parses an {@link Acknowledgement} from a {@link org.eclipse.ditto.json.JsonObject}.
 *
 * @since 1.1.0
 */
@Immutable
final class AcknowledgementJsonParser implements Function<JsonObject, Acknowledgement> {

    @Override
    public Acknowledgement apply(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");
        final AcknowledgementLabel label = tryToGetAcknowledgementLabel(jsonObject);
        final EntityId entityId = getEntityId(jsonObject);
        final HttpStatus httpStatus = getHttpStatus(jsonObject);
        final DittoHeaders dittoHeaders = getDittoHeaders(jsonObject);
        @Nullable final JsonValue payload = getPayloadOrNull(jsonObject);

        return Acknowledgement.of(label, entityId, httpStatus, dittoHeaders, payload);
    }

    private static AcknowledgementLabel tryToGetAcknowledgementLabel(final JsonObject jsonObject) {
        try {
            return getAcknowledgementLabel(jsonObject);
        } catch (final AcknowledgementLabelInvalidException e) {
            throw JsonParseException.newBuilder()
                    .message(e.getMessage())
                    .description(e.getDescription().orElse(null))
                    .cause(e)
                    .build();
        }
    }

    private static AcknowledgementLabel getAcknowledgementLabel(final JsonObject jsonObject) {
        return AcknowledgementLabel.of(jsonObject.getValueOrThrow(Acknowledgement.JsonFields.LABEL));
    }

    private EntityId getEntityId(final JsonObject jsonObject) {
        final EntityType entityType = EntityType.of(jsonObject.getValueOrThrow(Acknowledgement.JsonFields.ENTITY_TYPE));
        final String entityId = jsonObject.getValueOrThrow(Acknowledgement.JsonFields.ENTITY_ID);
        return tryToGetEntityId(entityType, entityId);
    }

    /**
     * Returns the specific EntityId of the target Acknowledgement.
     *
     * @param entityIdValue the raw value of the entity ID.
     * @return the specific entity ID.
     * @throws org.eclipse.ditto.json.JsonParseException if the given {@code entityIdValue} is invalid.
     */
    public EntityId tryToGetEntityId(final EntityType entityType, final CharSequence entityIdValue) {
        try {
            return EntityId.of(entityType, entityIdValue);
        } catch (final DittoRuntimeException e) {
            throw JsonParseException.newBuilder()
                    .message(e.getMessage())
                    .description(e.getDescription().orElse(null))
                    .cause(e)
                    .build();
        } catch (final RuntimeException e) {
            final String message = MessageFormat.format("Entity ID <{0}> is invalid!", entityIdValue);
            final JsonParseException jsonParseException = new JsonParseException(message);
            jsonParseException.initCause(e);
            throw jsonParseException;
        }
    }

    private static HttpStatus getHttpStatus(final JsonObject jsonObject) {
        final Integer statusCodeValue = jsonObject.getValueOrThrow(Acknowledgement.JsonFields.STATUS_CODE);
        try {
            return HttpStatus.getInstance(statusCodeValue);
        } catch (final HttpStatusCodeOutOfRangeException e) {
            throw JsonParseException.newBuilder()
                    .message(e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    private static DittoHeaders getDittoHeaders(final JsonObject jsonObject) {
        final JsonObject dittoHeadersJsonObject = jsonObject.getValueOrThrow(Acknowledgement.JsonFields.DITTO_HEADERS);
        return DittoHeaders.newBuilder(dittoHeadersJsonObject).build();
    }

    @Nullable
    private static JsonValue getPayloadOrNull(final JsonObject jsonObject) {
        return jsonObject.getValue(Acknowledgement.JsonFields.PAYLOAD)
                .filter(value -> !value.isNull())
                .orElse(null);
    }

}
