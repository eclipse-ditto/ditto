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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;

/**
 * An immutable implementation of {@link PolicyEntry}.
 */
@Immutable
final class ImmutablePolicyEntry implements PolicyEntry {

    private final Label label;
    private final Subjects subjects;
    private final Resources resources;
    private final ImportableType importableType;

    private ImmutablePolicyEntry(final Label theLabel, final Subjects theSubjects, final Resources theResources,
            final ImportableType importableType) {
        label = checkNotNull(theLabel, "label");
        subjects = theSubjects;
        resources = theResources;
        this.importableType = importableType;
    }

    /**
     * Returns a new {@code PolicyEntry} object of the given Subjects and Resources.
     *
     * @param label the Label of the PolicyEntry to create.
     * @param subjects the Subjects contained in the PolicyEntry to create.
     * @param resources the Resources of the PolicyEntry to create.
     * @return a new {@code PolicyEntry} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PolicyEntry of(final Label label, final Subjects subjects, final Resources resources) {
        checkNotNull(subjects, "subjects");
        checkNotNull(resources, "resources");
        return new ImmutablePolicyEntry(label, subjects, resources, ImportableType.IMPLICIT);
    }

    /**
     * Returns a new {@code PolicyEntry} object of the given Subjects and Resources.
     *
     * @param label the Label of the PolicyEntry to create.
     * @param subjects the Subjects contained in the PolicyEntry to create.
     * @param resources the Resources of the PolicyEntry to create.
     * @param importableType specifies whether and how this entry is allowed to be imported by others
     * @return a new {@code PolicyEntry} object.
     * @throws NullPointerException if any argument is {@code null}.
    * @since 3.1.0
     */
    public static PolicyEntry of(final Label label, final Subjects subjects, final Resources resources,
            final ImportableType importableType) {
        checkNotNull(subjects, "subjects");
        checkNotNull(resources, "resources");
        checkNotNull(importableType, "importableType");
        return new ImmutablePolicyEntry(label, subjects, resources, importableType);
    }

    /**
     * Creates a new {@code PolicyEntry} object from the specified JSON object.
     *
     * @param label the Label for the PolicyEntry to create.
     * @param jsonObject a JSON object which provides the data for the Policy entry to be created. If there are more
     * than one entries in the given JSON object, it'll take the first in the object.
     * @return a new Policy entry which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code label} or {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     * @throws DittoJsonException if {@code jsonObject}
     * <ul>
     *     <li>is empty or</li>
     *     <li>contains only a field with the schema version.</li>
     * </ul>
     */
    public static PolicyEntry fromJson(final CharSequence label, final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");
        final Label lbl = Label.of(label);

        try {
            final JsonObject subjectsJsonObject = jsonObject.getValueOrThrow(JsonFields.SUBJECTS);
            final Subjects subjectsFromJson = PoliciesModelFactory.newSubjects(subjectsJsonObject);
            final JsonObject resourcesJsonObject = jsonObject.getValueOrThrow(JsonFields.RESOURCES);
            final Resources resourcesFromJson = PoliciesModelFactory.newResources(resourcesJsonObject);
            final Optional<ImportableType> importableTypeOpt = readImportableType(jsonObject);
            return importableTypeOpt
                    .map(importableType -> of(lbl, subjectsFromJson, resourcesFromJson, importableType))
                    .orElseGet(() -> of(lbl, subjectsFromJson, resourcesFromJson));
        } catch (final JsonMissingFieldException e) {
            throw new DittoJsonException(e);
        }
    }

    private static Optional<ImportableType> readImportableType(final JsonObject json) {
        return json.getValue(JsonFields.IMPORTABLE_TYPE).map(typeFromJson -> ImportableType.forName(typeFromJson)
                .orElseThrow(() -> PolicyEntryInvalidException
                        .newBuilder()
                        .description(() -> {
                            final JsonPointer field = JsonFields.IMPORTABLE_TYPE.getPointer();
                            final String validTypes = Arrays.toString(ImportableType.values());
                            return String.format("The value '%s' of field '%s' is not valid. Valid values are: %s", typeFromJson, field, validTypes);
                        })
                        .build()));
    }

    @Override
    public Label getLabel() {
        return label;
    }

    @Override
    public Subjects getSubjects() {
        return subjects;
    }

    @Override
    public Resources getResources() {
        return resources;
    }

    @Override
    public ImportableType getImportableType() {
        return importableType;
    }

    @Override
    public boolean isSemanticallySameAs(final PolicyEntry otherPolicyEntry) {
        return subjects.isSemanticallySameAs(otherPolicyEntry.getSubjects()) &&
                resources.equals(otherPolicyEntry.getResources()) &&
                importableType.equals(otherPolicyEntry.getImportableType());
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.SUBJECTS, subjects.toJson(schemaVersion, thePredicate), predicate)
                .set(JsonFields.RESOURCES, resources.toJson(schemaVersion, thePredicate), predicate)
                .set(JsonFields.IMPORTABLE_TYPE, importableType.toString(), predicate)
                .build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutablePolicyEntry that = (ImmutablePolicyEntry) o;
        return Objects.equals(label, that.label) &&
                Objects.equals(subjects, that.subjects) &&
                Objects.equals(resources, that.resources) &&
                Objects.equals(importableType, that.importableType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, subjects, resources, importableType);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "label=" + label +
                ", subjects=" + subjects +
                ", resources=" + resources +
                ", importableType=" + importableType +
                "]";
    }

}
