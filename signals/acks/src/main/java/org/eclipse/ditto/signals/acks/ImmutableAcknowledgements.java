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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link Acknowledgements}.
 */
@Immutable
final class ImmutableAcknowledgements implements Acknowledgements {

    private final EntityId entityId;
    private final List<Acknowledgement> acknowledgements;
    private final HttpStatusCode statusCode;
    private final DittoHeaders dittoHeaders;

    private ImmutableAcknowledgements(final EntityId entityId,
            final Collection<Acknowledgement> acknowledgements,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {

        this.entityId = entityId;
        this.acknowledgements = Collections.unmodifiableList(new ArrayList<>(acknowledgements));
        this.statusCode = checkNotNull(statusCode, "statusCode");
        this.dittoHeaders = checkNotNull(dittoHeaders, "dittoHeaders");
    }

    /**
     * Returns a new instance of {@code ImmutableAcknowledgements} with the given acknowledgements.
     *
     * @param acknowledgements the acknowledgements of the result.
     * @param dittoHeaders the headers of the returned Acknowledgements instance.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if the given {@code acknowledgements} are empty or if the entity IDs of the
     * given acknowledgements are not equal.
     */
    static ImmutableAcknowledgements of(final Collection<Acknowledgement> acknowledgements,
            final DittoHeaders dittoHeaders) {

        argumentNotEmpty(acknowledgements, "acknowledgements");

        return new ImmutableAcknowledgements(getEntityId(acknowledgements), acknowledgements,
                calculateCombinedStatusCode(acknowledgements), dittoHeaders);
    }

    private static EntityId getEntityId(final Iterable<Acknowledgement> acknowledgements) {
        final Iterator<Acknowledgement> acknowledgementIterator = acknowledgements.iterator();
        Acknowledgement acknowledgement = acknowledgementIterator.next();
        final EntityId result = acknowledgement.getEntityId();
        while (acknowledgementIterator.hasNext()) {
            acknowledgement = acknowledgementIterator.next();
            final EntityId nextEntityId = acknowledgement.getEntityId();
            if (!result.equals(nextEntityId)) {
                final String msgPattern = "Entity ID <{0}> differs from the expected entity ID <{1}>!";
                throw new IllegalArgumentException(MessageFormat.format(msgPattern, nextEntityId, result));
            }
        }
        return result;
    }

    private static HttpStatusCode calculateCombinedStatusCode(final Collection<Acknowledgement> acknowledgements) {
        final HttpStatusCode result;
        if (1 == acknowledgements.size()) {
            result = acknowledgements.stream()
                    .findFirst()
                    .map(Acknowledgement::getStatusCode)
                    .orElse(HttpStatusCode.INTERNAL_SERVER_ERROR);
        } else {
            final Stream<Acknowledgement> acknowledgementStream = acknowledgements.stream();
            final boolean allAcknowledgementsSuccessful = acknowledgementStream.allMatch(Acknowledgement::isSuccess);
            if (allAcknowledgementsSuccessful) {
                result = HttpStatusCode.OK;
            } else {
                result = HttpStatusCode.FAILED_DEPENDENCY;
            }
        }
        return result;
    }

    /**
     * Returns an empty instance of {@code ImmutableAcknowledgements}.
     *
     * @param entityId the entity ID for which no acknowledgements were received at all.
     * @param dittoHeaders the headers of the returned Acknowledgements instance.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static ImmutableAcknowledgements empty(final EntityId entityId, final DittoHeaders dittoHeaders) {
        final List<Acknowledgement> acknowledgements = Collections.emptyList();

        return new ImmutableAcknowledgements(checkNotNull(entityId, "entityId"), acknowledgements,
                calculateCombinedStatusCode(acknowledgements), dittoHeaders);
    }

    /**
     * Parses the given JSON object to an instance of {@code ImmutableAcknowledgements}.
     *
     * @param jsonObject the JSON object representation of an ImmutableAcknowledgements.
     * @return the parsed instance.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} misses a required field.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contained an unexpected value.
     */
    public static ImmutableAcknowledgements fromJson(final JsonObject jsonObject) {
        final EntityId readEntityId = getEntityId(jsonObject);
        final List<Acknowledgement> readAcknowledgements = getAcknowledgements(jsonObject);
        final HttpStatusCode readStatusCode = getStatusCode(jsonObject);
        final DittoHeaders readDittoHeaders = getDittoHeaders(jsonObject);

        return new ImmutableAcknowledgements(readEntityId, readAcknowledgements, readStatusCode, readDittoHeaders);
    }

    private static EntityId getEntityId(final JsonObject jsonObject) {
        return DefaultEntityId.of(jsonObject.getValueOrThrow(JsonFields.ENTITY_ID));
    }

    private static List<Acknowledgement> getAcknowledgements(final JsonObject jsonObject) {
        final JsonObject readAcknowledgementsJsonObject = jsonObject.getValueOrThrow(JsonFields.ACKNOWLEDGEMENTS);
        return readAcknowledgementsJsonObject.stream()
                .filter(field -> !Objects.equals(field.getKey(), JsonSchemaVersion.getJsonKey()))
                .map(JsonField::getValue)
                .map(JsonValue::asObject)
                .map(AcknowledgementFactory::acknowledgementFromJson)
                .collect(Collectors.toList());
    }

    private static HttpStatusCode getStatusCode(final JsonObject jsonObject) {
        final Integer readStatusCodeValue = jsonObject.getValueOrThrow(JsonFields.STATUS_CODE);
        return HttpStatusCode.forInt(readStatusCodeValue)
                .orElseThrow(() -> {
                    final String msgPattern = "Status code <{0}> is not supported!";
                    return new JsonParseException(MessageFormat.format(msgPattern, readStatusCodeValue));
                });
    }

    private static DittoHeaders getDittoHeaders(final JsonObject jsonObject) {
        final JsonObject readDittoHeadersJsonObject = jsonObject.getValueOrThrow(JsonFields.DITTO_HEADERS);
        return DittoHeaders.newBuilder(readDittoHeadersJsonObject).build();
    }

    @Override
    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    @Override
    public Set<AcknowledgementLabel> getMissingAcknowledgementLabels() {
        return stream()
                .filter(Acknowledgement::isTimeout)
                .map(Acknowledgement::getLabel)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Set<Acknowledgement> getSuccessfulAcknowledgements() {
        return stream()
                .filter(Acknowledgement::isSuccess)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Set<Acknowledgement> getFailedAcknowledgements() {
        return stream()
                .filter(acknowledgement -> !acknowledgement.isSuccess())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Iterator<Acknowledgement> iterator() {
        return acknowledgements.iterator();
    }

    @Override
    public int getSize() {
        return acknowledgements.size();
    }

    @Override
    public boolean isEmpty() {
        return acknowledgements.isEmpty();
    }

    @Override
    public Stream<Acknowledgement> stream() {
        return acknowledgements.stream();
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Override
    public Acknowledgements setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ImmutableAcknowledgements(entityId, acknowledgements, statusCode, dittoHeaders);
    }

    @Override
    public EntityId getEntityId() {
        return entityId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        final int acknowledgementsSize = acknowledgements.size();
        final Optional<JsonValue> result;
        if (0 == acknowledgementsSize) {
            result = Optional.empty();
        } else if (1 == acknowledgementsSize) {
            final Acknowledgement soleAcknowledgement = acknowledgements.get(0);
            result = soleAcknowledgement.getEntity();
        } else {
            result = Optional.of(acknowledgementsToJson(schemaVersion, p -> true));
        }
        return result;
    }

    private JsonObject acknowledgementsToJson(final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        return stream()
                .map(ack -> JsonField.newInstance(ack.getLabel(), ack.toJson(schemaVersion, predicate)))
                .collect(JsonCollectors.fieldsToObject());
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        return JsonObject.newBuilder()
                .set(JsonFields.ENTITY_ID, entityId.toString(), predicate)
                .set(JsonFields.STATUS_CODE, statusCode.toInt(), predicate)
                .set(JsonFields.ACKNOWLEDGEMENTS, acknowledgementsToJson(schemaVersion, thePredicate), predicate)
                .set(JsonFields.DITTO_HEADERS, dittoHeaders.toJson(), predicate)
                .build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableAcknowledgements that = (ImmutableAcknowledgements) o;
        return entityId.equals(that.entityId) &&
                acknowledgements.equals(that.acknowledgements) &&
                statusCode.equals(that.statusCode) &&
                dittoHeaders.equals(that.dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, acknowledgements, statusCode, dittoHeaders);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "entityId=" + entityId +
                ", acknowledgements=" + acknowledgements +
                ", statusCode=" + statusCode +
                ", dittoHeaders=" + dittoHeaders +
                "]";
    }

}
