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

import java.util.Set;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;

/**
 * This Set contains the imported Policy {@link Label}s.
 *
 * @since 3.1.0
 */
public interface ImportedLabels extends Set<Label>, Jsonifiable<JsonArray> {

    /**
     * Returns a new immutable instance of {@code ImportedLabels} containing the given imported {@link Label}s.
     *
     * @param label the mandatory label to be contained in the result.
     * @param furtherLabels additional labels to be contained in the result.
     * @return the new {@code ImportedLabels}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static ImportedLabels newInstance(final CharSequence label, final CharSequence... furtherLabels) {
        return PoliciesModelFactory.newImportedEntries(label, furtherLabels);
    }

    /**
     * Returns a new immutable instance of {@code ImportedLabels} containing no {@link Label}s.
     *
     * @return the new {@code ImportedLabels}.
     */
    static ImportedLabels none() {
        return PoliciesModelFactory.noImportedEntries();
    }

    /**
     * ImportedLabels is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of ImportedLabels.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Indicates whether this set of imported {@link Label}s contains the specified {@link Label}s.
     *
     * @param label the label whose presence in this set is to be tested.
     * @param furtherLabels additional labels whose presence in this set is to be tested.
     * @return {@code true} if this set contains each specified label, {@code false} else.
     * @throws NullPointerException if any argument is {@code null}.
     */
    boolean contains(CharSequence label, CharSequence... furtherLabels);

    /**
     * Indicates whether this set of label contains the specified {@link ImportedLabels}.
     *
     * @param label the label to be contained
     * @return {@code true} if this set contains each specified label, {@code false} else.
     * @throws NullPointerException if any argument is {@code null}.
     */
    boolean contains(ImportedLabels label);

}
