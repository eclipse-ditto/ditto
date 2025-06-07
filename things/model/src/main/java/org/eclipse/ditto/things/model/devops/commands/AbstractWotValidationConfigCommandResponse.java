/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.devops.commands;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Abstract base class for WoT validation config command responses.
 *
 * @param <T> the type of the implementing class.
 *
 * @since 3.8.0
 */
@Immutable
abstract class AbstractWotValidationConfigCommandResponse<T extends AbstractWotValidationConfigCommandResponse<T>>
        extends AbstractCommandResponse<T> implements WotValidationConfigCommandResponse<T> {

    protected final WotValidationConfigId configId;

    /**
     * Constructs a new {@code AbstractWotValidationConfigCommandResponse} object.
     *
     * @param type the type of this command response.
     * @param status the HTTP status of this command response.
     * @param configId the ID of the WoT validation config.
     * @param dittoHeaders the headers of the command which caused this response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected AbstractWotValidationConfigCommandResponse(final String type,
            final HttpStatus status,
            final WotValidationConfigId configId,
            final DittoHeaders dittoHeaders) {
        super(type, status, dittoHeaders);
        this.configId = Objects.requireNonNull(configId, "configId");
    }

    /**
     * Returns the ID of the WoT validation config.
     *
     * @return the config ID.
     */
    public WotValidationConfigId getConfigId() {
        return configId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(WotValidationConfigCommand.JsonFields.CONFIG_ID, configId.toString(), predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), configId);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final AbstractWotValidationConfigCommandResponse<?> that = (AbstractWotValidationConfigCommandResponse<?>) o;
        return Objects.equals(configId, that.configId);
    }

    @Override
    public String toString() {
        return super.toString() + ", configId=" + configId;
    }
} 