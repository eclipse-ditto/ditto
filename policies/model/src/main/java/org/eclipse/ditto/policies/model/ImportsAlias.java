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

import java.util.List;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;

/**
 * A imports alias maps a {@link Label} to one or more {@link ImportsAliasTarget}s. Subject operations on the alias
 * label transparently fan out to all referenced entries additions targets.
 *
 * @since 3.9.0
 */
public interface ImportsAlias extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns the {@link Label} of this alias.
     *
     * @return the alias label.
     */
    Label getLabel();

    /**
     * Returns the list of {@link ImportsAliasTarget}s this alias fans out to.
     *
     * @return the unmodifiable list of targets.
     */
    List<ImportsAliasTarget> getTargets();

    /**
     * ImportsAlias is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns all non-hidden marked fields of this ImportsAlias.
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

    /**
     * Known JSON fields of an ImportsAlias.
     */
    final class JsonFields {

        /**
         * JSON field containing the targets array.
         */
        public static final JsonFieldDefinition<JsonArray> TARGETS =
                JsonFactory.newJsonArrayFieldDefinition("targets", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }

}
