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
package org.eclipse.ditto.base.model.signals.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;


/**
 * Abstract implementation of the {@link Command} interface.
 *
 * @param <T> the type of the implementing class.
 */
public abstract class AbstractCommand<T extends AbstractCommand<T>> implements Command<T> {

    private final String type;
    private final DittoHeaders dittoHeaders;

    /**
     * Constructs a new {@code AbstractCommand} object.
     *
     * @param type the name of this command.
     * @param dittoHeaders the headers of the command.
     * @param category used for validation of response required header.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws CommandHeaderInvalidException if category is
     * {@link org.eclipse.ditto.base.model.signals.commands.Command.Category#QUERY} and response is not required.
     */
    protected AbstractCommand(final String type, final DittoHeaders dittoHeaders, final Category category) {
        this.type = checkNotNull(type, "type");
        this.dittoHeaders = checkNotNull(dittoHeaders, "dittoHeaders");
        validateHeaders(category);
    }

    /**
     * Constructs a new {@code AbstractCommand} object.
     *
     * @param type the name of this command.
     * @param dittoHeaders the headers of the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws CommandHeaderInvalidException if {@link #getCategory()} is
     * {@link org.eclipse.ditto.base.model.signals.commands.Command.Category#QUERY} and response is not required.
     */
    protected AbstractCommand(final String type, final DittoHeaders dittoHeaders) {
        this.type = checkNotNull(type, "type");
        this.dittoHeaders = checkNotNull(dittoHeaders, "dittoHeaders");
        validateHeaders(getCategory());
    }

    private void validateHeaders(final Category category) {
        if (Category.QUERY == category && !dittoHeaders.isResponseRequired()) {
            final String headerKey = DittoHeaderDefinition.RESPONSE_REQUIRED.getKey();
            throw CommandHeaderInvalidException.newBuilder(headerKey)
                    .message(MessageFormat.format(
                            "Query commands must not have the header ''{0}'' set to 'false'", headerKey)
                    )
                    .description(MessageFormat.format(
                            "Set the header ''{0}'' to 'true' instead in order to receive a response to your " +
                                    "query command.", headerKey))
                    .build();
        }
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
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder()
                .set(JsonFields.TYPE, type, predicate);

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
        final AbstractCommand<?> other = (AbstractCommand<?>) obj;

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
