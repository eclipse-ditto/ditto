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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link Acknowledgements}.
 */
@Immutable
final class ImmutableAcknowledgements implements Acknowledgements {

    private final EntityIdWithType entityId;
    private final List<Acknowledgement> acknowledgements;
    private final HttpStatusCode statusCode;
    private final DittoHeaders dittoHeaders;

    private ImmutableAcknowledgements(final EntityIdWithType entityId,
            final Collection<? extends Acknowledgement> acknowledgements,
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
     * @throws IllegalArgumentException if the given {@code acknowledgements} are empty or if the entity IDs or entity
     * types of the given acknowledgements are not equal.
     */
    static ImmutableAcknowledgements of(final Collection<? extends Acknowledgement> acknowledgements,
            final DittoHeaders dittoHeaders) {

        argumentNotEmpty(acknowledgements, "acknowledgements");

        return new ImmutableAcknowledgements(getEntityId(acknowledgements), acknowledgements,
                getCombinedStatusCode(acknowledgements), dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ImmutableAcknowledgements} with the given parameters.
     *
     * @param entityId the ID of the affected entity being acknowledged.
     * @param acknowledgements the map of acknowledgements to be included in the result.
     * @param statusCode the status code (HTTP semantics) of the combined Acknowledgements.
     * @param dittoHeaders the headers of the returned Acknowledgements instance.
     * @return the Acknowledgements.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if the given {@code acknowledgements} are empty or if the entity IDs or entity
     * types of the given acknowledgements are not equal.
     */
    static ImmutableAcknowledgements of(final EntityIdWithType entityId,
            final Collection<? extends Acknowledgement> acknowledgements,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {

        checkNotNull(entityId, "entityId");
        argumentNotEmpty(acknowledgements, "acknowledgements");
        checkNotNull(statusCode, "statusCode");
        checkNotNull(dittoHeaders, "dittoHeaders");

        return new ImmutableAcknowledgements(entityId, acknowledgements, statusCode, dittoHeaders);
    }

    private static EntityIdWithType getEntityId(final Iterable<? extends Acknowledgement> acknowledgements) {
        final Iterator<? extends Acknowledgement> acknowledgementIterator = acknowledgements.iterator();
        Acknowledgement acknowledgement = acknowledgementIterator.next();
        final EntityIdWithType entityId = acknowledgement.getEntityId();
        while (acknowledgementIterator.hasNext()) {
            acknowledgement = acknowledgementIterator.next();
            // will throw an IllegalArgumentException if they are not equal
            entityId.isCompatibleOrThrow(acknowledgement.getEntityId());
        }
        return entityId;
    }

    private static HttpStatusCode getCombinedStatusCode(final Collection<? extends Acknowledgement> acknowledgements) {
        final HttpStatusCode result;
        if (1 == acknowledgements.size()) {
            result = acknowledgements.stream()
                    .findFirst()
                    .map(Acknowledgement::getStatusCode)
                    .orElse(HttpStatusCode.INTERNAL_SERVER_ERROR);
        } else {
            final Stream<? extends Acknowledgement> acknowledgementStream = acknowledgements.stream();
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
    static ImmutableAcknowledgements empty(final EntityIdWithType entityId, final DittoHeaders dittoHeaders) {
        final List<Acknowledgement> acknowledgements = Collections.emptyList();

        return new ImmutableAcknowledgements(checkNotNull(entityId, "entityId"),
                acknowledgements,
                getCombinedStatusCode(acknowledgements),
                dittoHeaders);
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
    public Optional<Acknowledgement> getAcknowledgement(final AcknowledgementLabel acknowledgementLabel) {
        return stream()
                .filter(ack -> acknowledgementLabel.equals(ack.getLabel()))
                .findAny();
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
        if (acknowledgements.size() == 1) {
            return acknowledgements.get(0).getDittoHeaders().toBuilder().putHeaders(dittoHeaders).build();
        }
        return dittoHeaders;
    }

    @Override
    public ImmutableAcknowledgements setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ImmutableAcknowledgements(entityId, acknowledgements, statusCode, dittoHeaders);
    }

    @Override
    public EntityIdWithType getEntityId() {
        return entityId;
    }

    @Override
    public EntityType getEntityType() {
        return entityId.getEntityType();
    }

    @Override
    public String getType() {
        return Acknowledgements.getType(getEntityType());
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        final int acknowledgementsSize = acknowledgements.size();
        final Optional<JsonValue> result;
        if (0 == acknowledgementsSize) {
            result = Optional.empty();
        } else if (1 == acknowledgementsSize) {
            final Acknowledgement soleAcknowledgement = acknowledgements.get(0);
            result = soleAcknowledgement.getEntity(schemaVersion);
        } else {
            result = Optional.of(acknowledgementsEntitiesToJson(schemaVersion));
        }
        return result;
    }

    private JsonObject acknowledgementsEntitiesToJson(final JsonSchemaVersion schemaVersion) {

        return acknowledgementsToJsonWithDisambiguation(schemaVersion, FieldType.all(), (ack, version, predicate) -> {
            final JsonObjectBuilder jsonObjectBuilder = JsonObject.newBuilder()
                    .set(Acknowledgement.JsonFields.STATUS_CODE, ack.getStatusCode().toInt());

            final Optional<JsonValue> ackEntity = ack.getEntity(version);
            ackEntity.ifPresent(ae -> jsonObjectBuilder.set(Acknowledgement.JsonFields.PAYLOAD, ae));

            final DittoHeaders ackHeaders = ack.getDittoHeaders();
            jsonObjectBuilder.set(Acknowledgement.JsonFields.DITTO_HEADERS, buildHeadersJson(ackHeaders));
            return jsonObjectBuilder.build();
        });
    }

    private static JsonObject buildHeadersJson(final DittoHeaders dittoHeaders) {

        final boolean containsDittoContentType = dittoHeaders.getContentType()
                .filter(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE::equals)
                .isPresent();
        if (containsDittoContentType) {
            return dittoHeaders.toBuilder()
                    .removeHeader(DittoHeaderDefinition.CONTENT_TYPE.getKey())
                    .build()
                    .toJson();
        } else {
            return dittoHeaders.toJson();
        }
    }

    /**
     * Create a JSON object of acknowledgements with labels as key such that acks of the same label are grouped into
     * an array.
     *
     * @param schemaVersion the schema version.
     * @param predicate the JSON field predicate.
     * @param acknowledgementToJson the function to turn each ack into a JSON object.
     * @return the disambiguated JSON object.
     */
    private JsonObject acknowledgementsToJsonWithDisambiguation(final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate,
            final AcknowledgementToJson acknowledgementToJson) {

        // use a linked hash map to preserve the order of the first appearance of ack labels
        final Map<CharSequence, JsonArrayBuilder> disambiguationMap = new LinkedHashMap<>();
        for (final Acknowledgement ack : acknowledgements) {
            disambiguationMap.compute(ack.getLabel(), (label, previousBuilder) -> {
                final JsonArrayBuilder builder = previousBuilder == null ? JsonArray.newBuilder() : previousBuilder;
                return builder.add(acknowledgementToJson.toJson(ack, schemaVersion, predicate));
            });
        }

        return disambiguationMap.entrySet()
                .stream()
                .map(entry -> {
                    final JsonArray array = entry.getValue().build();
                    final JsonValue value = array.getSize() == 1 ? array.get(0).orElse(array) : array;
                    return JsonField.newInstance(entry.getKey(), value);
                })
                .collect(JsonCollectors.fieldsToObject());
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObject acksJsonObject =
                acknowledgementsToJsonWithDisambiguation(schemaVersion, thePredicate, Acknowledgement::toJson);
        return JsonObject.newBuilder()
                .set(JsonFields.ENTITY_ID, entityId.toString(), predicate)
                .set(JsonFields.ENTITY_TYPE, getEntityType().toString(), predicate)
                .set(JsonFields.STATUS_CODE, statusCode.toInt(), predicate)
                .set(JsonFields.ACKNOWLEDGEMENTS, acksJsonObject, predicate)
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

    @FunctionalInterface
    private interface AcknowledgementToJson {

        JsonObject toJson(Acknowledgement ack, JsonSchemaVersion schemaVersion, Predicate<JsonField> predicate);
    }

}
