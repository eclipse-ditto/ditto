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
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * An immutable implementation of {@link SubjectAliasTarget}.
 */
@Immutable
final class ImmutableSubjectAliasTarget implements SubjectAliasTarget {

    private final PolicyId importedPolicyId;
    private final Label entryLabel;

    private ImmutableSubjectAliasTarget(final PolicyId importedPolicyId, final Label entryLabel) {
        this.importedPolicyId = checkNotNull(importedPolicyId, "importedPolicyId");
        this.entryLabel = checkNotNull(entryLabel, "entryLabel");
    }

    /**
     * Returns a new {@code SubjectAliasTarget} with the given parameters.
     *
     * @param importedPolicyId the ID of the imported policy.
     * @param entryLabel the label of the entry within the import's entries additions.
     * @return the new SubjectAliasTarget.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SubjectAliasTarget of(final PolicyId importedPolicyId, final Label entryLabel) {
        return new ImmutableSubjectAliasTarget(importedPolicyId, entryLabel);
    }

    /**
     * Creates a new {@code SubjectAliasTarget} from the specified JSON object.
     *
     * @param jsonObject the JSON object providing the data.
     * @return a new SubjectAliasTarget.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static SubjectAliasTarget fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");
        final PolicyId importedPolicyId = PolicyId.of(jsonObject.getValueOrThrow(SubjectAliasTarget.JsonFields.IMPORT));
        final Label entryLabel = Label.of(jsonObject.getValueOrThrow(SubjectAliasTarget.JsonFields.ENTRY));
        return of(importedPolicyId, entryLabel);
    }

    @Override
    public PolicyId getImportedPolicyId() {
        return importedPolicyId;
    }

    @Override
    public Label getEntryLabel() {
        return entryLabel;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        builder.set(SubjectAliasTarget.JsonFields.IMPORT, importedPolicyId.toString(), predicate);
        builder.set(SubjectAliasTarget.JsonFields.ENTRY, entryLabel.toString(), predicate);
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
        final ImmutableSubjectAliasTarget that = (ImmutableSubjectAliasTarget) o;
        return Objects.equals(importedPolicyId, that.importedPolicyId) &&
                Objects.equals(entryLabel, that.entryLabel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(importedPolicyId, entryLabel);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "importedPolicyId=" + importedPolicyId +
                ", entryLabel=" + entryLabel +
                "]";
    }

}
