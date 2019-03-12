/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.base;

import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

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
    public String getId() {
        return "";
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
