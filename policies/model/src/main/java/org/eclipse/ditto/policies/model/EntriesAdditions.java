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

import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;

/**
 * A collection of {@link EntryAddition}s keyed by {@link Label}, defining additional subjects and/or resources
 * to merge into imported policy entries.
 *
 * @since 3.9.0
 */
public interface EntriesAdditions extends Iterable<EntryAddition>,
        Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns the {@link EntryAddition} for the given label, if present.
     *
     * @param label the label to look up.
     * @return the addition for the given label, or empty.
     * @throws NullPointerException if {@code label} is {@code null}.
     */
    Optional<EntryAddition> getAddition(Label label);

    /**
     * Returns the number of entries additions.
     *
     * @return the size.
     */
    int getSize();

    /**
     * Indicates whether this collection is empty.
     *
     * @return {@code true} if empty, {@code false} otherwise.
     */
    boolean isEmpty();

    /**
     * Returns a new {@code EntriesAdditions} with the given addition added or replaced (by label).
     *
     * @param addition the entry addition to set.
     * @return a new EntriesAdditions containing the given addition.
     * @throws NullPointerException if {@code addition} is {@code null}.
     * @since 3.9.0
     */
    EntriesAdditions setAddition(EntryAddition addition);

    /**
     * Returns a new {@code EntriesAdditions} with the addition for the given label removed.
     *
     * @param label the label of the addition to remove.
     * @return a new EntriesAdditions without the given addition.
     * @throws NullPointerException if {@code label} is {@code null}.
     * @since 3.9.0
     */
    EntriesAdditions removeAddition(Label label);

    /**
     * Returns a sequential stream of the contained {@link EntryAddition}s.
     *
     * @return a stream of entry additions.
     */
    Stream<EntryAddition> stream();

    /**
     * EntriesAdditions is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns all non-hidden marked fields of this EntriesAdditions.
     *
     * @return a JSON object representation including only non-hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.regularOrSpecial()).get(fieldSelector);
    }

}
