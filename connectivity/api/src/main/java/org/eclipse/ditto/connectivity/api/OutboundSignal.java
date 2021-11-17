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
package org.eclipse.ditto.connectivity.api;

import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.protocol.Adaptable;

import akka.actor.ActorRef;

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
     * @return extra fields of an enriched signal. Only relevant during message mapping.
     */
    default Optional<JsonObject> getExtra() {
        return Optional.empty();
    }

    /**
     * Returns all non-hidden marked fields of this {@code Connection}.
     *
     * @return a JSON object representation of this Connection including only non-hidden marked fields.
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
     * Extends the {@link OutboundSignal} by adding the payload mapping used to map the signal.
     */
    interface Mappable extends OutboundSignal {

        /**
         * @return the {@link PayloadMapping} common to all {@link Target}s.
         */
        PayloadMapping getPayloadMapping();

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
     * Collection of outbound signals mapped from the same signal.
     */
    interface MultiMapped extends OutboundSignal {

        /**
         * @return the first mapped signal.
         */
        Mapped first();

        /**
         * @return a list of outbound signals mapped from mapping 1 signal.
         */
        List<Mapped> getMappedOutboundSignals();

        /**
         * @return sender of the original signal.
         */
        Optional<ActorRef> getSender();
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
                JsonFactory.newJsonObjectFieldDefinition("source", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code OutboundSignal} {@code target}.
         */
        static final JsonFieldDefinition<JsonArray> TARGETS =
                JsonFactory.newJsonArrayFieldDefinition("targets", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code DittoHeaders} of the {@code source} {@link Signal}.
         */
        static final JsonFieldDefinition<JsonObject> JSON_DITTO_HEADERS =
                JsonFactory.newJsonObjectFieldDefinition("dittoHeaders", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
