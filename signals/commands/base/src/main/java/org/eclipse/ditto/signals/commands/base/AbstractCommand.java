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
package org.eclipse.ditto.signals.commands.base;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;


/**
 * Abstract implementation of the {@link Command} interface.
 *
 * @param <T> the type of the implementing class.
 */
public abstract class AbstractCommand<T extends AbstractCommand> implements Command<T> {

    private final String type;
    private final DittoHeaders dittoHeaders;

    /**
     * Constructs a new {@code AbstractCommand} object.
     *
     * @param type the name of this command.
     * @param dittoHeaders the headers of the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected AbstractCommand(final String type, final DittoHeaders dittoHeaders) {
        this.type = requireNonNull(type, "The type must not be null!");
        this.dittoHeaders = requireNonNull(dittoHeaders, "The command headers must not be null!");
    }

    @Override
    public String getType() {
        return type;
    }

    @Nonnull
    @Override
    public String getManifest() {
        return getType();
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        // for types containing the exchange separated with ":" :
        if (type.contains(":")) {
            // backward compatibility to V1!
            jsonObjectBuilder.set(JsonFields.ID, getName(), predicate);
        }
        jsonObjectBuilder.set(JsonFields.TYPE, type, predicate);

        appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        return jsonObjectBuilder.build();
    }

    /**
     * Appends the command specific custom payload to the passed {@code jsonObjectBuilder}.
     *
     * @param jsonObjectBuilder the JsonObjectBuilder to add the custom payload to.
     * @param schemaVersion the JsonSchemaVersion used in toJson().
     * @param predicate the predicate to evaluate when adding the payload.
     */
    protected abstract void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate);

    @Override
    public int hashCode() {
        return Objects.hash(dittoHeaders, type);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final AbstractCommand other = (AbstractCommand) obj;
        return other.canEqual(this) && Objects.equals(dittoHeaders, other.dittoHeaders)
                && Objects.equals(type, other.type);
    }

    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractCommand;
    }

    @Override
    public String toString() {
        return "type=" + type + ", dittoHeaders=" + dittoHeaders;
    }

}
