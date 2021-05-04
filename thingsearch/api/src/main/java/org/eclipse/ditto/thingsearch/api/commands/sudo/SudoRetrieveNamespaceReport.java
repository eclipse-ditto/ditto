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
package org.eclipse.ditto.thingsearch.api.commands.sudo;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Command which retrieves a Namespace-Report (without authorization).
 */
@Immutable
@AllValuesAreNonnullByDefault
@JsonParsableCommand(typePrefix = ThingSearchSudoCommand.TYPE_PREFIX, name = SudoRetrieveNamespaceReport.NAME)
public final class SudoRetrieveNamespaceReport extends AbstractCommand<SudoRetrieveNamespaceReport>
        implements ThingSearchSudoCommand<SudoRetrieveNamespaceReport> {

    /**
     * Name of the command.
     */
    public static final String NAME = "sudoRetrieveNamespaceReport";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private SudoRetrieveNamespaceReport(final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveNamespaceReport}.
     *
     * @param dittoHeaders the command headers of the request.
     * @return a command for retrieving a namespace report without authorization.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoRetrieveNamespaceReport of(final DittoHeaders dittoHeaders) {
        return new SudoRetrieveNamespaceReport(dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveNamespaceReport} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static SudoRetrieveNamespaceReport fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveNamespaceReport} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SudoRetrieveNamespaceReport fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<SudoRetrieveNamespaceReport>(TYPE, jsonObject)
                .deserialize(() -> of(dittoHeaders));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        // nothing to do
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public SudoRetrieveNamespaceReport setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(dittoHeaders);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return super.equals(o);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoRetrieveNamespaceReport;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
