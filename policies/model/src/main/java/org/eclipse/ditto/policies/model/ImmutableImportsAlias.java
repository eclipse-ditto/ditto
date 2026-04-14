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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;

/**
 * An immutable implementation of {@link ImportsAlias}.
 */
@Immutable
final class ImmutableImportsAlias implements ImportsAlias {

    private final Label label;
    private final List<ImportsAliasTarget> targets;

    private ImmutableImportsAlias(final Label label, final List<ImportsAliasTarget> targets) {
        this.label = checkNotNull(label, "label");
        // Deduplicate targets while preserving order
        this.targets = Collections.unmodifiableList(
                new ArrayList<>(new LinkedHashSet<>(checkNotNull(targets, "targets"))));
    }

    /**
     * Returns a new {@code ImportsAlias} with the given parameters.
     *
     * @param label the alias label.
     * @param targets the list of targets.
     * @return the new ImportsAlias.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImportsAlias of(final Label label, final List<ImportsAliasTarget> targets) {
        return new ImmutableImportsAlias(label, targets);
    }

    /**
     * Creates a new {@code ImportsAlias} from the specified label and JSON object.
     *
     * @param label the alias label.
     * @param jsonObject the JSON object providing the data.
     * @return a new ImportsAlias.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImportsAlias fromJson(final Label label, final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");
        final JsonArray targetsArray = jsonObject.getValueOrThrow(ImportsAlias.JsonFields.TARGETS);
        final List<ImportsAliasTarget> targets = new ArrayList<>(targetsArray.getSize());
        int i = 0;
        for (final JsonValue element : targetsArray) {
            if (!element.isObject()) {
                throw JsonParseException.newBuilder()
                        .message("The element at index " + i + " of the 'targets' array in imports alias '" +
                                label + "' is not a JSON object.")
                        .build();
            }
            targets.add(ImmutableImportsAliasTarget.fromJson(element.asObject()));
            i++;
        }
        return of(label, targets);
    }

    @Override
    public Label getLabel() {
        return label;
    }

    @Override
    public List<ImportsAliasTarget> getTargets() {
        return targets;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        final JsonArray targetsArray = targets.stream()
                .map(target -> target.toJson(schemaVersion, thePredicate))
                .collect(JsonCollectors.valuesToArray());
        builder.set(ImportsAlias.JsonFields.TARGETS, targetsArray, predicate);
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
        final ImmutableImportsAlias that = (ImmutableImportsAlias) o;
        return Objects.equals(label, that.label) &&
                Objects.equals(targets, that.targets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, targets);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "label=" + label +
                ", targets=" + targets +
                "]";
    }

}
