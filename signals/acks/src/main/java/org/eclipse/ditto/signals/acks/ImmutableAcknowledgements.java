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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
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
    private final HttpStatusCode statusCode;
    private final Map<AcknowledgementLabel, Acknowledgement> acknowledgements;
    private final DittoHeaders dittoHeaders;

    private ImmutableAcknowledgements(final EntityId entityId,
            final HttpStatusCode statusCode,
            final Map<AcknowledgementLabel, Acknowledgement> acknowledgements,
            final DittoHeaders dittoHeaders) {

        this.entityId = checkNotNull(entityId, "entityId");
        this.statusCode = checkNotNull(statusCode, "statusCode");
        checkNotNull(acknowledgements, "acknowledgements");
        this.acknowledgements = Collections.unmodifiableMap(new LinkedHashMap<>(acknowledgements));
        this.dittoHeaders = checkNotNull(dittoHeaders, "dittoHeaders");
    }

    /**
     * Returns a new {@code ImmutableAcknowledgements} for the specified parameters.
     *
     * @param entityId the ID of the affected entity being acknowledged.
     * @param statusCode the aggregated status code (HTTP semantics) of the Acknowledgements.
     * @param dittoHeaders the DittoHeaders.
     * @return the new AcknowledgementAggregation.
     * @throws NullPointerException if one of the required parameters was {@code null}.
     * @throws IllegalArgumentException if {@code entityId} is empty.
     */
    static ImmutableAcknowledgements of(final CharSequence entityId,
            final HttpStatusCode statusCode,
            final Map<AcknowledgementLabel, Acknowledgement> acknowledgements,
            final DittoHeaders dittoHeaders) {

        return new ImmutableAcknowledgements(DefaultEntityId.of(entityId), statusCode, acknowledgements, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code } with the given acknowledgements.
     *
     * @param acknowledgements the {@link Acknowledgement}s of the new AcknowledgementAggregation.
     * @return the new {@code AcknowledgementAggregation}.
     * @throws NullPointerException if {@code acknowledgements} is {@code null}.
     * @throws IllegalArgumentException if the passed {@code acknowledgements} iterable contains an
     * {@code Acknowledgement} with the same {@code AcknowledgementLabel} more than once.
     */
    static ImmutableAcknowledgements of(final CharSequence entityId,
            final HttpStatusCode statusCode,
            final Iterable<Acknowledgement> acknowledgements,
            final DittoHeaders dittoHeaders) {

        checkNotNull(acknowledgements, "acknowledgements");
        return of(entityId, statusCode, buildAcknowledgementMap(acknowledgements), dittoHeaders);
    }

    private static Map<AcknowledgementLabel, Acknowledgement> buildAcknowledgementMap(
            final Iterable<Acknowledgement> acknowledgements) {

        final Map<AcknowledgementLabel, Acknowledgement> acknowledgementMap = new LinkedHashMap<>();
        acknowledgements.forEach(acknowledgement -> {
            final Acknowledgement existingAck = acknowledgementMap.put(acknowledgement.getLabel(), acknowledgement);
            if (null != existingAck) {
                final String msgTemplate = "There is more than one Acknowledgement with the label <{0}>!";
                throw new IllegalArgumentException(MessageFormat.format(msgTemplate, acknowledgement.getLabel()));
            }
        });
        return acknowledgementMap;
    }

    /**
     * Returns a new {@code AcknowledgementAggregation} parsed from the specified {@code jsonObject}.
     *
     * @param jsonObject the JSON object.
     * @return the Acknowledgement.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the expected
     * 'Acknowledgement' format.
     */
    public static ImmutableAcknowledgements fromJson(final JsonObject jsonObject) {

        final String extractedEntityId = jsonObject.getValueOrThrow(JsonFields.ENTITY_ID);
        final EntityId entityId = DefaultEntityId.of(extractedEntityId);
        final HttpStatusCode statusCode = HttpStatusCode.forInt(jsonObject.getValueOrThrow(JsonFields.STATUS_CODE))
                .orElseThrow(() -> new IllegalArgumentException("Status code not supported!"));
        final JsonObject extractedAcknowledgements = jsonObject.getValueOrThrow(JsonFields.ACKNOWLEDGEMENTS);
        final List<Acknowledgement> acknowledgements = extractedAcknowledgements.stream()
                .filter(field -> !Objects.equals(field.getKey(), JsonSchemaVersion.getJsonKey()))
                .map(field -> ImmutableAcknowledgement.fromJson(field.getValue().asObject()))
                .collect(Collectors.toList());
        final JsonObject jsonDittoHeaders = jsonObject.getValueOrThrow(JsonFields.DITTO_HEADERS);
        final DittoHeaders extractedDittoHeaders = DittoHeaders.newBuilder(jsonDittoHeaders).build();

        return of(entityId, statusCode, acknowledgements, extractedDittoHeaders);
    }

    @Override
    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    @Override
    public Set<AcknowledgementLabel> getMissingAcknowledgementLabels() {
        return getCopyAsLinkedHashSet(acknowledgements.values().stream()
                .filter(Acknowledgement::isTimeout)
                .map(Acknowledgement::getLabel)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Set<Acknowledgement> getSuccessfulAcknowledgements() {
        return getCopyAsLinkedHashSet(acknowledgements.values().stream()
                .filter(Acknowledgement::isSuccess)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Set<Acknowledgement> getFailedAcknowledgements() {
        return getCopyAsLinkedHashSet(acknowledgements.values().stream()
                .filter(Acknowledgement::isFailed)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Iterator<Acknowledgement> iterator() {
        return new HashSet<>(acknowledgements.values()).iterator();
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
        return acknowledgements.values().stream();
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Override
    public Acknowledgements setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ImmutableAcknowledgements(entityId, statusCode, acknowledgements, dittoHeaders);
    }

    @Override
    public EntityId getEntityId() {
        return entityId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        if (acknowledgements.isEmpty()) {
            return Optional.empty();
        } else if (acknowledgements.size() == 1) {
            return acknowledgements.values().stream().findFirst().flatMap(Acknowledgement::getEntity);
        } else {
            return Optional.of(acknowledgementsToJson(schemaVersion, p -> true));
        }
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JsonFields.ENTITY_ID, entityId.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.STATUS_CODE, statusCode.toInt(), predicate);
        jsonObjectBuilder.set(JsonFields.ACKNOWLEDGEMENTS, acknowledgementsToJson(schemaVersion, thePredicate),
                predicate);
        jsonObjectBuilder.set(JsonFields.DITTO_HEADERS, dittoHeaders.toJson(), predicate);

        return jsonObjectBuilder.build();
    }

    private JsonObject acknowledgementsToJson(final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        acknowledgements.values().forEach(acknowledgement -> {
            final JsonKey key = JsonKey.of(acknowledgement.getLabel());
            final JsonValue value = acknowledgement.toJson(schemaVersion, thePredicate);
            jsonObjectBuilder.set(key, value);
        });

        return jsonObjectBuilder.build();
    }

    /**
     * Creates a copy of the given collection.
     * The returned set is not supposed to be extended by the caller.
     * Thus and because the final size is already known the load factor of the returned Set is set to 1.0 to reduce the
     * memory footprint of the Set.
     */
    private static <T> Set<T> getCopyAsLinkedHashSet(final Collection<T> c) {
        final Set<T> result = new LinkedHashSet<>(c.size(), 1.0F);
        result.addAll(c);
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableAcknowledgements that = (ImmutableAcknowledgements) o;
        return entityId.equals(that.entityId) &&
                statusCode == that.statusCode &&
                acknowledgements.equals(that.acknowledgements) &&
                dittoHeaders.equals(that.dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, statusCode, acknowledgements, dittoHeaders);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "entityId=" + entityId +
                ", statusCode=" + statusCode +
                ", acknowledgements=" + acknowledgements +
                ", dittoHeaders=" + dittoHeaders +
                "]";
    }

}
