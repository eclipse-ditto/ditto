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
package org.eclipse.ditto.base.model.signals.events.assertions;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

@JsonParsableEvent(name = TestEvent.NAME, typePrefix = TestEvent.TYPE_PREFIX)
@AllValuesAreNonnullByDefault
public final class TestEvent implements Event<TestEvent> {

    @SuppressWarnings("WeakerAccess") public static final String TYPE_PREFIX = "test.event.";

    public static final String NAME = "type";
    public static final String TYPE = TYPE_PREFIX + NAME;
    private final String payload;

    private TestEvent(final String payload) {
        this.payload = payload;
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return DittoHeaders.empty();
    }

    @Override
    public TestEvent setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new TestEvent(payload);
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
        return new TestEvent("test");
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Optional<Instant> getTimestamp() {
        return Optional.empty();
    }

    @Override
    public Optional<Metadata> getMetadata() {
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
