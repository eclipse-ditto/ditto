/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.gateway.service.endpoints.routes.whoami;

import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * Contains information about a user.
 * @since 1.2.0
 */
public interface UserInformation extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Get the default {@link org.eclipse.ditto.policies.model.SubjectId} of the user (the first subject of
     * {@link #getSubjects()}. This authorization subject is also used as default subject when a policy is created.
     *
     * @return the default subject, or an empty {@link java.util.Optional} if none could be found.
     */
    Optional<String> getDefaultSubject();

    /**
     * Get all {@link org.eclipse.ditto.policies.model.SubjectId}s to which the user has access.
     *
     * @return the authorization subjects of the user.
     */
    List<String> getSubjects();

    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.notHidden()).get(fieldSelector);
    }

    /**
     * {@link JsonField}s of a {@link UserInformation}.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the default subject of a user.
         */
        public static final JsonFieldDefinition<String> DEFAULT_SUBJECT =
                JsonFactory.newStringFieldDefinition("defaultSubject", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing all subjects of a user.
         */
        public static final JsonFieldDefinition<JsonArray> SUBJECTS =
                JsonFactory.newJsonArrayFieldDefinition("subjects", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
