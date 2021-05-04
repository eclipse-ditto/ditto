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
package org.eclipse.ditto.internal.utils.health;

import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;

/**
 * Internal command to reset the health events of underlying systems.
 */
@Immutable
@JsonParsableCommand(typePrefix = ResetHealthEvents.TYPE_PREFIX, name = ResetHealthEvents.NAME)
public final class ResetHealthEvents extends AbstractCommand<ResetHealthEvents> {

    /**
     * Type prefix of this command.
     */
    public static final String TYPE_PREFIX = "status." + TYPE_QUALIFIER + ":";

    /**
     * Name of this command.
     */
    public static final String NAME = "resetHealthEvents";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private ResetHealthEvents(final DittoHeaders headers) {
        super(TYPE, headers);
    }

    /**
     * Returns a new {@code RetrieveHealth} instance.
     *
     * @return the new RetrieveHealth instance.
     */
    public static ResetHealthEvents newInstance() {
        return new ResetHealthEvents(DittoHeaders.empty());
    }

    /**
     * Creates a new {@code RetrieveHealth} command from the given JSON object.
     *
     * @param jsonObject the JSON object of which the Cleanup is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static ResetHealthEvents fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        // Json object is ignored -- this command has no payload.
        return new ResetHealthEvents(dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        // there is no payload.
    }

    @Override
    public String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public ResetHealthEvents setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ResetHealthEvents(dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        // no resource type
        return "";
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
