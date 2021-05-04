/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

@JsonParsableCommand(typePrefix = TestCommand.TYPE_PREFIX, name = TestCommand.NAME)
public final class TestCommand extends AbstractCommand<TestCommand> {

    static final String TYPE_PREFIX = "test.command.typePrefix.";

    static final String NAME = "testCommand";

    static final String TYPE = TYPE_PREFIX + NAME;

    private TestCommand(final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

    }

    @Override
    public String getTypePrefix() {
        return "";
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public TestCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new TestCommand(dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return "";
    }

    public static TestCommand fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new TestCommand(dittoHeaders);
    }

}
