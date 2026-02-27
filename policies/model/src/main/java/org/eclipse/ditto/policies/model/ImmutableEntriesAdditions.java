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

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * An immutable implementation of {@link EntriesAdditions}.
 */
@Immutable
final class ImmutableEntriesAdditions implements EntriesAdditions {

    private final Map<Label, EntryAddition> additions;

    private ImmutableEntriesAdditions(final Map<Label, EntryAddition> additions) {
        this.additions = Collections.unmodifiableMap(new LinkedHashMap<>(additions));
    }

    /**
     * Returns a new {@code EntriesAdditions} from the given entry additions.
     *
     * @param additions the entry additions.
     * @return the new EntriesAdditions.
     * @throws NullPointerException if {@code additions} is {@code null}.
     */
    public static EntriesAdditions of(final Iterable<EntryAddition> additions) {
        checkNotNull(additions, "additions");
        final Map<Label, EntryAddition> map = new LinkedHashMap<>();
        additions.forEach(a -> map.put(a.getLabel(), a));
        return new ImmutableEntriesAdditions(map);
    }

    /**
     * Returns an empty {@code EntriesAdditions}.
     *
     * @return empty EntriesAdditions.
     */
    public static EntriesAdditions empty() {
        return new ImmutableEntriesAdditions(Collections.emptyMap());
    }

    /**
     * Creates a new {@code EntriesAdditions} from the specified JSON object.
     * Each key is a label, each value is the JSON of an {@link EntryAddition}.
     *
     * @param jsonObject the JSON object.
     * @return a new EntriesAdditions.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static EntriesAdditions fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");
        final Map<Label, EntryAddition> map = new LinkedHashMap<>();
        jsonObject.forEach(field -> {
            final Label label = Label.of(field.getKey());
            final EntryAddition addition = ImmutableEntryAddition.fromJson(label, field.getValue().asObject());
            map.put(label, addition);
        });
        return new ImmutableEntriesAdditions(map);
    }

    @Override
    public EntriesAdditions setAddition(final EntryAddition addition) {
        checkNotNull(addition, "addition");
        final Map<Label, EntryAddition> copy = new LinkedHashMap<>(additions);
        copy.put(addition.getLabel(), addition);
        return new ImmutableEntriesAdditions(copy);
    }

    @Override
    public EntriesAdditions removeAddition(final Label label) {
        checkNotNull(label, "label");
        if (!additions.containsKey(label)) {
            return this;
        }
        final Map<Label, EntryAddition> copy = new LinkedHashMap<>(additions);
        copy.remove(label);
        return new ImmutableEntriesAdditions(copy);
    }

    @Override
    public Optional<EntryAddition> getAddition(final Label label) {
        checkNotNull(label, "label");
        return Optional.ofNullable(additions.get(label));
    }

    @Override
    public int getSize() {
        return additions.size();
    }

    @Override
    public boolean isEmpty() {
        return additions.isEmpty();
    }

    @Override
    public Stream<EntryAddition> stream() {
        return additions.values().stream();
    }

    @Override
    public Iterator<EntryAddition> iterator() {
        return additions.values().iterator();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        additions.forEach((label, addition) ->
                builder.set(label.toString(), addition.toJson(schemaVersion, thePredicate)));
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
        final ImmutableEntriesAdditions that = (ImmutableEntriesAdditions) o;
        return Objects.equals(additions, that.additions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(additions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "additions=" + additions +
                "]";
    }

}
