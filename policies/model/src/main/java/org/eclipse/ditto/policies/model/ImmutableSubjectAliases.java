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
 * An immutable implementation of {@link SubjectAliases}.
 */
@Immutable
final class ImmutableSubjectAliases implements SubjectAliases {

    private final Map<Label, SubjectAlias> aliases;

    private ImmutableSubjectAliases(final Map<Label, SubjectAlias> aliases) {
        this.aliases = Collections.unmodifiableMap(new LinkedHashMap<>(aliases));
    }

    /**
     * Returns a new {@code SubjectAliases} from the given subject aliases.
     *
     * @param aliases the subject aliases.
     * @return the new SubjectAliases.
     * @throws NullPointerException if {@code aliases} is {@code null}.
     */
    public static SubjectAliases of(final Iterable<SubjectAlias> aliases) {
        checkNotNull(aliases, "aliases");
        final Map<Label, SubjectAlias> map = new LinkedHashMap<>();
        aliases.forEach(a -> map.put(a.getLabel(), a));
        return new ImmutableSubjectAliases(map);
    }

    /**
     * Returns an empty {@code SubjectAliases}.
     *
     * @return empty SubjectAliases.
     */
    public static SubjectAliases empty() {
        return new ImmutableSubjectAliases(Collections.emptyMap());
    }

    /**
     * Creates a new {@code SubjectAliases} from the specified JSON object.
     * Each key is a label, each value is the JSON of a {@link SubjectAlias}.
     *
     * @param jsonObject the JSON object.
     * @return a new SubjectAliases.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static SubjectAliases fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");
        final Map<Label, SubjectAlias> map = new LinkedHashMap<>();
        jsonObject.forEach(field -> {
            final Label label = Label.of(field.getKey());
            final SubjectAlias alias = ImmutableSubjectAlias.fromJson(label, field.getValue().asObject());
            map.put(label, alias);
        });
        return new ImmutableSubjectAliases(map);
    }

    @Override
    public SubjectAliases setAlias(final SubjectAlias alias) {
        checkNotNull(alias, "alias");
        final Map<Label, SubjectAlias> copy = new LinkedHashMap<>(aliases);
        copy.put(alias.getLabel(), alias);
        return new ImmutableSubjectAliases(copy);
    }

    @Override
    public SubjectAliases removeAlias(final Label label) {
        checkNotNull(label, "label");
        if (!aliases.containsKey(label)) {
            return this;
        }
        final Map<Label, SubjectAlias> copy = new LinkedHashMap<>(aliases);
        copy.remove(label);
        return new ImmutableSubjectAliases(copy);
    }

    @Override
    public Optional<SubjectAlias> getAlias(final Label label) {
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
    public Stream<SubjectAlias> stream() {
        return aliases.values().stream();
    }

    @Override
    public Iterator<SubjectAlias> iterator() {
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
        final ImmutableSubjectAliases that = (ImmutableSubjectAliases) o;
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
