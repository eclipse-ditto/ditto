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
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.common.HttpStatusCodeOutOfRangeException;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;

/**
 * Parses an {@link Acknowledgements} from a {@link org.eclipse.ditto.json.JsonObject}.
 *
 * @since 1.1.0
 */
@Immutable
final class AcknowledgementsJsonParser implements BiFunction<JsonObject, DittoHeaders, Acknowledgements> {

    private final AcknowledgementJsonParser acknowledgementJsonParser;

    private AcknowledgementsJsonParser(final AcknowledgementJsonParser acknowledgementJsonParser) {
        this.acknowledgementJsonParser = checkNotNull(acknowledgementJsonParser, "acknowledgementJsonParser");
    }

    /**
     * Returns an instance of AcknowledgementsJsonParser.
     *
     * @param acknowledgementJsonParser a parser for a single {@link Acknowledgement}.
     * @return the instance.
     * @throws NullPointerException if {@code acknowledgementJsonParser} is {@code null}.
     */
    public static AcknowledgementsJsonParser getInstance(final AcknowledgementJsonParser acknowledgementJsonParser) {

        return new AcknowledgementsJsonParser(acknowledgementJsonParser);
    }

    @Override
    public Acknowledgements apply(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return tryToParseJsonObject(checkNotNull(jsonObject, "jsonObject"), dittoHeaders);
    }

    private Acknowledgements tryToParseJsonObject(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        try {
            return parseJsonObject(jsonObject, dittoHeaders);
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

    private Acknowledgements parseJsonObject(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final EntityId entityId = getEntityId(jsonObject);
        final List<Acknowledgement> acknowledgements = getAcknowledgements(jsonObject, entityId);

        final Acknowledgements result;
        if (acknowledgements.isEmpty()) {
            result = Acknowledgements.empty(entityId, dittoHeaders);
        } else {
            result = Acknowledgements.of(acknowledgements, dittoHeaders);
        }
        validateHttpStatus(getHttpStatus(jsonObject), result.getHttpStatus());
        return result;
    }

    private EntityId getEntityId(final JsonObject jsonObject) {
        final EntityType entityType =
                EntityType.of(jsonObject.getValueOrThrow(Acknowledgements.JsonFields.ENTITY_TYPE));
        final String entityIdValue = jsonObject.getValueOrThrow(Acknowledgements.JsonFields.ENTITY_ID);
        return acknowledgementJsonParser.tryToGetEntityId(entityType, entityIdValue);
    }

    private List<Acknowledgement> getAcknowledgements(final JsonObject jsonObject, final EntityId expectedEntityId) {
        final JsonObject acknowledgements = jsonObject.getValueOrThrow(Acknowledgements.JsonFields.ACKNOWLEDGEMENTS);
        final Predicate<JsonField> isNotJsonSchemaVersion =
                field -> !Objects.equals(field.getKey(), JsonSchemaVersion.getJsonKey());

        return acknowledgements.stream()
                .filter(isNotJsonSchemaVersion)
                .flatMap(jsonField -> parseAcknowledgement(jsonField, expectedEntityId))
                .collect(Collectors.toList());
    }

    private Stream<Acknowledgement> parseAcknowledgement(final JsonField acknowledgementJsonField,
            final EntityId expectedEntityId) {
        final JsonValue acknowledgementJsonValue = acknowledgementJsonField.getValue();

        if (acknowledgementJsonValue.isArray()) {
            return acknowledgementJsonValue.asArray()
                    .stream()
                    .map(json -> parseAcknowledgementFromObject(json, expectedEntityId));
        } else {
            return Stream.of(parseAcknowledgementFromObject(acknowledgementJsonValue, expectedEntityId));
        }
    }

    private Acknowledgement parseAcknowledgementFromObject(final JsonValue jsonObject,
            final EntityId expectedEntityId) {
        if (!jsonObject.isObject()) {
            final String msgPattern = "<{0}> is not an Acknowledgement JSON object representation!";
            throw new JsonParseException(MessageFormat.format(msgPattern, jsonObject));
        }
        final Acknowledgement result = acknowledgementJsonParser.apply(jsonObject.asObject());
        validateEntityId(result, expectedEntityId);
        return result;
    }

    private void validateEntityId(final Acknowledgement acknowledgement, final EntityId expected) {
        final EntityId actual = acknowledgement.getEntityId();
        if (!actual.equals(expected)) {
            final String mPtrn = "The entity ID <{0}> of parsed acknowledgement <{1}> differs from the expected <{2}>!";
            throw new JsonParseException(MessageFormat.format(mPtrn, actual, acknowledgement, expected));
        }
    }

    private static HttpStatus getHttpStatus(final JsonObject jsonObject) {
        final Integer statusCode = jsonObject.getValueOrThrow(Acknowledgements.JsonFields.STATUS_CODE);
        try {
            return HttpStatus.getInstance(statusCode);
        } catch (final HttpStatusCodeOutOfRangeException e) {
            throw JsonParseException.newBuilder()
                    .message(e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    private static void validateHttpStatus(final HttpStatus actual, final HttpStatus expected) {
        if (!actual.equals(expected)) {
            final String msgPattern = "The read status code <{0}> differs from the expected <{1}>!";
            throw new JsonParseException(MessageFormat.format(msgPattern, actual.getCode(), expected.getCode()));
        }
    }

}
