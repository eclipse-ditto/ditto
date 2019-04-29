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
package org.eclipse.ditto.signals.events.base.assertions;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableEvent;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.events.base.Event;

@JsonParsableEvent(name = TestEvent.NAME, typePrefix= TestEvent.TYPE_PREFIX)
public final class TestEvent implements Event<TestEvent> {

    @SuppressWarnings("WeakerAccess") public static final String TYPE_PREFIX = "test.event.";

    public static final String NAME = "type";
    public static final String TYPE = TYPE_PREFIX + NAME;
    private final String id;

    private TestEvent(final String id) {
        this.id = id;
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return DittoHeaders.empty();
    }

    @Override
    public TestEvent setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new TestEvent(id);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return "";
    }

    public static TestEvent fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new TestEvent("0");
    }

    public static TestEvent alternativeFromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new TestEvent("1");
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public long getRevision() {
        return 0;
    }

    @Override
    public TestEvent setRevision(final long revision) {
        return new TestEvent(id);
    }

    @Override
    public Optional<Instant> getTimestamp() {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public String getManifest() {
        return "";
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        return JsonObject.empty();
    }
}
