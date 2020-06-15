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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Parses an {@link Acknowledgements} from a {@link JsonObject}.
 *
 * @param <I> the type of the EntityId the parsed Acknowledgements returns.
 * @since 1.1.0
 */
@Immutable
public final class AcknowledgementsJsonParser<I extends EntityIdWithType>
        implements Function<JsonObject, Acknowledgements> {

    private final AcknowledgementJsonParser<I> acknowledgementJsonParser;

    private AcknowledgementsJsonParser(final AcknowledgementJsonParser<I> acknowledgementJsonParser) {
        this.acknowledgementJsonParser = checkNotNull(acknowledgementJsonParser, "acknowledgementJsonParser");
    }

    /**
     * Returns an instance of AcknowledgementsJsonParser.
     *
     * @param acknowledgementJsonParser a parser for a single {@link Acknowledgement}.
     * @param <I> the type of the EntityId the parsed Acknowledgements returns.
     * @return the instance.
     * @throws NullPointerException if {@code acknowledgementJsonParser} is {@code null}.
     */
    public static <I extends EntityIdWithType> AcknowledgementsJsonParser<I> getInstance(
            final AcknowledgementJsonParser<I> acknowledgementJsonParser) {

        return new AcknowledgementsJsonParser<>(acknowledgementJsonParser);
    }

    @Override
    public Acknowledgements apply(final JsonObject jsonObject) {
        return tryToParseJsonObject(checkNotNull(jsonObject, "jsonObject"));
    }

    private Acknowledgements tryToParseJsonObject(final JsonObject jsonObject) {
        try {
            return parseJsonObject(jsonObject);
        } catch (final JsonParseException | JsonMissingFieldException e) {
            throw e;
        } catch (final DittoRuntimeException e) {
            throw JsonParseException.newBuilder()
                    .message(e.getMessage())
                    .description(e.getDescription().orElse(null))
                    .cause(e)
                    .build();
        } catch (final RuntimeException e) {
            throw JsonParseException.newBuilder()
                    .message(e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    private Acknowledgements parseJsonObject(final JsonObject jsonObject) {
        final I entityId = getEntityId(jsonObject);
        final EntityType entityType = getEntityType(jsonObject);
        final List<Acknowledgement> acknowledgements = getAcknowledgements(jsonObject, entityId);
        final DittoHeaders dittoHeaders = getDittoHeaders(jsonObject);

        final Acknowledgements result;
        if (acknowledgements.isEmpty()) {
            result = Acknowledgements.empty(entityId, dittoHeaders);
        } else {
            result = Acknowledgements.of(acknowledgements, dittoHeaders);
        }
        validateEntityType(entityType, result.getEntityType());
        validateStatusCode(getStatusCode(jsonObject), result.getStatusCode());
        return result;
    }

    private I getEntityId(final JsonObject jsonObject) {
        final String entityIdValue = jsonObject.getValueOrThrow(Acknowledgements.JsonFields.ENTITY_ID);
        return acknowledgementJsonParser.tryToGetEntityId(entityIdValue);
    }

    private static EntityType getEntityType(final JsonObject jsonObject) {
        return EntityType.of(jsonObject.getValueOrThrow(Acknowledgements.JsonFields.ENTITY_TYPE));
    }

    private List<Acknowledgement> getAcknowledgements(final JsonObject jsonObject, final I expectedEntityId) {
        final JsonObject acknowledgements = jsonObject.getValueOrThrow(Acknowledgements.JsonFields.ACKNOWLEDGEMENTS);
        final Predicate<JsonField> isNotJsonSchemaVersion =
                field -> !Objects.equals(field.getKey(), JsonSchemaVersion.getJsonKey());

        return acknowledgements.stream()
                .filter(isNotJsonSchemaVersion)
                .map(jsonField -> parseAcknowledgement(jsonField, expectedEntityId))
                .collect(Collectors.toList());
    }

    private Acknowledgement parseAcknowledgement(final JsonField acknowledgementJsonField, final I expectedEntityId) {
        final JsonValue acknowledgementJsonValue = acknowledgementJsonField.getValue();
        if (!acknowledgementJsonValue.isObject()) {
            final String msgPattern = "<{0}> is not an Acknowledgement JSON object representation!";
            throw new JsonParseException(MessageFormat.format(msgPattern, acknowledgementJsonValue));
        }
        final Acknowledgement result = acknowledgementJsonParser.apply(acknowledgementJsonValue.asObject());
        validateEntityId(result, expectedEntityId);
        return result;
    }

    private void validateEntityId(final Acknowledgement acknowledgement, final I expected) {
        final EntityIdWithType actual = acknowledgement.getEntityId();
        if (!actual.equals(expected)) {
            final String mPtrn = "The entity ID <{0}> of parsed acknowledgement <{1}> differs from the expected <{2}>!";
            throw new JsonParseException(MessageFormat.format(mPtrn, actual, acknowledgement, expected));
        }
    }

    private static DittoHeaders getDittoHeaders(final JsonObject jsonObject) {
        return DittoHeaders.newBuilder(jsonObject.getValueOrThrow(Acknowledgements.JsonFields.DITTO_HEADERS)).build();
    }

    private static void validateEntityType(final EntityType actual, final EntityType expected) {
        if (!actual.equals(expected)) {
            final String msgPattern = "The read entity type <{0}> differs from the expected <{1}>!";
            throw new JsonParseException(MessageFormat.format(msgPattern, actual, expected));
        }
    }

    private static HttpStatusCode getStatusCode(final JsonObject jsonObject) {
        final Integer statusCodeValue = jsonObject.getValueOrThrow(Acknowledgements.JsonFields.STATUS_CODE);
        return HttpStatusCode.forInt(statusCodeValue)
                .orElseThrow(() -> {
                    final String msgPattern = "Status code <{0}> is not supported!";
                    return new JsonParseException(MessageFormat.format(msgPattern, statusCodeValue));
                });
    }

    private static void validateStatusCode(final HttpStatusCode actual, final HttpStatusCode expected) {
        if (actual != expected) {
            final String msgPattern = "The read status code <{0}> differs from the expected <{1}>!";
            throw new JsonParseException(MessageFormat.format(msgPattern, actual.toInt(), expected.toInt()));
        }
    }

}
