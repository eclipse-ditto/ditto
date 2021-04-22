/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.signals.commands.assertions;

import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.assertions.AbstractCommandResponseAssert;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;

/**
 * An assert for {@link org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse}s.
 */
public class ThingQueryCommandResponseAssert
        extends AbstractCommandResponseAssert<ThingQueryCommandResponseAssert, ThingQueryCommandResponse<?>> {

    /**
     * Constructs a new {@code ThingQueryCommandResponseAssert} object.
     *
     * @param actual the command response to be checked.
     */
    public ThingQueryCommandResponseAssert(final ThingQueryCommandResponse actual) {
        super(actual, ThingQueryCommandResponseAssert.class);
    }

    public ThingQueryCommandResponseAssert withType(final CharSequence expectedType) {
        return hasType(expectedType);
    }

    public ThingQueryCommandResponseAssert withDittoHeaders(final DittoHeaders expectedDittoHeaders) {
        return hasDittoHeaders(expectedDittoHeaders);
    }

    public ThingQueryCommandResponseAssert withResourcePath(final CharSequence expectedResourcePath) {
        return withResourcePath(expectedResourcePath, JsonFactory.emptyPointer());
    }

    public ThingQueryCommandResponseAssert withResourcePath(final CharSequence expectedResourcePath,
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
        Assertions.assertThat((Object) actualResourcePath)
                .overridingErrorMessage("Expected ThingQueryCommandResponse to have resource path \n<%s> but it" +
                        " had\n<%s>", expectedResourcePathPointer, actualResourcePath)
                .isEqualTo(expectedResourcePathPointer);
        return myself;
    }

    public ThingQueryCommandResponseAssert withResourcePath(final CharSequence resourcePath,
            final JsonPointer furtherResourcePathSegments) {
        isNotNull();
        final JsonPointer expectedResourcePath = JsonFactory.newPointer(resourcePath)
                .append(furtherResourcePathSegments);
        final JsonPointer actualResourcePath = actual.getResourcePath();
        Assertions.assertThat((Object) actualResourcePath)
                .overridingErrorMessage("Expected ThingQueryCommandResponse to have resource path \n<%s> but it" +
                        " had\n<%s>", expectedResourcePath, actualResourcePath)
                .isEqualTo(expectedResourcePath);
        return myself;
    }

    public ThingQueryCommandResponseAssert withStatus(final HttpStatus expectedStatus) {
        return hasStatus(expectedStatus);
    }

    public ThingQueryCommandResponseAssert withEntity(final JsonValue expectedEntity) {
        isNotNull();
        final JsonValue actualEntity = actual.getEntity();
        Assertions.assertThat(actualEntity)
                .overridingErrorMessage("Expected ThingQueryCommandResponse to have entity\n<%s> but it had\n<%s>",
                        expectedEntity, actualEntity)
                .isEqualTo(expectedEntity);
        return myself;
    }

}
