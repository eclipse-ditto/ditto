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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.things.model.devops.WithWotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Abstract base class for WoT validation config commands.
 *
 * @param <T> the type of the implementing class.
 * @since 3.8.0
 */
@Immutable
abstract class AbstractWotValidationConfigCommand<T extends AbstractWotValidationConfigCommand<T>>
        extends AbstractCommand<T> implements WithWotValidationConfigId {

    private final WotValidationConfigId configId;

    /**
     * Constructs a new {@code AbstractWotValidationConfigCommand} object.
     *
     * @param type the name of this command.
     * @param configId the ID of the validation config.
     * @param dittoHeaders the headers of the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected AbstractWotValidationConfigCommand(final String type, final WotValidationConfigId configId,
            final DittoHeaders dittoHeaders) {
        super(type, dittoHeaders);
        this.configId = Objects.requireNonNull(configId, "configId");
    }

    @Override
    public WotValidationConfigId getEntityId() {
        return configId;
    }

    @Override
    public String getTypePrefix() {
        return WotValidationConfigCommand.TYPE_PREFIX;
    }

    @Override
    public String getResourceType() {
        return WotValidationConfigCommand.RESOURCE_TYPE;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(WotValidationConfigCommand.JsonFields.CONFIG_ID, configId.toString(), predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), configId);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        final AbstractWotValidationConfigCommand<?> that = (AbstractWotValidationConfigCommand<?>) obj;
        return Objects.equals(configId, that.configId);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractWotValidationConfigCommand;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", configId=" + configId +
                "]";
    }
} 