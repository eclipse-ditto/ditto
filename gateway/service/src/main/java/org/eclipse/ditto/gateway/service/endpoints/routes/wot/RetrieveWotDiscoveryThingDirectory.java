/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.gateway.service.endpoints.routes.wot;

import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.api.common.CommonCommand;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Command to retrieve the WoT Discovery "Thing Directory".
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommand(typePrefix = RetrieveWotDiscoveryThingDirectory.TYPE_PREFIX,
        name = RetrieveWotDiscoveryThingDirectory.NAME)
public final class RetrieveWotDiscoveryThingDirectory extends CommonCommand<RetrieveWotDiscoveryThingDirectory> {

    static final String TYPE_PREFIX = CommonCommand.TYPE_PREFIX;

    /**
     * The name of the command.
     */
    static final String NAME = "retrieveWotDiscoveryThingDirectory";

    /**
     * The type of the command.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveWotDiscoveryThingDirectory.NAME;

    /**
     * Constructs a new {@code RetrieveWotDiscoveryThingDirectory} object.
     *
     * @param dittoHeaders the headers of the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    private RetrieveWotDiscoveryThingDirectory(final DittoHeaders dittoHeaders) {
        super(TYPE, Category.QUERY, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        // intentionally empty as no additional information is needed.
    }

    /**
     * Create a new RetrieveWotDiscoveryThingDirectory command.
     *
     * @param headers the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code headers} is {@code null}.
     */
    public static RetrieveWotDiscoveryThingDirectory of(final DittoHeaders headers) {
        return new RetrieveWotDiscoveryThingDirectory(headers);
    }

    /**
     * Creates a new {@code RetrieveWotDiscoveryThingDirectory} from the given JSON object.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    // intentionally has {@code jsonObject} as it wouldn't be parsable as {@link JsonParsableCommand} otherwise
    public static RetrieveWotDiscoveryThingDirectory fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new RetrieveWotDiscoveryThingDirectory(dittoHeaders);
    }

    @Override
    public RetrieveWotDiscoveryThingDirectory setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new RetrieveWotDiscoveryThingDirectory(dittoHeaders);
    }

    @Override
    public boolean equals(final Object that) {
        return super.equals(that) && that instanceof RetrieveWotDiscoveryThingDirectory;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
