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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * An immutable implementation of {@link PolicyEntry}.
 */
@Immutable
final class ImmutablePolicyEntry implements PolicyEntry {

    private final Label label;
    private final Subjects subjects;
    private final Resources resources;

    private ImmutablePolicyEntry(final Label theLabel, final Subjects theSubjects, final Resources theResources) {
        label = checkNotNull(theLabel, "label");
        subjects = theSubjects;
        resources = theResources;
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
        return new ImmutablePolicyEntry(label, subjects, resources);
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

            return of(lbl, subjectsFromJson, resourcesFromJson);
        } catch (final JsonMissingFieldException e) {
            throw new DittoJsonException(e);
        }
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
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate)
                .set(JsonFields.SUBJECTS, subjects.toJson(schemaVersion, thePredicate), predicate)
                .set(JsonFields.RESOURCES, resources.toJson(schemaVersion, thePredicate), predicate)
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
                Objects.equals(resources, that.resources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, subjects, resources);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "label=" + label +
                ", subjects=" + subjects +
                ", resources=" + resources +
                "]";
    }

}
