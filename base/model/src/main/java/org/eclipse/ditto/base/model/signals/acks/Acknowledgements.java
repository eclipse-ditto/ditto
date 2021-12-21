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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
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

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.entity.type.WithEntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Acknowledgements aggregate several {@link Acknowledgement}s and contain an aggregated overall
 * {@link #getHttpStatus() HTTP status} describing the aggregated status of all contained Acknowledgements as well as
 * a {@link #getEntity(org.eclipse.ditto.base.model.json.JsonSchemaVersion)} returning the contained Json entity.
 *
 * @since 1.1.0
 */
@JsonParsableCommandResponse(type = Acknowledgements.TYPE)
@Immutable
public final class Acknowledgements
        implements Iterable<Acknowledgement>, CommandResponse<Acknowledgements>, WithOptionalEntity, WithEntityType,
        SignalWithEntityId<Acknowledgements> {

    static final String TYPE = "acknowledgements";
    private final EntityId entityId;
    private final List<Acknowledgement> acknowledgements;
    private final HttpStatus httpStatus;
    private final DittoHeaders dittoHeaders;

    private Acknowledgements(final EntityId entityId,
            final Collection<? extends Acknowledgement> acknowledgements,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        this.entityId = entityId;
        this.acknowledgements = Collections.unmodifiableList(new ArrayList<>(acknowledgements));
        this.httpStatus = checkNotNull(httpStatus, "httpStatus");
        final DittoHeadersBuilder<?, ?> dittoHeadersBuilder =
                checkNotNull(dittoHeaders, "dittoHeaders").isResponseRequired() ? dittoHeaders
                        .toBuilder()
                        .responseRequired(false) : dittoHeaders.toBuilder();
        this.dittoHeaders = dittoHeadersBuilder
                .removeHeader(DittoHeaderDefinition.REQUESTED_ACKS.getKey())
                .build();
    }

    /**
     * Returns a new instance of {@code Acknowledgements} with the given acknowledgements.
     *
     * @param acknowledgements the acknowledgements of the result.
     * @param dittoHeaders the headers of the returned Acknowledgements instance.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if the given {@code acknowledgements} are empty or if the entity IDs or entity
     * types of the given acknowledgements are not equal.
     */
    public static Acknowledgements of(final Collection<? extends Acknowledgement> acknowledgements,
            final DittoHeaders dittoHeaders) {

        argumentNotEmpty(acknowledgements, "acknowledgements");

        return of(getEntityId(acknowledgements), acknowledgements, getCombinedHttpStatus(acknowledgements),
                dittoHeaders);
    }

    /**
     * Returns a new instance of {@code Acknowledgements} with the given parameters.
     *
     * @param entityId the ID of the affected entity being acknowledged.
     * @param acknowledgements the map of acknowledgements to be included in the result.
     * @param httpStatus the HTTP status of the combined Acknowledgements.
     * @param dittoHeaders the headers of the returned Acknowledgements instance.
     * @return the Acknowledgements.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if the given {@code acknowledgements} are empty or if the entity IDs or entity
     * types of the given acknowledgements are not equal.
     * @since 2.0.0
     */
    public static Acknowledgements of(final EntityId entityId,
            final Collection<? extends Acknowledgement> acknowledgements,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        checkNotNull(entityId, "entityId");
        argumentNotEmpty(acknowledgements, "acknowledgements");
        checkNotNull(httpStatus, "httpStatus");
        checkNotNull(dittoHeaders, "dittoHeaders");

        return new Acknowledgements(entityId, acknowledgements, httpStatus, dittoHeaders);
    }

    private static EntityId getEntityId(final Iterable<? extends Acknowledgement> acknowledgements) {
        final Iterator<? extends Acknowledgement> acknowledgementIterator = acknowledgements.iterator();
        Acknowledgement acknowledgement = acknowledgementIterator.next();
        final EntityId entityId = acknowledgement.getEntityId();
        while (acknowledgementIterator.hasNext()) {
            acknowledgement = acknowledgementIterator.next();
            final EntityId acknowledgementEntityId = acknowledgement.getEntityId();
            if (!entityId.equals(acknowledgement.getEntityId())) {
                final String pattern = "The entity ID <{0}> is not compatible with <{1}>!";
                throw new IllegalArgumentException(MessageFormat.format(pattern, acknowledgementEntityId, entityId));
            }
        }
        return entityId;
    }

    private static HttpStatus getCombinedHttpStatus(final Collection<? extends Acknowledgement> acknowledgements) {
        final HttpStatus result;
        if (1 == acknowledgements.size()) {
            result = acknowledgements.stream()
                    .findFirst()
                    .map(Acknowledgement::getHttpStatus)
                    .orElse(HttpStatus.INTERNAL_SERVER_ERROR);
        } else {
            final Stream<? extends Acknowledgement> acknowledgementStream = acknowledgements.stream();
            final boolean allAcknowledgementsSuccessful = acknowledgementStream.allMatch(Acknowledgement::isSuccess);
            if (allAcknowledgementsSuccessful) {
                result = HttpStatus.OK;
            } else {
                result = HttpStatus.FAILED_DEPENDENCY;
            }
        }
        return result;
    }

    /**
     * Returns an empty instance of {@code Acknowledgements}.
     *
     * @param entityId the entity ID for which no acknowledgements were received at all.
     * @param dittoHeaders the headers of the returned Acknowledgements instance.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Acknowledgements empty(final EntityId entityId, final DittoHeaders dittoHeaders) {
        final List<Acknowledgement> acknowledgements = Collections.emptyList();

        return new Acknowledgements(checkNotNull(entityId, "entityId"),
                acknowledgements,
                getCombinedHttpStatus(acknowledgements),
                dittoHeaders);
    }

    /**
     * Returns the HTTP status of the Acknowledgements:
     * <ul>
     * <li>If only one acknowledgement is included, its status is returned.</li>
     * <li>
     * If several acknowledgements are included:
     * <ul>
     * <li>
     * If all contained acknowledgements are successful, the overall HTTP status is
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#OK}.
     * </li>
     * <li>
     * If at least one acknowledgement failed, the overall HTTP status is
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#FAILED_DEPENDENCY}.
     * </li>
     * </ul>
     * </li>
     * </ul>
     *
     * @return the HTTP status.
     * @since 2.0.0
     */
    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * Returns a set containing the the AcknowledgementLabels.
     *
     * @return the unanswered acknowledgement labels.
     * Changes on the returned set are not reflected back to this AcknowledgementsPerRequest instance.
     */
    public Set<AcknowledgementLabel> getMissingAcknowledgementLabels() {
        return stream()
                .filter(Acknowledgement::isTimeout)
                .map(Acknowledgement::getLabel)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Returns a set containing the the successful acknowledgements.
     *
     * @return the successful acknowledgements.
     * The returned set maintains the order in which the acknowledgement were received.
     * Changes on the returned set are not reflected back to this AcknowledgementsPerRequest instance.
     */
    public Set<Acknowledgement> getSuccessfulAcknowledgements() {
        return stream()
                .filter(Acknowledgement::isSuccess)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Returns a set containing the the failed acknowledgements.
     *
     * @return the failed acknowledgements.
     * The returned set maintains the order in which the acknowledgement were received.
     * Changes on the returned set are not reflected back to this AcknowledgementsPerRequest instance.
     */
    public Set<Acknowledgement> getFailedAcknowledgements() {
        return stream()
                .filter(acknowledgement -> !acknowledgement.isSuccess())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Returns the in this Acknowledgements contained acknowledgement identified by the passed
     * {@code acknowledgementLabel}, if it was present. {@link java.util.Optional#empty()} otherwise.
     *
     * @param acknowledgementLabel the acknowledgement label to return.
     * @return the found acknowledgement if the {@code acknowledgementLabel} was part of this Acknowledgements, empty
     * Optional otherwise.
     */
    public Optional<Acknowledgement> getAcknowledgement(final AcknowledgementLabel acknowledgementLabel) {
        return stream()
                .filter(ack -> acknowledgementLabel.equals(ack.getLabel()))
                .findAny();
    }

    @Override
    public Iterator<Acknowledgement> iterator() {
        return acknowledgements.iterator();
    }

    /**
     * Returns the size of the Acknowledgements, i. e. the number of contained values.
     *
     * @return the size.
     */
    public int getSize() {
        return acknowledgements.size();
    }

    /**
     * Indicates whether this Acknowledgements is empty.
     *
     * @return {@code true} if this Acknowledgements does not contain any values, {@code false} else.
     */
    public boolean isEmpty() {
        return acknowledgements.isEmpty();
    }

    /**
     * Returns a sequential {@code Stream} with the values of this Acknowledgements as its source.
     *
     * @return a sequential stream of the Acknowledgements of this container.
     */
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
    public Acknowledgements setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new Acknowledgements(entityId, acknowledgements, httpStatus, dittoHeaders);
    }

    @Override
    public EntityId getEntityId() {
        return entityId;
    }

    @Override
    public EntityType getEntityType() {
        return entityId.getEntityType();
    }

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * Returns the JSON representation of this Acknowledgements' entity.
     * <ul>
     *     <li>
     *         If only one acknowledgement is included, the {@link Acknowledgement#getEntity(org.eclipse.ditto.base.model.json.JsonSchemaVersion)} of this
     *         Ack is returned.
     *     </li>
     *     <li>
     *         If several acknowledgements are included, the {@link Acknowledgement#getEntity(org.eclipse.ditto.base.model.json.JsonSchemaVersion)} of
     *         each Ack is returned in a JsonObject with the AcknowledgementLabel as key of each Ack entry.
     *     </li>
     * </ul>
     *
     * @param schemaVersion the JsonSchemaVersion in which to return the JSON.
     * @return the entity's JSON representation.
     */
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
                    .set(Acknowledgement.JsonFields.STATUS_CODE, ack.getHttpStatus().getCode());

            final Optional<JsonValue> ackEntity = ack.getEntity(version);
            ackEntity.ifPresent(ae -> jsonObjectBuilder.set(Acknowledgement.JsonFields.PAYLOAD, ae));

            final DittoHeaders ackHeaders = ack.getDittoHeaders();
            jsonObjectBuilder.set(Acknowledgement.JsonFields.DITTO_HEADERS, buildHeadersJson(ackHeaders));
            return jsonObjectBuilder.build();
        });
    }

    private static JsonObject buildHeadersJson(final DittoHeaders dittoHeaders) {
        final boolean containsDittoContentType = dittoHeaders.getDittoContentType()
                .filter(ContentType::isDittoProtocol)
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
            final Acknowledgements.AcknowledgementToJson acknowledgementToJson) {

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

    /**
     * Returns a new {@code Acknowledgements} parsed from the given JSON object.
     *
     * @param jsonObject the JSON object to be parsed.
     * @param dittoHeaders the ditto headers of the acknowledgements
     * @return the Acknowledgements.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} misses a required field.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contained an unexpected value type.
     */
    public static Acknowledgements fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return AcknowledgementsJsonParser.getInstance(new AcknowledgementJsonParser()).apply(jsonObject, dittoHeaders);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObject acksJsonObject =
                acknowledgementsToJsonWithDisambiguation(schemaVersion, thePredicate, Acknowledgement::toJson);
        return JsonObject.newBuilder()
                .set(CommandResponse.JsonFields.TYPE, getType(), predicate)
                .set(JsonFields.ENTITY_ID, entityId.toString(), predicate)
                .set(JsonFields.ENTITY_TYPE, getEntityType().toString(), predicate)
                .set(JsonFields.STATUS_CODE, httpStatus.getCode(), predicate)
                .set(JsonFields.ACKNOWLEDGEMENTS, acksJsonObject, predicate)
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
        final Acknowledgements that = (Acknowledgements) o;
        return entityId.equals(that.entityId) &&
                acknowledgements.equals(that.acknowledgements) &&
                httpStatus.equals(that.httpStatus) &&
                dittoHeaders.equals(that.dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, acknowledgements, httpStatus, dittoHeaders);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "entityId=" + entityId +
                ", acknowledgements=" + acknowledgements +
                ", httpStatus=" + httpStatus +
                ", dittoHeaders=" + dittoHeaders +
                "]";
    }

    @Override
    public ResponseType getResponseType() {
        if (stream().allMatch(Acknowledgement::isSuccess)) {
            return ResponseType.RESPONSE;
        } else {
            return ResponseType.NACK;
        }
    }

    /**
     * Returns all non-hidden marked fields of this Acknowledgement.
     *
     * @return a JSON object representation of this Acknowledgement including only non-hidden marked fields.
     */
    @Override
    public JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    public String getManifest() {
        return getType();
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return getType();
    }

    /**
     * Definition of fields of the JSON representation of an {@link Acknowledgements}.
     */
    @Immutable
    public static final class JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }

        /**
         * Definition of the JSON field for the Acknowledgements' entity ID.
         */
        static final JsonFieldDefinition<String> ENTITY_ID =
                JsonFactory.newStringFieldDefinition("entityId", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the Acknowledgements' entity type.
         */
        static final JsonFieldDefinition<String> ENTITY_TYPE =
                JsonFactory.newStringFieldDefinition("entityType", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the Acknowledgements' statusCode.
         */
        static final JsonFieldDefinition<Integer> STATUS_CODE =
                JsonFactory.newIntFieldDefinition("statusCode", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the Acknowledgements' acknowledgements.
         */
        static final JsonFieldDefinition<JsonObject> ACKNOWLEDGEMENTS =
                JsonFactory.newJsonObjectFieldDefinition("acknowledgements", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

    }

    @FunctionalInterface
    private interface AcknowledgementToJson {

        JsonObject toJson(Acknowledgement ack, JsonSchemaVersion schemaVersion, Predicate<JsonField> predicate);

    }

}
