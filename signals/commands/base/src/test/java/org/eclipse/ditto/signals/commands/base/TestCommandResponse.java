
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
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

@JsonParsableCommandResponse(type = TestCommandResponse.TYPE)
public final class TestCommandResponse extends  AbstractCommandResponse<TestCommandResponse> {

    static final String TYPE = "test.commandresponse.type";

    private TestCommandResponse(final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK ,dittoHeaders);
    }

    @Override
    public TestCommandResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new TestCommandResponse(dittoHeaders);
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

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
    }

    public static TestCommandResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new TestCommandResponse(dittoHeaders);
    }
}
