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
 * A collection of {@link SubjectAlias}es keyed by {@link Label}. Subject aliases map a label to one or more
 * entries additions targets, enabling transparent subject fan-out through the entries API.
 *
 * @since 3.9.0
 */
public interface SubjectAliases extends Iterable<SubjectAlias>,
        Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns an empty {@code SubjectAliases} instance.
     *
     * @return the empty instance.
     */
    static SubjectAliases emptyInstance() {
        return ImmutableSubjectAliases.empty();
    }

    /**
     * Returns the {@link SubjectAlias} for the given label, if present.
     *
     * @param label the label to look up.
     * @return the alias for the given label, or empty.
     * @throws NullPointerException if {@code label} is {@code null}.
     */
    Optional<SubjectAlias> getAlias(Label label);

    /**
     * Returns the number of subject aliases.
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
     * Returns a new {@code SubjectAliases} with the given alias added or replaced (by label).
     *
     * @param alias the subject alias to set.
     * @return a new SubjectAliases containing the given alias.
     * @throws NullPointerException if {@code alias} is {@code null}.
     */
    SubjectAliases setAlias(SubjectAlias alias);

    /**
     * Returns a new {@code SubjectAliases} with the alias for the given label removed.
     *
     * @param label the label of the alias to remove.
     * @return a new SubjectAliases without the given alias.
     * @throws NullPointerException if {@code label} is {@code null}.
     */
    SubjectAliases removeAlias(Label label);

    /**
     * Returns the set of labels used by these aliases.
     *
     * @return the set of alias labels.
     */
    Set<Label> getLabels();

    /**
     * Returns a sequential stream of the contained {@link SubjectAlias}es.
     *
     * @return a stream of subject aliases.
     */
    Stream<SubjectAlias> stream();

    /**
     * SubjectAliases is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns all non-hidden marked fields of this SubjectAliases.
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
