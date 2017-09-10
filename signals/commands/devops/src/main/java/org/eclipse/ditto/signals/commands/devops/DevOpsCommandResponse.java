/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.devops;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.WithManifest;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Base interface for all devops command responses.
 *
 * @param <T> the type of the implementing class.
 */
public interface DevOpsCommandResponse<T extends DevOpsCommandResponse> extends
        Jsonifiable.WithPredicate<JsonObject, JsonField>, WithDittoHeaders<T>, WithManifest {

    /**
     * Type Prefix of DevOps commands.
     */
    String TYPE_PREFIX = "things.devops.responses:";

    /**
     * Returns the type of this response.
     *
     * @return the type of this response.
     */
    String getType();

    /**
     * Returns the name of the response. This is gathered by the type of the response in the default implementation.
     *
     * @return the response name.
     */
    default String getName() {
        return getType().contains(":") ? getType().split(":")[1] : getType();
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * Returns the status code of the issued {@link DevOpsCommand}. The semantics of the codes is the one of HTTP Status
     * Codes (e.g.: {@literal 200} for "OK", {@literal 409} for "Conflict").
     *
     * @return the status code of the issued DevOpsCommand.
     */
    HttpStatusCode getStatusCode();

    /**
     * This convenience method returns the status code value of the issued {@link DevOpsCommand}. The semantics of the
     * codes is the one of HTTP Status Codes (e.g.: {@literal 200} for "OK", {@literal 409} for "Conflict").
     *
     * @return the status code value of the issued CommandType.
     * @see #getStatusCode()
     */
    default int getStatusCodeValue() {
        final HttpStatusCode statusCode = getStatusCode();
        return statusCode.toInt();
    }

    @Override
    default JsonSchemaVersion getImplementedSchemaVersion() {
        return getDittoHeaders().getSchemaVersion().orElse(getLatestSchemaVersion());
    }

    /**
     * Returns all non hidden marked fields of this devops command response.
     *
     * @return a JSON object representation of this devops command response including only regular, non-hidden marked
     * fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    /**
     * An enumeration of the known {@link JsonField}s of a {@code DevOpsCommand}.
     */
    final class JsonFields {

        /**
         * JSON field containing the response type.
         */
        public static final JsonFieldDefinition TYPE =
                JsonFactory.newFieldDefinition("type", String.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the message's status code.
         */
        public static final JsonFieldDefinition STATUS =
                JsonFactory.newFieldDefinition("status", int.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
