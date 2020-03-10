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
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithOptionalEntity;

/**
 * Acknowledgements aggregate several {@link Acknowledgement}s and contain an aggregated overall
 * {@link #getStatusCode() statusCode} describing the aggregated status of all contained Acknowledgements as well as
 * a {@link #getEntity(JsonSchemaVersion)} returning the contained Json entity.
 *
 * @since 1.1.0
 */
@Immutable
public interface Acknowledgements extends Iterable<Acknowledgement>, Signal<Acknowledgements>, WithOptionalEntity {

    /**
     * Type of the Acknowledgements.
     */
    String TYPE = "acknowledgements";

    /**
     * Returns a new instance of {@code Acknowledgements} combining several passed in acknowledgements with a combined
     * status code.
     *
     * @param acknowledgements the acknowledgements to be included in the result.
     * @param dittoHeaders the DittoHeaders of the result.
     * @return the Acknowledgements.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if the given {@code acknowledgements} are empty or if the entity IDs of the
     * given acknowledgements are not equal.
     */
    static Acknowledgements of(final Collection<Acknowledgement> acknowledgements, final DittoHeaders dittoHeaders) {
        return AcknowledgementFactory.newAcknowledgements(acknowledgements, dittoHeaders);
    }

    /**
     * Returns an empty instance of {@code Acknowledgements} for the given entity ID.
     *
     * @param entityId the entity ID for which no acknowledgements were received at all.
     * @param dittoHeaders the headers of the returned Acknowledgements instance.
     * @return the Acknowledgements.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Acknowledgements empty(final EntityId entityId, final DittoHeaders dittoHeaders) {
        return AcknowledgementFactory.emptyAcknowledgements(entityId, dittoHeaders);
    }

    /**
     * Returns a new {@code Acknowledgements} parsed from the given JSON object.
     *
     * @param jsonObject the JSON object to be parsed.
     * @return the Acknowledgements.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} misses a required field.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contained an unexpected value type.
     */
    static Acknowledgements fromJson(final JsonObject jsonObject) {
        return AcknowledgementFactory.acknowledgementsFromJson(jsonObject);
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
     * @return the status code.
     */
    HttpStatusCode getStatusCode();

    /**
     * Returns the JSON representation of this Acknowledgement's entity.
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
    default String getType() {
        return TYPE;
    }

    @Override
    default JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    default String getResourceType() {
        return getType();
    }

    /**
     * Definition of fields of the JSON representation of an {@link Acknowledgements}.
     */
    @Immutable
    final class JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }

        /**
         * The type of the Acknowledgements entity ID.
         */
        static final JsonFieldDefinition<String> ENTITY_ID =
                JsonFactory.newStringFieldDefinition("entityId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * The type of the Acknowledgements' statusCode.
         */
        static final JsonFieldDefinition<Integer> STATUS_CODE =
                JsonFactory.newIntFieldDefinition("statusCode", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * The type of the Acknowledgements' acknowledgements.
         */
        static final JsonFieldDefinition<JsonObject> ACKNOWLEDGEMENTS =
                JsonFactory.newJsonObjectFieldDefinition("acknowledgements", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * The type of the Acknowledgements DittoHeaders.
         */
        static final JsonFieldDefinition<JsonObject> DITTO_HEADERS =
                JsonFactory.newJsonObjectFieldDefinition("dittoHeaders", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

    }

}
