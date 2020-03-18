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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabelInvalidException;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Parses an {@link Acknowledgement} from a {@link JsonObject}.
 *
 * @param <I> the type of the EntityId the parsed Acknowledgement returns.
 * @since 1.1.0
 */
@Immutable
public abstract class AcknowledgementJsonParser<I extends EntityIdWithType>
        implements Function<JsonObject, Acknowledgement> {

    /**
     * Constructs a new AcknowledgementJsonParser object.
     *
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected AcknowledgementJsonParser() {
        super();
    }

    @Override
    public Acknowledgement apply(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");
        final AcknowledgementLabel label = tryToGetAcknowledgementLabel(jsonObject);
        final I entityId = getEntityId(jsonObject);
        validateEntityType(getEntityType(jsonObject), entityId.getEntityType());
        final HttpStatusCode statusCode = getStatusCode(jsonObject);
        final DittoHeaders dittoHeaders = getDittoHeaders(jsonObject);
        @Nullable final JsonValue payload = getPayloadOrNull(jsonObject);

        return AcknowledgementFactory.newAcknowledgement(label, entityId, statusCode, dittoHeaders, payload);
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

    private I getEntityId(final JsonObject jsonObject) {
        return tryToGetEntityId(jsonObject.getValueOrThrow(Acknowledgement.JsonFields.ENTITY_ID));
    }

    /**
     * Returns the specific EntityId of the target Acknowledgement.
     *
     * @param entityIdValue the raw value of the entity ID.
     * @return the specific entity ID.
     * @throws JsonParseException if the given {@code entityIdValue} is invalid.
     */
    public I tryToGetEntityId(final CharSequence entityIdValue) {
        try {
            return createEntityIdInstance(entityIdValue);
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

    /**
     * Return the specific EntityId of the target Acknowledgement.
     *
     * @param entityIdValue the raw value of the entity ID.
     * @return the specific entity ID.
     */
    protected abstract I createEntityIdInstance(CharSequence entityIdValue);

    private static EntityType getEntityType(final JsonObject jsonObject) {
        return EntityType.of(jsonObject.getValueOrThrow(Acknowledgement.JsonFields.ENTITY_TYPE));
    }

    private static void validateEntityType(final EntityType actual, final EntityType expected) {
        if (!actual.equals(expected)) {
            final String msgPattern = "The read entity type <{0}> differs from the expected <{1}>!";
            throw new JsonParseException(MessageFormat.format(msgPattern, actual, expected));
        }
    }

    private static HttpStatusCode getStatusCode(final JsonObject jsonObject) {
        final Integer statusCodeValue = jsonObject.getValueOrThrow(Acknowledgement.JsonFields.STATUS_CODE);
        return HttpStatusCode.forInt(statusCodeValue)
                .orElseThrow(() -> {
                    final String msgPattern = "Status code <{0}> is not supported!";
                    return new JsonParseException(MessageFormat.format(msgPattern, statusCodeValue));
                });
    }

    private static DittoHeaders getDittoHeaders(final JsonObject jsonObject) {
        final JsonObject dittoHeadersJsonObject = jsonObject.getValueOrThrow(Acknowledgement.JsonFields.DITTO_HEADERS);
        return DittoHeaders.newBuilder(dittoHeadersJsonObject).build();
    }

    @Nullable
    private static JsonValue getPayloadOrNull(final JsonObject jsonObject) {
        return jsonObject.getValue(Acknowledgement.JsonFields.PAYLOAD).orElse(null);
    }

}
