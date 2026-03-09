/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * An immutable implementation of {@link EntryAddition}.
 */
@Immutable
final class ImmutableEntryAddition implements EntryAddition {

    private final Label label;
    @Nullable private final Subjects subjects;
    @Nullable private final Resources resources;

    private ImmutableEntryAddition(final Label label, @Nullable final Subjects subjects,
            @Nullable final Resources resources) {
        this.label = checkNotNull(label, "label");
        this.subjects = subjects;
        this.resources = resources;
    }

    /**
     * Returns a new {@code EntryAddition} with the given parameters.
     *
     * @param label the label of the imported policy entry this addition applies to.
     * @param subjects the additional subjects, or {@code null}.
     * @param resources the additional resources, or {@code null}.
     * @return the new EntryAddition.
     * @throws NullPointerException if {@code label} is {@code null}.
     */
    public static EntryAddition of(final Label label, @Nullable final Subjects subjects,
            @Nullable final Resources resources) {
        return new ImmutableEntryAddition(label, subjects, resources);
    }

    /**
     * Creates a new {@code EntryAddition} from the specified JSON object.
     *
     * @param label the label of the imported policy entry this addition applies to.
     * @param jsonObject the JSON object providing the data.
     * @return a new EntryAddition.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static EntryAddition fromJson(final Label label, final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");

        final Subjects subjects = jsonObject.getValue(PolicyEntry.JsonFields.SUBJECTS)
                .map(PoliciesModelFactory::newSubjects)
                .orElse(null);
        final Resources resources = jsonObject.getValue(PolicyEntry.JsonFields.RESOURCES)
                .map(PoliciesModelFactory::newResources)
                .orElse(null);

        return of(label, subjects, resources);
    }

    @Override
    public Label getLabel() {
        return label;
    }

    @Override
    public Optional<Subjects> getSubjects() {
        return Optional.ofNullable(subjects);
    }

    @Override
    public Optional<Resources> getResources() {
        return Optional.ofNullable(resources);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        if (subjects != null) {
            builder.set(PolicyEntry.JsonFields.SUBJECTS, subjects.toJson(schemaVersion, thePredicate), predicate);
        }
        if (resources != null) {
            builder.set(PolicyEntry.JsonFields.RESOURCES, resources.toJson(schemaVersion, thePredicate), predicate);
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
        final ImmutableEntryAddition that = (ImmutableEntryAddition) o;
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
