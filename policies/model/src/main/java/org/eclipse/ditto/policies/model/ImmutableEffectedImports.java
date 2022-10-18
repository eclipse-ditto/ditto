/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import static org.eclipse.ditto.base.model.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * An immutable implementation of {@link EffectedImports}.
 */
@Immutable
final class ImmutableEffectedImports implements EffectedImports {

    private final ImportedLabels importedLabels;

    private ImmutableEffectedImports(final ImportedLabels importedLabels) {
        this.importedLabels = importedLabels;
    }

    /**
     * Returns a new {@code EffectedImports} object of the given {@code importedLabels}.
     *
     * @param labels the labels of the policy entries which should be added from the imported policy.
     * @return a new {@code EffectedImports} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static EffectedImports of(final Iterable<Label> labels) {

        final ImportedLabels importedLabels =
                toImportedEntries(toSet(checkNotNull(labels, "importedLabels")));

        return new ImmutableEffectedImports(importedLabels);
    }

    private static Collection<Label> toSet(final Iterable<Label> iterable) {
        final Set<Label> result;
        if (iterable instanceof Set) {
            result = (Set<Label>) iterable;
        } else {
            result = new LinkedHashSet<>();
            iterable.forEach(result::add);
        }
        return result;
    }

    private static ImportedLabels toImportedEntries(final Collection<Label> labels) {
        return labels.isEmpty()
                ? PoliciesModelFactory.noImportedEntries()
                : PoliciesModelFactory.newImportedEntries(labels);
    }

    /**
     * Creates a new {@code EffectedImports} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the EffectedImports to be created.
     * @return a new EffectedImports which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if the passed in {@code jsonObject} was not in
     * the expected 'EffectedImports' format.
     */
    public static EffectedImports fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");
        final Set<Label> importedLabels = wrapJsonRuntimeException(() -> getImportedEntries(jsonObject));
        return of(importedLabels);
    }

    private static Set<Label> getImportedEntries(final JsonObject jsonObject) {

        return jsonObject.getValue(JsonFields.ENTRIES)
                .orElse(JsonArray.empty())
                .stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(Label::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public ImportedLabels getImportedLabels() {
        return PoliciesModelFactory.newImportedEntries(importedLabels);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.ENTRIES, importedLabels.toJson(), predicate)
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
        final ImmutableEffectedImports that = (ImmutableEffectedImports) o;
        return Objects.equals(importedLabels, that.importedLabels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(importedLabels);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "importedLabels=" + importedLabels +
                "]";
    }

}
