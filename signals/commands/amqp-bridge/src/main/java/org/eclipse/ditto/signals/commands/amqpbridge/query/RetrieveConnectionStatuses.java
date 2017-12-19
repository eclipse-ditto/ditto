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
package org.eclipse.ditto.signals.commands.amqpbridge.query;

import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.model.amqpbridge.ConnectionStatus;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * Command which retrieves the {@link ConnectionStatus} from all {@link
 * AmqpConnection}s.
 */
@Immutable
public final class RetrieveConnectionStatuses extends AbstractCommand<RetrieveConnectionStatuses>
        implements AmqpBridgeQueryCommand<RetrieveConnectionStatuses> {

    /**
     * Name of this command.
     */
    public static final String NAME = "retrieveConnectionStatuses";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private RetrieveConnectionStatuses(final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code RetrieveConnection}.
     *
     * @param dittoHeaders the headers of the request.
     * @return a new RetrieveConnection command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionStatuses of(final DittoHeaders dittoHeaders) {
        return new RetrieveConnectionStatuses(dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnection} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be retrieved.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveConnectionStatuses fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnection} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveConnectionStatuses fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RetrieveConnectionStatuses>(TYPE, jsonObject).deserialize(
                () -> of(dittoHeaders));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        // nothing to append
    }

    /**
     * This command does not have an ID. Thus this implementation always returns an empty string.
     *
     * @return an empty string.
     */
    @Override
    public String getConnectionId() {
        return "";
    }

    @Override
    public RetrieveConnectionStatuses setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(dittoHeaders);
    }

}
