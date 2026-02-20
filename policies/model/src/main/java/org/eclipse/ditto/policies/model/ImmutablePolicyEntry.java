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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * An immutable implementation of {@link PolicyEntry}.
 */
@Immutable
final class ImmutablePolicyEntry implements PolicyEntry {

    private final Label label;
    private final Subjects subjects;
    private final Resources resources;
    private final ImportableType importableType;
    private final Set<AllowedImportAddition> allowedImportAdditions;

    private ImmutablePolicyEntry(final Label theLabel, final Subjects theSubjects, final Resources theResources,
            final ImportableType importableType, final Set<AllowedImportAddition> allowedImportAdditions) {
        label = checkNotNull(theLabel, "label");
        subjects = theSubjects;
        resources = theResources;
        this.importableType = importableType;
        this.allowedImportAdditions = Collections.unmodifiableSet(
                new LinkedHashSet<>(allowedImportAdditions));
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
        return new ImmutablePolicyEntry(label, subjects, resources, ImportableType.IMPLICIT,
                Collections.emptySet());
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
        return new ImmutablePolicyEntry(label, subjects, resources, importableType, Collections.emptySet());
    }

    /**
     * Returns a new {@code PolicyEntry} object of the given Subjects, Resources and allowed import additions.
     *
     * @param label the Label of the PolicyEntry to create.
     * @param subjects the Subjects contained in the PolicyEntry to create.
     * @param resources the Resources of the PolicyEntry to create.
     * @param importableType specifies whether and how this entry is allowed to be imported by others.
     * @param allowedImportAdditions which types of additions are allowed when importing this entry.
     * @return a new {@code PolicyEntry} object.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 3.9.0
     */
    public static PolicyEntry of(final Label label, final Subjects subjects, final Resources resources,
            final ImportableType importableType, final Set<AllowedImportAddition> allowedImportAdditions) {
        checkNotNull(subjects, "subjects");
        checkNotNull(resources, "resources");
        checkNotNull(importableType, "importableType");
        checkNotNull(allowedImportAdditions, "allowedImportAdditions");
        return new ImmutablePolicyEntry(label, subjects, resources, importableType, allowedImportAdditions);
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
            final ImportableType importType = readImportableType(jsonObject).orElse(ImportableType.IMPLICIT);
            final Set<AllowedImportAddition> additions = readAllowedImportAdditions(jsonObject);
            return new ImmutablePolicyEntry(lbl, subjectsFromJson, resourcesFromJson, importType, additions);
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

    private static Set<AllowedImportAddition> readAllowedImportAdditions(final JsonObject json) {
        return json.getValue(JsonFields.ALLOWED_IMPORT_ADDITIONS)
                .map(array -> array.stream()
                        .filter(JsonValue::isString)
                        .map(JsonValue::asString)
                        .map(value -> AllowedImportAddition.forName(value)
                                .orElseThrow(() -> PolicyEntryInvalidException.newBuilder()
                                        .description("The value '" + value + "' of field '" +
                                                JsonFields.ALLOWED_IMPORT_ADDITIONS.getPointer() +
                                                "' is not valid. Valid values are: " +
                                                Arrays.toString(AllowedImportAddition.values()))
                                        .build()))
                        .collect(Collectors.<AllowedImportAddition, Set<AllowedImportAddition>>toCollection(
                                LinkedHashSet::new)))
                .map(Collections::unmodifiableSet)
                .orElse(Collections.emptySet());
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
    public Set<AllowedImportAddition> getAllowedImportAdditions() {
        return allowedImportAdditions;
    }

    @Override
    public boolean isSemanticallySameAs(final PolicyEntry otherPolicyEntry) {
        return subjects.isSemanticallySameAs(otherPolicyEntry.getSubjects()) &&
                resources.equals(otherPolicyEntry.getResources()) &&
                importableType.equals(otherPolicyEntry.getImportableType()) &&
                allowedImportAdditions.equals(otherPolicyEntry.getAllowedImportAdditions());
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final org.eclipse.ditto.json.JsonObjectBuilder builder = JsonFactory.newObjectBuilder()
                .set(JsonFields.SUBJECTS, subjects.toJson(schemaVersion, thePredicate), predicate)
                .set(JsonFields.RESOURCES, resources.toJson(schemaVersion, thePredicate), predicate)
                .set(JsonFields.IMPORTABLE_TYPE, importableType.toString(), predicate);
        if (!allowedImportAdditions.isEmpty()) {
            final JsonArrayBuilder arrayBuilder = JsonFactory.newArrayBuilder();
            for (final AllowedImportAddition addition : allowedImportAdditions) {
                arrayBuilder.add(JsonValue.of(addition.getName()));
            }
            builder.set(JsonFields.ALLOWED_IMPORT_ADDITIONS, arrayBuilder.build(), predicate);
        }
        return builder.build();
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
                Objects.equals(importableType, that.importableType) &&
                Objects.equals(allowedImportAdditions, that.allowedImportAdditions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, subjects, resources, importableType, allowedImportAdditions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "label=" + label +
                ", subjects=" + subjects +
                ", resources=" + resources +
                ", importableType=" + importableType +
                ", allowedImportAdditions=" + allowedImportAdditions +
                "]";
    }

}
