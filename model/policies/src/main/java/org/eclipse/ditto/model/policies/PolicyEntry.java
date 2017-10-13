/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.policies;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Represents a single entry of a {@link Policy} consisting of a {@code label}, {@code subjects} and {@code resources}.
 */
public interface PolicyEntry extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new {@code PolicyEntry} with the specified {@code label}, {@code subjects} and {@code resources}.
     *
     * @param label the Label of the PolicyEntry to create.
     * @param subjects the Subjects contained in the PolicyEntry to create.
     * @param resources the Resources of the PolicyEntry to create.
     * @return the new {@code PolicyEntry}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    static PolicyEntry newInstance(final CharSequence label, final Iterable<Subject> subjects,
            final Iterable<Resource> resources) {

        return PoliciesModelFactory.newPolicyEntry(label, subjects, resources);
    }

    /**
     * PolicyEntry is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of PolicyEntry.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns the {@link Label} of this Policy Entry.
     *
     * @return this entry's label.
     */
    Label getLabel();

    /**
     * Returns the {@link Subjects} of this Policy Entry.
     *
     * @return the Subjects of this Policy Entry.
     */
    Subjects getSubjects();

    /**
     * Returns the {@link Resources} of this Policy Entry.
     *
     * @return the Resources of this Policy Entry.
     */
    Resources getResources();

    /**
     * Returns all non hidden marked fields of this Policy entry.
     *
     * @return a JSON object representation of this Policy entry including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.regularOrSpecial()).get(fieldSelector);
    }

    /**
     * An enumeration of the known {@link JsonField}s of a PolicyEntry.
     */
    @Nonnull
    final class JsonFields {

        /**
         * JSON field containing the {@link JsonSchemaVersion}.
         */
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION =
                JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL, FieldType.HIDDEN,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the PolicyEntry's subjects type.
         */
        public static final JsonFieldDefinition<JsonObject> SUBJECTS =
                JsonFactory.newJsonObjectFieldDefinition("subjects", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the PolicyEntry's resources type.
         */
        public static final JsonFieldDefinition<JsonObject> RESOURCES =
                JsonFactory.newJsonObjectFieldDefinition("resources", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
