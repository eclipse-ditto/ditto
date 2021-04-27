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
package org.eclipse.ditto.base.api.common;

import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Placeholder class for the lack of any shutdown reason.
 */
@Immutable
final class ShutdownNoReason implements ShutdownReason {

    /**
     * The unique instance of this class.
     */
    static final ShutdownNoReason INSTANCE = new ShutdownNoReason();

    private ShutdownNoReason() {}

    @Override
    public ShutdownReasonType getType() {
        return ShutdownReasonType.Unknown.of("");
    }

    @Override
    public boolean isRelevantFor(final Object value) {
        return false;
    }

    @Override
    public JsonObject toJson() {
        return JsonFactory.newObject();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        return JsonFactory.newObject();
    }
}
