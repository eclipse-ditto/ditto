/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.live.base;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * An abstract base implementation for all {@link LiveCommand}s.
 *
 * @param <T> the type of the LiveCommand; currently needed as return type for {@link #setDittoHeaders(DittoHeaders)}.
 * @param <B> the type of the LiveCommandAnswerBuilder to be returned for {@link #answer()}.
 */
@ParametersAreNonnullByDefault
@Immutable
public abstract class AbstractLiveCommand<T extends LiveCommand<T, B>, B extends LiveCommandAnswerBuilder>
        implements LiveCommand<T, B> {

    private final Command<?> command;

    /**
     * Constructs a new {@code AbstractLiveCommand} object.
     *
     * @param command the command to be wrapped by the returned object.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    protected AbstractLiveCommand(final Command<?> command) {
        this.command = checkNotNull(command, "command");
    }

    @Override
    public JsonPointer getResourcePath() {
        return command.getResourcePath();
    }

    @Nonnull
    @Override
    public String getManifest() {
        return command.getManifest();
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return command.getDittoHeaders();
    }

    @Override
    public String getType() {
        return command.getType();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        return command.toJson(schemaVersion, predicate);
    }

    @Override
    public final boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractLiveCommand)) {
            return false;
        }
        final AbstractLiveCommand<?, ?> that = (AbstractLiveCommand<?, ?>) o;
        return Objects.equals(getClass(), that.getClass()) && Objects.equals(command, that.command);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(command);
    }

    @Nonnull
    @Override
    public String toString() {
        return "command=" + command;
    }

}
