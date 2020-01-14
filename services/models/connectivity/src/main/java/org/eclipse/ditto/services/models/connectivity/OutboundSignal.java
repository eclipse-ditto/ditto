/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.models.connectivity;

import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Represents an outbound signal i.e. a signal that is sent from Ditto to an external target. It contains the
 * original signal and the set of targets where this signal should be delivered.
 */
public interface OutboundSignal extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * @return the originating signal.
     */
    Signal<?> getSource();

    /**
     * @return the targets that are authorized to read and subscribed for the outbound signal.
     */
    List<Target> getTargets();

    /**
     * Returns all non hidden marked fields of this {@code Connection}.
     *
     * @return a JSON object representation of this Connection including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.notHidden()).get(fieldSelector);
    }

    /**
     * Extends the {@link OutboundSignal} by adding the mapped message.
     */
    interface Mapped extends OutboundSignal {

        /**
         * @return the {@link ExternalMessage} that was mapped from the outbound signal.
         */
        ExternalMessage getExternalMessage();

        /**
         * @return the Ditto protocol message after adaptation.
         */
        Adaptable getAdaptable();
    }

    /**
     * An enumeration of the known {@code JsonField}s of a {@code OutboundSignal}.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@code OutboundSignal} {@code source} {@link Signal}.
         */
        static final JsonFieldDefinition<JsonObject> SOURCE =
                JsonFactory.newJsonObjectFieldDefinition("source", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code OutboundSignal} {@code target}.
         */
        static final JsonFieldDefinition<JsonArray> TARGETS =
                JsonFactory.newJsonArrayFieldDefinition("targets", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code DittoHeaders} of the {@code source} {@link Signal}.
         */
        static final JsonFieldDefinition<JsonObject> JSON_DITTO_HEADERS =
                JsonFactory.newJsonObjectFieldDefinition("dittoHeaders", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
