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
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * An immutable implementation of {@link ImportsAliases}.
 */
@Immutable
final class ImmutableImportsAliases implements ImportsAliases {

    private final Map<Label, ImportsAlias> aliases;

    private ImmutableImportsAliases(final Map<Label, ImportsAlias> aliases) {
        this.aliases = Collections.unmodifiableMap(new LinkedHashMap<>(aliases));
    }

    /**
     * Returns a new {@code ImportsAliases} from the given imports aliases.
     *
     * @param aliases the imports aliases.
     * @return the new ImportsAliases.
     * @throws NullPointerException if {@code aliases} is {@code null}.
     */
    public static ImportsAliases of(final Iterable<ImportsAlias> aliases) {
        checkNotNull(aliases, "aliases");
        final Map<Label, ImportsAlias> map = new LinkedHashMap<>();
        aliases.forEach(a -> map.put(a.getLabel(), a));
        return new ImmutableImportsAliases(map);
    }

    /**
     * Returns an empty {@code ImportsAliases}.
     *
     * @return empty ImportsAliases.
     */
    public static ImportsAliases empty() {
        return new ImmutableImportsAliases(Collections.emptyMap());
    }

    /**
     * Creates a new {@code ImportsAliases} from the specified JSON object.
     * Each key is a label, each value is the JSON of a {@link ImportsAlias}.
     *
     * @param jsonObject the JSON object.
     * @return a new ImportsAliases.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static ImportsAliases fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");
        final Map<Label, ImportsAlias> map = new LinkedHashMap<>();
        jsonObject.forEach(field -> {
            final Label label = Label.of(field.getKey());
            final ImportsAlias alias = ImmutableImportsAlias.fromJson(label, field.getValue().asObject());
            map.put(label, alias);
        });
        return new ImmutableImportsAliases(map);
    }

    @Override
    public ImportsAliases setAlias(final ImportsAlias alias) {
        checkNotNull(alias, "alias");
        final Map<Label, ImportsAlias> copy = new LinkedHashMap<>(aliases);
        copy.put(alias.getLabel(), alias);
        return new ImmutableImportsAliases(copy);
    }

    @Override
    public ImportsAliases removeAlias(final Label label) {
        checkNotNull(label, "label");
        if (!aliases.containsKey(label)) {
            return this;
        }
        final Map<Label, ImportsAlias> copy = new LinkedHashMap<>(aliases);
        copy.remove(label);
        return new ImmutableImportsAliases(copy);
    }

    @Override
    public Optional<ImportsAlias> getAlias(final Label label) {
        checkNotNull(label, "label");
        return Optional.ofNullable(aliases.get(label));
    }

    @Override
    public int getSize() {
        return aliases.size();
    }

    @Override
    public boolean isEmpty() {
        return aliases.isEmpty();
    }

    @Override
    public Set<Label> getLabels() {
        return aliases.keySet();
    }

    @Override
    public Stream<ImportsAlias> stream() {
        return aliases.values().stream();
    }

    @Override
    public Iterator<ImportsAlias> iterator() {
        return aliases.values().iterator();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        aliases.forEach((label, alias) ->
                builder.set(label.toString(), alias.toJson(schemaVersion, thePredicate)));
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
        final ImmutableImportsAliases that = (ImmutableImportsAliases) o;
        return Objects.equals(aliases, that.aliases);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aliases);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "aliases=" + aliases +
                "]";
    }

}
