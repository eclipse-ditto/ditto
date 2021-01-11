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
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.common.ResponseType;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.eclipse.ditto.model.base.entity.type.WithEntityType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.WithOptionalEntity;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

/**
 * Acknowledgements aggregate several {@link Acknowledgement}s and contain an aggregated overall
 * {@link #getHttpStatus() HTTP status} describing the aggregated status of all contained Acknowledgements as well as
 * a {@link #getEntity(JsonSchemaVersion)} returning the contained Json entity.
 *
 * @since 1.1.0
 */
@Immutable
public interface Acknowledgements
        extends Iterable<Acknowledgement>, CommandResponse<Acknowledgements>, WithOptionalEntity, WithEntityType {


    /**
     * Returns the type of an Acknowledgements for the context of the given entity type.
     *
     * @param entityType the type of the entity the Acknowledgements is meant for.
     * @return the type of the Acknowledgements.
     * @throws NullPointerException if {@code entityType} is {@code null}.
     */
    static String getType(final EntityType entityType) {
        return "acknowledgements." + checkNotNull(entityType, "entityType");
    }

    @Override
    default ResponseType getResponseType() {
        if (stream().allMatch(Acknowledgement::isSuccess)) {
            return ResponseType.RESPONSE;
        } else {
            return ResponseType.NACK;
        }
    }

    /**
     * Returns a new instance of {@code Acknowledgements} combining several passed in acknowledgements with a combined
     * status code.
     *
     * @param acknowledgements the acknowledgements to be included in the result.
     * @param dittoHeaders the DittoHeaders of the result.
     * @return the Acknowledgements.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if the given {@code acknowledgements} are empty or if the entity IDs or entity
     * types of the given acknowledgements are not equal.
     */
    static Acknowledgements of(final Collection<? extends Acknowledgement> acknowledgements,
            final DittoHeaders dittoHeaders) {

        return AcknowledgementFactory.newAcknowledgements(acknowledgements, dittoHeaders);
    }

    /**
     * Returns a new {@code Acknowledgements} based on the passed params, including the contained
     * {@link Acknowledgement}s.
     * <p>
     * <em>Should only be used for deserializing from a JSON representation, as {@link #of(Collection, DittoHeaders)}
     * does e.g. the calculation of the correct {@code statusCode}.</em>
     * </p>
     *
     * @param entityId the ID of the affected entity being acknowledged.
     * @param acknowledgements the map of acknowledgements to be included in the result.
     * @param statusCode the status code (HTTP semantics) of the combined Acknowledgements.
     * @param dittoHeaders the headers of the returned Acknowledgements instance.
     * @return the Acknowledgements.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if the given {@code acknowledgements} are empty or if the entity IDs or entity
     * types of the given acknowledgements are not equal.
     * @deprecated as of 2.0.0 please use {@link #of(EntityIdWithType, Collection, HttpStatus, DittoHeaders)} instead.
     */
    @Deprecated
    static Acknowledgements of(final EntityIdWithType entityId,
            final Collection<? extends Acknowledgement> acknowledgements,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {

        return of(entityId, acknowledgements, statusCode.getAsHttpStatus(), dittoHeaders);
    }

    /**
     * Returns a new {@code Acknowledgements} based on the passed params, including the contained
     * {@link Acknowledgement}s.
     * <p><em>
     * Should only be used for deserializing from a JSON representation, as {@link #of(Collection, DittoHeaders)}
     * does e.g. the calculation of the correct {@code httpStatus}.
     * </em></p>
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
    static Acknowledgements of(final EntityIdWithType entityId,
            final Collection<? extends Acknowledgement> acknowledgements,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return AcknowledgementFactory.newAcknowledgements(entityId, acknowledgements, httpStatus, dittoHeaders);
    }

    /**
     * Returns an empty instance of {@code Acknowledgements} for the given entity ID.
     *
     * @param entityId the entity ID for which no acknowledgements were received at all.
     * @param dittoHeaders the headers of the returned Acknowledgements instance.
     * @return the Acknowledgements.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Acknowledgements empty(final EntityIdWithType entityId, final DittoHeaders dittoHeaders) {
        return AcknowledgementFactory.emptyAcknowledgements(entityId, dittoHeaders);
    }

    /**
     * Returns the status code of the Acknowledgements:
     * <ul>
     *     <li>If only one acknowledgement is included, its status code is returned.</li>
     *     <li>
     *         If several acknowledgements are included:
     *         <ul>
     *             <li>
     *                 If all contained acknowledgements are successful, the overall status code is
     *                 {@link HttpStatusCode#OK}.
     *             </li>
     *             <li>
     *                 If at least one acknowledgement failed, the overall status code is
     *                 {@link HttpStatusCode#FAILED_DEPENDENCY}.
     *             </li>
     *         </ul>
     *     </li>
     * </ul>
     *
     * @return the status code.
     * @deprecated as of 2.0.0 please use {@link #getHttpStatus()} instead.
     */
    @Deprecated
    default HttpStatusCode getStatusCode() {
        final HttpStatus httpStatus = getHttpStatus();
        return HttpStatusCode.forInt(httpStatus.getCode()).orElseThrow(() -> {

            // This might happen at runtime when httpStatus has a code which is
            // not reflected as constant in HttpStatusCode.
            final String msgPattern = "Found no HttpStatusCode for int <{0}>!";
            return new IllegalStateException(MessageFormat.format(msgPattern, httpStatus.getCode()));
        });
    }

    /**
     * Returns the HTTP status of the Acknowledgements:
     * <ul>
     *     <li>If only one acknowledgement is included, its status is returned.</li>
     *     <li>
     *         If several acknowledgements are included:
     *         <ul>
     *             <li>
     *                 If all contained acknowledgements are successful, the overall HTTP status is
     *                 {@link HttpStatus#OK}.
     *             </li>
     *             <li>
     *                 If at least one acknowledgement failed, the overall HTTP status is
     *                 {@link HttpStatus#FAILED_DEPENDENCY}.
     *             </li>
     *         </ul>
     *     </li>
     * </ul>
     *
     * @return the HTTP status.
     * @since 2.0.0
     */
    HttpStatus getHttpStatus();

    /**
     * Returns the JSON representation of this Acknowledgements' entity.
     * <ul>
     *     <li>
     *         If only one acknowledgement is included, the {@link Acknowledgement#getEntity(JsonSchemaVersion)} of this
     *         Ack is returned.
     *     </li>
     *     <li>
     *         If several acknowledgements are included, the {@link Acknowledgement#getEntity(JsonSchemaVersion)} of
     *         each Ack is returned in a JsonObject with the AcknowledgementLabel as key of each Ack entry.
     *     </li>
     * </ul>
     *
     * @param schemaVersion the JsonSchemaVersion in which to return the JSON.
     * @return the entity's JSON representation.
     */
    @Override
    Optional<JsonValue> getEntity(JsonSchemaVersion schemaVersion);

    /**
     * Returns a set containing the the AcknowledgementLabels.
     *
     * @return the unanswered acknowledgement labels.
     * Changes on the returned set are not reflected back to this AcknowledgementsPerRequest instance.
     */
    Set<AcknowledgementLabel> getMissingAcknowledgementLabels();

    /**
     * Returns a set containing the the successful acknowledgements.
     *
     * @return the successful acknowledgements.
     * The returned set maintains the order in which the acknowledgement were received.
     * Changes on the returned set are not reflected back to this AcknowledgementsPerRequest instance.
     */
    Set<Acknowledgement> getSuccessfulAcknowledgements();

    /**
     * Returns a set containing the the failed acknowledgements.
     *
     * @return the failed acknowledgements.
     * The returned set maintains the order in which the acknowledgement were received.
     * Changes on the returned set are not reflected back to this AcknowledgementsPerRequest instance.
     */
    Set<Acknowledgement> getFailedAcknowledgements();

    /**
     * Returns the in this Acknowledgements contained acknowledgement identified by the passed
     * {@code acknowledgementLabel}, if it was present. {@link Optional#empty()} otherwise.
     *
     * @param acknowledgementLabel the acknowledgement label to return.
     * @return the found acknowledgement if the {@code acknowledgementLabel} was part of this Acknowledgements, empty
     * Optional otherwise.
     */
    Optional<Acknowledgement> getAcknowledgement(AcknowledgementLabel acknowledgementLabel);

    /**
     * Returns the size of the Acknowledgements, i. e. the number of contained values.
     *
     * @return the size.
     */
    int getSize();

    /**
     * Indicates whether this Acknowledgements is empty.
     *
     * @return {@code true} if this Acknowledgements does not contain any values, {@code false} else.
     */
    boolean isEmpty();

    /**
     * Returns a sequential {@code Stream} with the values of this Acknowledgements as its source.
     *
     * @return a sequential stream of the Acknowledgements of this container.
     */
    Stream<Acknowledgement> stream();

    /**
     * Returns all non hidden marked fields of this Acknowledgement.
     *
     * @return a JSON object representation of this Acknowledgement including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default String getManifest() {
        return getType();
    }

    @Override
    default JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    default String getResourceType() {
        return getType();
    }

    @Override
    EntityIdWithType getEntityId();

    /**
     * Definition of fields of the JSON representation of an {@link Acknowledgements}.
     */
    @Immutable
    final class JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }

        /**
         * Definition of the JSON field for the Acknowledgements' entity ID.
         */
        static final JsonFieldDefinition<String> ENTITY_ID =
                JsonFactory.newStringFieldDefinition("entityId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the Acknowledgements' entity type.
         */
        static final JsonFieldDefinition<String> ENTITY_TYPE =
                JsonFactory.newStringFieldDefinition("entityType", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the Acknowledgements' statusCode.
         */
        static final JsonFieldDefinition<Integer> STATUS_CODE =
                JsonFactory.newIntFieldDefinition("statusCode", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the Acknowledgements' acknowledgements.
         */
        static final JsonFieldDefinition<JsonObject> ACKNOWLEDGEMENTS =
                JsonFactory.newJsonObjectFieldDefinition("acknowledgements", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the Acknowledgements' DittoHeaders.
         */
        static final JsonFieldDefinition<JsonObject> DITTO_HEADERS =
                JsonFactory.newJsonObjectFieldDefinition("dittoHeaders", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

    }

}
