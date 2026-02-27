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

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;

/**
 * Represents additional {@link Subjects} and/or {@link Resources} to be merged into an imported policy entry
 * identified by its {@link Label}.
 *
 * @since 3.9.0
 */
public interface EntryAddition extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns the {@link Label} of the imported policy entry this addition applies to.
     *
     * @return the label.
     */
    Label getLabel();

    /**
     * Returns the optional additional {@link Subjects} to merge into the imported entry.
     *
     * @return the additional subjects, or empty if none.
     */
    Optional<Subjects> getSubjects();

    /**
     * Returns the optional additional {@link Resources} to merge into the imported entry.
     *
     * @return the additional resources, or empty if none.
     */
    Optional<Resources> getResources();

    /**
     * EntryAddition is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of EntryAddition.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns all non-hidden marked fields of this EntryAddition.
     *
     * @return a JSON object representation of this EntryAddition including only non-hidden marked fields.
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
