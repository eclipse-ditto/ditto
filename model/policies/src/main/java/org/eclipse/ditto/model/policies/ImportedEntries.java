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

import java.util.Set;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * This Set is ...
 */
public interface ImportedEntries extends Set<String>, Jsonifiable<JsonArray> {

    /**
     * Returns a new immutable instance of {@code ImportedEntries} containing the given permissions.
     *
     * @param entryLabel the mandatory entryLabel to be contained in the result.
     * @param furtherEntryLabels additional permissions to be contained in the result.
     * @return the new {@code ImportedEntries}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static ImportedEntries newInstance(final String entryLabel, final String... furtherEntryLabels) {
        return PoliciesModelFactory.newImportedEntries(entryLabel, furtherEntryLabels);
    }

    /**
     * Returns a new immutable instance of {@code ImportedEntries} containing no permissions.
     *
     * @return the new {@code ImportedEntries}.
     */
    static ImportedEntries none() {
        return PoliciesModelFactory.noImportedEntries();
    }

    /**
     * ImportedEntries is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of ImportedEntries.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Indicates whether this set of permissions contains the specified ...
     *
     * @param entryLabel the entryLabel whose presence in this set is to be tested.
     * @param furtherEntryLabels additional ... whose presence in this set is to be tested.
     * @return {@code true} if this set contains each specified entryLabel, {@code false} else.
     * @throws NullPointerException if any argument is {@code null}.
     */
    boolean contains(String entryLabel, String... furtherEntryLabels);

    /**
     * Indicates whether this set of importedEntries contains the specified ...
     *
     * @param importedEntries the importedEntries to be contained
     * @return {@code true} if this set contains each specified ..., {@code false} else.
     * @throws NullPointerException if any argument is {@code null}.
     */
    boolean contains(ImportedEntries importedEntries);

}
