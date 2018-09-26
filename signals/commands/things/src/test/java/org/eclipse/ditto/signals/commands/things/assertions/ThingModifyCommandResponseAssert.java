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
package org.eclipse.ditto.signals.commands.things.assertions;

import java.util.Optional;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.assertions.AbstractCommandResponseAssert;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;

/**
 * An assert for {@link ThingModifyCommandResponse}s.
 */
public class ThingModifyCommandResponseAssert
        extends AbstractCommandResponseAssert<ThingModifyCommandResponseAssert, ThingModifyCommandResponse> {

    /**
     * Constructs a new {@code ThingModifyCommandResponseAssert} object.
     *
     * @param actual the command response to be checked.
     */
    public ThingModifyCommandResponseAssert(final ThingModifyCommandResponse actual) {
        super(actual, ThingModifyCommandResponseAssert.class);
    }

    public ThingModifyCommandResponseAssert withType(final CharSequence expectedType) {
        return hasType(expectedType);
    }

    public ThingModifyCommandResponseAssert withDittoHeaders(final DittoHeaders expectedDittoHeaders) {
        return hasDittoHeaders(expectedDittoHeaders);
    }

    public ThingModifyCommandResponseAssert withResourcePath(final CharSequence expectedResourcePath) {
        return withResourcePath(expectedResourcePath, JsonFactory.emptyPointer());
    }

    public ThingModifyCommandResponseAssert withResourcePath(final CharSequence expectedResourcePath,
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
                .overridingErrorMessage("Expected ThingModifyCommandResponse to have resource path \n<%s> but it" +
                        " had\n<%s>", expectedResourcePathPointer, actualResourcePath)
                .isEqualTo(expectedResourcePathPointer);
        return myself;
    }

    public ThingModifyCommandResponseAssert withResourcePath(final CharSequence resourcePath,
            final JsonPointer furtherResourcePathSegments) {
        isNotNull();
        final JsonPointer expectedResourcePath = JsonFactory.newPointer(resourcePath)
                .append(furtherResourcePathSegments);
        final JsonPointer actualResourcePath = actual.getResourcePath();
        Assertions.assertThat((Object) actualResourcePath)
                .overridingErrorMessage("Expected ThingModifyCommandResponse to have resource path \n<%s> but it" +
                        " had\n<%s>", expectedResourcePath, actualResourcePath)
                .isEqualTo(expectedResourcePath);
        return myself;
    }

    public ThingModifyCommandResponseAssert withStatus(final HttpStatusCode expectedStatus) {
        return hasStatus(expectedStatus);
    }

    public ThingModifyCommandResponseAssert withEntity(final JsonValue expectedEntity) {
        isNotNull();
        final Optional<JsonValue> actualEntity = actual.getEntity();
        Assertions.assertThat(actualEntity)
                .overridingErrorMessage("Expected ThingModifyCommandResponse to have entity\n<%s> but it had\n<%s>",
                        expectedEntity, actualEntity.orElse(null))
                .contains(expectedEntity);
        return myself;
    }

    public ThingModifyCommandResponseAssert withoutEntity() {
        isNotNull();
        final Optional<JsonValue> actualEntity = actual.getEntity();
        Assertions.assertThat(actualEntity)
                .overridingErrorMessage("Expected ThingModifyCommandResponse not to have an entity but it had\n<%s>",
                        actualEntity.orElse(null))
                .isEmpty();
        return myself;
    }

}
