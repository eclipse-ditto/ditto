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
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;

/**
 * A collection of {@link ImportsAlias}es keyed by {@link Label}. Imports aliases map a label to one or more
 * entries additions targets, enabling transparent subject fan-out through the entries API.
 *
 * @since 3.9.0
 */
public interface ImportsAliases extends Iterable<ImportsAlias>,
        Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns an empty {@code ImportsAliases} instance.
     *
     * @return the empty instance.
     */
    static ImportsAliases emptyInstance() {
        return ImmutableImportsAliases.empty();
    }

    /**
     * Returns the {@link ImportsAlias} for the given label, if present.
     *
     * @param label the label to look up.
     * @return the alias for the given label, or empty.
     * @throws NullPointerException if {@code label} is {@code null}.
     */
    Optional<ImportsAlias> getAlias(Label label);

    /**
     * Returns the number of imports aliases.
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
     * Returns a new {@code ImportsAliases} with the given alias added or replaced (by label).
     *
     * @param alias the imports alias to set.
     * @return a new ImportsAliases containing the given alias.
     * @throws NullPointerException if {@code alias} is {@code null}.
     */
    ImportsAliases setAlias(ImportsAlias alias);

    /**
     * Returns a new {@code ImportsAliases} with the alias for the given label removed.
     *
     * @param label the label of the alias to remove.
     * @return a new ImportsAliases without the given alias.
     * @throws NullPointerException if {@code label} is {@code null}.
     */
    ImportsAliases removeAlias(Label label);

    /**
     * Returns the set of labels used by these aliases.
     *
     * @return the set of alias labels.
     */
    Set<Label> getLabels();

    /**
     * Returns a sequential stream of the contained {@link ImportsAlias}es.
     *
     * @return a stream of imports aliases.
     */
    Stream<ImportsAlias> stream();

    /**
     * ImportsAliases is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns all non-hidden marked fields of this ImportsAliases.
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
