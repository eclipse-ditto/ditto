/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.devops;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.AbstractNamespacedEntityId;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.entity.id.TypedEntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigInvalidException;

/**
 * Java representation of a WoT validation config ID.
 *
 * @since 3.8.0
 */
@Immutable
@TypedEntityId(type = "wot-validation-config")
public final class WotValidationConfigId extends AbstractNamespacedEntityId implements EntityId, Jsonifiable<JsonObject> {

    private static final EntityType ENTITY_TYPE = EntityType.of("wot-validation-config");
    private final NamespacedEntityId id;
    private static final JsonFieldDefinition<String> ID_FIELD =
            JsonFactory.newStringFieldDefinition("id");

    private WotValidationConfigId(final NamespacedEntityId namespacedEntityId) {
        super(ENTITY_TYPE, namespacedEntityId);
        this.id = namespacedEntityId;
    }

    /**
     * Returns a WoT validation config ID based on the given ID.
     *
     * @param configId the ID of the config.
     * @return the WoT validation config ID.
     */
    public static WotValidationConfigId of(final CharSequence configId) {
        final String id = configId.toString();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("The config ID must not be empty.");
        }
        return new WotValidationConfigId(new AbstractNamespacedEntityId(ENTITY_TYPE, id) {
            @Override
            public String toString() {
                return id;
            }
        });
    }

    /**
     * Returns a WoT validation config ID based on the given namespace and name.
     *
     * @param namespace the namespace of the config.
     * @param name the name of the config.
     * @return the WoT validation config ID.
     */
    public static WotValidationConfigId of(final String namespace, final String name) {
        if (namespace == null || namespace.isEmpty()) {
            throw WotValidationConfigInvalidException.newBuilder("The namespace must not be empty.")
                    .build();
        }
        if (name == null || name.isEmpty()) {
            throw WotValidationConfigInvalidException.newBuilder("The name must not be empty.")
                    .build();
        }
        final String id = namespace + ":" + name;
        return new WotValidationConfigId(new AbstractNamespacedEntityId(ENTITY_TYPE, id) {
            @Override
            public String toString() {
                return id;
            }
        });
    }

    /**
     * The global WoT validation config id.
     */
    public static final WotValidationConfigId GLOBAL = WotValidationConfigId.of("ditto:global");
    public static final WotValidationConfigId MERGED = WotValidationConfigId.of("ditto:merged");

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final WotValidationConfigId that = (WotValidationConfigId) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id.toString();
    }

    @Override
    public JsonObject toJson() {
        return JsonFactory.newObjectBuilder()
                .set(ID_FIELD, id.toString())
                .build();
    }


    /**
     * Creates a {@link WotValidationConfigId} from a JSON object.
     * <p>
     * The JSON object must contain an <code>id</code> field.
     * </p>
     *
     * @param jsonObject the JSON object containing the config ID.
     * @return the corresponding {@code WotValidationConfigId}.
     */
    public static WotValidationConfigId fromJson(final JsonObject jsonObject) {
        final String id = jsonObject.getValueOrThrow(ID_FIELD);
        return WotValidationConfigId.of(id);
    }
}