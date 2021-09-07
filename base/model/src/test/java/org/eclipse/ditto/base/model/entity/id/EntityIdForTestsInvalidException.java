/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.entity.id;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;

/**
 * An implementation of {@code EntityIdInvalidException} to be used for testing in conjunction with
 * {@link EntityIdForTests}.
 */
@JsonParsableException(errorCode = "EntityIdForTestsInvalidException")
final class EntityIdForTestsInvalidException extends EntityIdInvalidException {

    private static final long serialVersionUID = 3573397504239435104L;

    EntityIdForTestsInvalidException(@Nullable final String message, @Nullable final Throwable cause) {
        super(EntityIdForTests.ENTITY_TYPE_STRING + "id.invalid", DittoHeaders.empty(), message, null, cause, null);
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return this;
    }

    public static EntityIdForTestsInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        // Do nothing (necessary for ErrorRegistry)
        return null;
    }

}
