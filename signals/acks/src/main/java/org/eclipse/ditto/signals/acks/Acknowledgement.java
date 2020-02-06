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

import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.base.WithOptionalEntity;

/**
 * An Acknowledgement contains all information about a successful (business) {@code ACK} or a not successful
 * {@code NACK}.
 * <p>
 * Can contain built-in Ditto ACK labels as well as custom ones emitted by external applications.
 * </p>
 */
public interface Acknowledgement extends Jsonifiable.WithPredicate<JsonObject, JsonField>,
        WithDittoHeaders<Acknowledgement>, WithId, WithOptionalEntity {

    /**
     * Returns the label identifying the Acknowledgement.
     * May be a a built-in Ditto ACK label as well as custom one emitted by an external application.
     *
     * @return the label identifying the Acknowledgement.
     */
    AcknowledgementLabel getLabel();

    @Override
    EntityId getEntityId();

    /**
     * Returns the status code of the Acknowledgement specifying whether it was a successful {@code ACK} or a
     * {@code NACK} where the status code is something else than {@code 2xx}.
     *
     * @return the status code of the Acknowledgement.
     */
    int getStatusCode();

    /**
     * Returns the optional payload of the Acknowledgement.
     *
     * @return the optional payload of the Acknowledgement.
     */
    @Override
    Optional<JsonValue> getEntity(JsonSchemaVersion schemaVersion);

    /**
     * Returns all non hidden marked fields of this Acknowledgement.
     *
     * @return a JSON object representation of this Acknowledgement including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    /**
     * Definition of fields of the JSON representation of an {@link Acknowledgement}.
     */
    final class JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }

        /**
         * The type of the Acknowledge label.
         */
        static final JsonFieldDefinition<String> LABEL = JsonFactory.newStringFieldDefinition("label",
                FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * The type of the Acknowledge entity ID.
         */
        static final JsonFieldDefinition<String> ENTITY_ID = JsonFactory.newStringFieldDefinition("entityId",
                FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * The type of the Acknowledge status code.
         */
        static final JsonFieldDefinition<Integer> STATUS_CODE = JsonFactory.newIntFieldDefinition("status",
                FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * The type of the (optional) Acknowledge payload.
         */
        static final JsonFieldDefinition<JsonValue> PAYLOAD = JsonFactory.newJsonValueFieldDefinition("payload",
                FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * The type of the Acknowledge DittoHeaders.
         */
        static final JsonFieldDefinition<JsonObject> DITTO_HEADERS =
                JsonFactory.newJsonObjectFieldDefinition("dittoHeaders",
                        FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

    }
}
