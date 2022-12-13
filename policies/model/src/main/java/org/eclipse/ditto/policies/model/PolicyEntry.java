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
package org.eclipse.ditto.policies.model;

import javax.annotation.Nonnull;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;

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
     * Returns a new {@code PolicyEntry} with the specified {@code label}, {@code subjects} and {@code resources}.
     *
     * @param label the Label of the PolicyEntry to create.
     * @param subjects the Subjects contained in the PolicyEntry to create.
     * @param resources the Resources of the PolicyEntry to create.
     * @param importableType whether the entry is importable by othersa.
     * @return the new {@code PolicyEntry}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     * @since 3.1.0
     */
    static PolicyEntry newInstance(final CharSequence label, final Iterable<Subject> subjects,
            final Iterable<Resource> resources, final ImportableType importableType) {
        return PoliciesModelFactory.newPolicyEntry(label, subjects, resources, importableType);
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
     * Returns whether/how this Policy Entry is allowed to be imported by others.
     *
     * @return whether/how this entry is importable.
     * @since 3.1.0
     */
    ImportableType getImportableType();

    /**
     * Checks if the passed {@code otherPolicyEntry} is semantically the same as this entry.
     * I.e. that it contains the same subject ids having the same resources.
     *
     * @param otherPolicyEntry the other policy entry to check against.
     * @return {@code true} if the other policy entry is semantically the same as this one.
     * @since 3.0.0
     */
    boolean isSemanticallySameAs(PolicyEntry otherPolicyEntry);

    /**
     * Returns all non-hidden marked fields of this Policy entry.
     *
     * @return a JSON object representation of this Policy entry including only non-hidden marked fields.
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
         * JSON field containing the PolicyEntry's subjects type.
         */
        public static final JsonFieldDefinition<JsonObject> SUBJECTS =
                JsonFactory.newJsonObjectFieldDefinition("subjects", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the PolicyEntry's resources type.
         */
        public static final JsonFieldDefinition<JsonObject> RESOURCES =
                JsonFactory.newJsonObjectFieldDefinition("resources", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the PolicyEntry's importable type.
         * @since 3.1.0
         */
        public static final JsonFieldDefinition<String> IMPORTABLE_TYPE = JsonFactory
                .newStringFieldDefinition("importable", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
