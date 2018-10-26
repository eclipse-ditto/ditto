/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * An immutable implementation of {@link EffectedImportedEntries}.
 */
@Immutable
final class ImmutableEffectedImportedEntries implements EffectedImportedEntries {

    private final ImportedEntries includedImportedEntries;
    private final ImportedEntries excludedImportedEntries;

    private ImmutableEffectedImportedEntries(final ImportedEntries included, final ImportedEntries excluded) {
        includedImportedEntries = included;
        excludedImportedEntries = excluded;
    }

    /**
     * Returns a new {@code EffectedImportedEntries} object of the given {@code includedImportedEntries} and {@code
     * excludedImportedEntries}.
     *
     * @param includedImportedEntries the ImportedEntries which should be added as "granted".
     * @param excludedImportedEntries the ImportedEntries which should be added as "revoked".
     * @return a new {@code EffectedImportedEntries} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static EffectedImportedEntries of(final Iterable<String> includedImportedEntries,
            final Iterable<String> excludedImportedEntries) {

        final ImportedEntries included =
                toImportedEntries(toSet(checkNotNull(includedImportedEntries, "included imported entries")));
        final ImportedEntries excluded =
                toImportedEntries(toSet(checkNotNull(excludedImportedEntries, "excluded imported entries")));

        return new ImmutableEffectedImportedEntries(included, excluded);
    }

    private static Collection<String> toSet(final Iterable<String> iterable) {
        final Set<String> result;
        if (iterable instanceof Set) {
            result = (Set<String>) iterable;
        } else {
            result = new HashSet<>();
            iterable.forEach(result::add);
        }
        return result;
    }

    private static ImportedEntries toImportedEntries(final Collection<String> stringCollection) {
        return stringCollection.isEmpty()
                ? PoliciesModelFactory.noImportedEntries()
                : PoliciesModelFactory.newImportedEntries(stringCollection);
    }

    /**
     * Creates a new {@code EffectedImportedEntries} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the EffectedImportedEntries to be created.
     * @return a new EffectedImportedEntries which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if the passed in {@code jsonObject} was not in
     * the expected 'EffectedImportedEntries' format.
     */
    public static EffectedImportedEntries fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");

        final Set<String> included =
                wrapJsonRuntimeException(() -> getImportedEntriesFor(jsonObject, JsonFields.INCLUDED));
        final Set<String> excluded =
                wrapJsonRuntimeException(() -> getImportedEntriesFor(jsonObject, JsonFields.EXCLUDED));
        return of(included, excluded);
    }

    private static Set<String> getImportedEntriesFor(final JsonObject jsonObject,
            final JsonFieldDefinition<JsonArray> effect) {

        return jsonObject.getValueOrThrow(effect)
                .stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .collect(Collectors.toSet());
    }

    @Override
    public ImportedEntries getImportedEntries(final ImportedEntryEffect effect) {
        switch (checkNotNull(effect, "imported entry effect")) {
            case INCLUDED:
                return PoliciesModelFactory.newImportedEntries(includedImportedEntries);
            case EXCLUDED:
                return PoliciesModelFactory.newImportedEntries(excludedImportedEntries);
            default:
                throw new IllegalArgumentException("Permission effect <" + effect + "> is unknown!");
        }
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate)
                .set(JsonFields.INCLUDED, includedImportedEntries.toJson(), predicate)
                .set(JsonFields.EXCLUDED, excludedImportedEntries.toJson(), predicate)
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
        final ImmutableEffectedImportedEntries that = (ImmutableEffectedImportedEntries) o;
        return Objects.equals(includedImportedEntries, that.includedImportedEntries) && Objects
                .equals(excludedImportedEntries, that.excludedImportedEntries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(includedImportedEntries, excludedImportedEntries);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "includedImportedEntries=" + includedImportedEntries +
                ", excludedImportedEntries=" + excludedImportedEntries +
                "]";
    }

}
