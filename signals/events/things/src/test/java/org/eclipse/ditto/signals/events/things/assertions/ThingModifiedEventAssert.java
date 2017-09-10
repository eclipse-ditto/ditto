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
package org.eclipse.ditto.signals.events.things.assertions;

import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.events.base.assertions.AbstractEventAssert;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * An Assert for {@link ThingModifiedEvent}s.
 */
public class ThingModifiedEventAssert extends AbstractEventAssert<ThingModifiedEventAssert, ThingModifiedEvent> {

    /**
     * Constructs a new {@code ThingEventAssert} object.
     *
     * @param actual the event to be checked.
     */
    public ThingModifiedEventAssert(final ThingModifiedEvent actual) {
        super(actual, ThingModifiedEventAssert.class);
    }

    public ThingModifiedEventAssert withType(final CharSequence expectedType) {
        return hasType(expectedType);
    }

    public ThingModifiedEventAssert hasThingId(final CharSequence expectedThingId) {
        isNotNull();
        final String actualThingId = actual.getThingId();
        Assertions.assertThat(actualThingId)
                .overridingErrorMessage("Expected ThingModifiedEvent to have Thing ID\n<%s> but it had\n<%s>",
                        expectedThingId, actualThingId)
                .isEqualTo(expectedThingId.toString());
        return this;
    }

    public ThingModifiedEventAssert withThingId(final CharSequence expectedThingId) {
        return hasThingId(expectedThingId);
    }

    public ThingModifiedEventAssert withDittoHeaders(final DittoHeaders expectedDittoHeaders) {
        return hasDittoHeaders(expectedDittoHeaders);
    }

    public ThingModifiedEventAssert withRevision(final long expectedRevision) {
        return hasRevision(expectedRevision);
    }

    public ThingModifiedEventAssert withEntity(final JsonValue expectedEntity) {
        return hasEntity(expectedEntity);
    }

    public ThingModifiedEventAssert withoutEntity() {
        return hasNoEntity();
    }

    public ThingModifiedEventAssert withResourcePath(final CharSequence expectedResourcePath) {
        return withResourcePath(expectedResourcePath, (JsonPointer) null);
    }

    public ThingModifiedEventAssert withResourcePath(final CharSequence expectedResourcePath,
            final CharSequence... furtherResourcePathSegments) {
        final JsonKey[] subLevels;
        if (null != furtherResourcePathSegments) {
            subLevels = Stream.of(furtherResourcePathSegments)
                    .map(JsonFactory::newKey)
                    .toArray(JsonKey[]::new);
        } else {
            subLevels = new JsonKey[0];
        }
        final JsonPointer expectedResourcePathPointer =
                JsonFactory.newPointer(JsonFactory.newKey(expectedResourcePath), subLevels);
        final JsonPointer actualResourcePath = actual.getResourcePath();
        Assertions.assertThat((CharSequence) actualResourcePath)
                .overridingErrorMessage("Expected ThingModifyCommandResponse to have resource path \n<%s> but it" +
                        " had\n<%s>", expectedResourcePathPointer, actualResourcePath)
                .isEqualTo(expectedResourcePathPointer);
        return myself;
    }

    public ThingModifiedEventAssert withResourcePath(final CharSequence resourcePath,
            final JsonPointer furtherResourcePathSegments) {
        isNotNull();
        final JsonPointer expectedResourcePath;
        if (null != furtherResourcePathSegments) {
            expectedResourcePath = JsonFactory.newPointer(resourcePath).append(furtherResourcePathSegments);
        } else {
            expectedResourcePath = JsonFactory.newPointer(resourcePath);
        }

        final JsonPointer actualResourcePath = actual.getResourcePath();
        Assertions.assertThat((CharSequence) actualResourcePath)
                .overridingErrorMessage("Expected ThingModifiedEvent to have resource path \n<%s> but it had\n<%s>",
                        expectedResourcePath, actualResourcePath)
                .isEqualTo(expectedResourcePath);
        return myself;
    }

}
