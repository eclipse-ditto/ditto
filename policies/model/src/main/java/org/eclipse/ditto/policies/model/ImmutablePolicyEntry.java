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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
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
    @Nullable private final List<String> namespaces;
    private final ImportableType importableType;
    @Nullable private final Set<AllowedAddition> allowedAdditions;
    private final List<EntryReference> references;

    private ImmutablePolicyEntry(final Label theLabel, final Subjects theSubjects,
            final Resources theResources,
            @Nullable final List<String> namespaces, final ImportableType importableType,
            @Nullable final Set<AllowedAddition> allowedAdditions,
            @Nullable final List<EntryReference> references) {
        label = checkNotNull(theLabel, "label");
        subjects = theSubjects;
        resources = theResources;
        this.namespaces = namespaces != null
                ? Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(namespaces)))
                : null;
        this.importableType = importableType;
        this.allowedAdditions = allowedAdditions != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(allowedAdditions))
                : null;
        this.references = references != null
                ? Collections.unmodifiableList(new ArrayList<>(references))
                : Collections.emptyList();
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
        return new ImmutablePolicyEntry(label, subjects, resources, null,
                ImportableType.IMPLICIT, null, null);
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
        return new ImmutablePolicyEntry(label, subjects, resources, null,
                importableType, null, null);
    }

    /**
     * Returns a new {@code PolicyEntry} object of the given Subjects, Resources and allowed import additions.
     *
     * @param label the Label of the PolicyEntry to create.
     * @param subjects the Subjects contained in the PolicyEntry to create.
     * @param resources the Resources of the PolicyEntry to create.
     * @param importableType specifies whether and how this entry is allowed to be imported by others.
     * @param allowedAdditions which types of additions are allowed when importing this entry.
     * @return a new {@code PolicyEntry} object.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 3.9.0
     */
    public static PolicyEntry of(final Label label, final Subjects subjects, final Resources resources,
            final ImportableType importableType, final Set<AllowedAddition> allowedAdditions) {
        checkNotNull(subjects, "subjects");
        checkNotNull(resources, "resources");
        checkNotNull(importableType, "importableType");
        checkNotNull(allowedAdditions, "allowedAdditions");
        return new ImmutablePolicyEntry(label, subjects, resources, null,
                importableType, allowedAdditions, null);
    }

    /**
     * Returns a new {@code PolicyEntry} object with the given Subjects, Resources, namespace patterns, and import
     * settings.
     *
     * @param label the Label of the PolicyEntry to create.
     * @param subjects the Subjects contained in the PolicyEntry to create.
     * @param resources the Resources of the PolicyEntry to create.
     * @param namespaces the namespace patterns restricting which thing namespaces this entry applies to, or
     * {@code null} if the field is absent (never configured).
     * @param importableType specifies whether and how this entry is allowed to be imported by others.
     * @param allowedAdditions which types of additions are allowed when importing this entry, or {@code null}
     * if the field is absent (never configured).
     * @return a new {@code PolicyEntry} object.
     * @throws NullPointerException if {@code label}, {@code subjects}, {@code resources} or {@code importableType}
     * is {@code null}.
     * @since 3.9.0
     */
    public static PolicyEntry of(final Label label, final Subjects subjects, final Resources resources,
            @Nullable final List<String> namespaces, final ImportableType importableType,
            @Nullable final Set<AllowedAddition> allowedAdditions) {
        checkNotNull(subjects, "subjects");
        checkNotNull(resources, "resources");
        checkNotNull(importableType, "importableType");
        if (namespaces != null) {
            PolicyEntryNamespaces.validate(namespaces);
        }
        return new ImmutablePolicyEntry(label, subjects, resources, namespaces, importableType,
                allowedAdditions, null);
    }

    /**
     * Returns a new {@code PolicyEntry} object with all fields including references.
     *
     * @param label the Label of the PolicyEntry to create.
     * @param subjects the Subjects contained in the PolicyEntry to create.
     * @param resources the Resources of the PolicyEntry to create.
     * @param namespaces the namespace patterns, or {@code null} if absent.
     * @param importableType specifies whether and how this entry is allowed to be imported by others.
     * @param allowedAdditions which types of additions are allowed, or {@code null} if absent.
     * @param references the list of entry references, or {@code null} if absent.
     * @return a new {@code PolicyEntry} object.
     * @throws NullPointerException if {@code label}, {@code subjects}, {@code resources} or {@code importableType}
     * is {@code null}.
     * @since 3.9.0
     */
    public static PolicyEntry of(final Label label, final Subjects subjects, final Resources resources,
            @Nullable final List<String> namespaces, final ImportableType importableType,
            @Nullable final Set<AllowedAddition> allowedAdditions,
            @Nullable final List<EntryReference> references) {
        checkNotNull(subjects, "subjects");
        checkNotNull(resources, "resources");
        checkNotNull(importableType, "importableType");
        if (namespaces != null) {
            PolicyEntryNamespaces.validate(namespaces);
        }
        return new ImmutablePolicyEntry(label, subjects, resources, namespaces, importableType,
                allowedAdditions, references);
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
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static PolicyEntry fromJson(final CharSequence label, final JsonObject jsonObject) {
        return fromJson(label, jsonObject, false);
    }

    /**
     * Variant of {@link #fromJson(CharSequence, JsonObject)} for deserializing a resolved-view response: accepts
     * {@code imported-*} labels. Never use this on user-submitted bodies.
     *
     * @since 3.9.0
     */
    public static PolicyEntry fromJsonAcceptingImportedLabels(final CharSequence label, final JsonObject jsonObject) {
        return fromJson(label, jsonObject, true);
    }

    private static PolicyEntry fromJson(final CharSequence label, final JsonObject jsonObject,
            final boolean acceptImportedLabels) {
        checkNotNull(jsonObject, "JSON object");
        final Label lbl = acceptImportedLabels && label.toString().startsWith(ImmutableImportedLabel.IMPORTED_PREFIX + "-")
                ? Label.ofImported(label)
                : Label.of(label);

        final Subjects subjectsFromJson = jsonObject.getValue(JsonFields.SUBJECTS)
                .map(PoliciesModelFactory::newSubjects)
                .orElseGet(PoliciesModelFactory::emptySubjects);
        final Resources resourcesFromJson = jsonObject.getValue(JsonFields.RESOURCES)
                .map(PoliciesModelFactory::newResources)
                .orElseGet(PoliciesModelFactory::emptyResources);
        final List<String> namespacesFromJson = readNamespaces(jsonObject);
        final ImportableType importType = readImportableType(jsonObject).orElse(ImportableType.IMPLICIT);
        final Set<AllowedAddition> additions = readAllowedAdditions(jsonObject);
        final List<EntryReference> referencesFromJson = readReferences(jsonObject);
        return new ImmutablePolicyEntry(lbl, subjectsFromJson, resourcesFromJson, namespacesFromJson,
                importType, additions, referencesFromJson);
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

    @Nullable
    private static Set<AllowedAddition> readAllowedAdditions(final JsonObject json) {
        return json.getValue(JsonFields.ALLOWED_ADDITIONS)
                .map(PoliciesModelFactory::parseAllowedAdditions)
                .orElse(null);
    }

    @Nullable
    private static List<String> readNamespaces(final JsonObject json) {
        return json.getValue(JsonFields.NAMESPACES)
                .map(PolicyEntryNamespaces::fromJsonArray)
                .orElse(null);
    }

    @Nullable
    private static List<EntryReference> readReferences(final JsonObject json) {
        return json.getValue(JsonFields.REFERENCES)
                .map(PoliciesModelFactory::parseEntryReferences)
                .orElse(null);
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
    public Optional<List<String>> getNamespaces() {
        return Optional.ofNullable(namespaces);
    }

    @Override
    public ImportableType getImportableType() {
        return importableType;
    }

    @Override
    public Optional<Set<AllowedAddition>> getAllowedAdditions() {
        return Optional.ofNullable(allowedAdditions);
    }

    @Override
    public List<EntryReference> getReferences() {
        return references;
    }

    @Override
    public boolean isSemanticallySameAs(final PolicyEntry otherPolicyEntry) {
        return subjects.isSemanticallySameAs(otherPolicyEntry.getSubjects()) &&
                resources.equals(otherPolicyEntry.getResources()) &&
                Objects.equals(namespaces, otherPolicyEntry.getNamespaces().orElse(null)) &&
                importableType.equals(otherPolicyEntry.getImportableType()) &&
                Objects.equals(allowedAdditions,
                        otherPolicyEntry.getAllowedAdditions().orElse(null)) &&
                Objects.equals(references, otherPolicyEntry.getReferences());
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final org.eclipse.ditto.json.JsonObjectBuilder builder = JsonFactory.newObjectBuilder()
                .set(JsonFields.SUBJECTS, subjects.toJson(schemaVersion, thePredicate), predicate)
                .set(JsonFields.RESOURCES, resources.toJson(schemaVersion, thePredicate), predicate);
        if (namespaces != null) {
            final JsonArrayBuilder namespacesBuilder = JsonFactory.newArrayBuilder();
            for (final String namespace : namespaces) {
                namespacesBuilder.add(JsonValue.of(namespace));
            }
            builder.set(JsonFields.NAMESPACES, namespacesBuilder.build(), predicate);
        }
        builder.set(JsonFields.IMPORTABLE_TYPE, importableType.toString(), predicate);
        if (allowedAdditions != null) {
            final JsonArrayBuilder arrayBuilder = JsonFactory.newArrayBuilder();
            for (final AllowedAddition addition : allowedAdditions) {
                arrayBuilder.add(JsonValue.of(addition.getName()));
            }
            builder.set(JsonFields.ALLOWED_ADDITIONS, arrayBuilder.build(), predicate);
        }
        if (!references.isEmpty()) {
            final JsonArrayBuilder refsBuilder = JsonFactory.newArrayBuilder();
            for (final EntryReference ref : references) {
                refsBuilder.add(ref.toJson(schemaVersion, thePredicate));
            }
            builder.set(JsonFields.REFERENCES, refsBuilder.build(), predicate);
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
                Objects.equals(namespaces, that.namespaces) &&
                Objects.equals(importableType, that.importableType) &&
                Objects.equals(allowedAdditions, that.allowedAdditions) &&
                Objects.equals(references, that.references);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, subjects, resources, namespaces, importableType, allowedAdditions,
                references);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "label=" + label +
                ", subjects=" + subjects +
                ", resources=" + resources +
                ", namespaces=" + namespaces +
                ", importableType=" + importableType +
                ", allowedAdditions=" + allowedAdditions +
                ", references=" + references +
                "]";
    }

}
