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
package org.eclipse.ditto.internal.models.streaming;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * Represents the ID of an entity with a revision of the entity.
 */
public interface EntityIdWithRevision<I extends EntityId> extends Jsonifiable<JsonObject>,
        IdentifiableStreamingMessage {

    /**
     * Returns the Entity ID of the modified entity.
     *
     * @return the ID of the modified entity.
     */
    I getEntityId();

    /**
     * Returns the revision of the modified entity.
     *
     * @return the revision of the modified entity.
     */
    long getRevision();

    /**
     * Returns this tag as an identifier in the format {@code <id>:<revision>}.
     *
     * @return the tag as an identifier
     */
    default String asIdentifierString() {
        return getEntityId() + ":" + getRevision();
    }

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a EntityIdWithRevision.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the message's entity type.
         */
        public static final JsonFieldDefinition<String> ENTITY_TYPE = JsonFactory.newStringFieldDefinition("type");

        /**
         * JSON field containing the message's ID.
         */
        public static final JsonFieldDefinition<String> ENTITY_ID = JsonFactory.newStringFieldDefinition("id");

        /**
         * JSON field containing the message's revision.
         */
        public static final JsonFieldDefinition<Long> REVISION = JsonFactory.newLongFieldDefinition("revision");


        private JsonFields() {
            throw new AssertionError();
        }

    }
}
