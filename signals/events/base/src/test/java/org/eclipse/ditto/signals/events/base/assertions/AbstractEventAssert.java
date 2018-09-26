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
package org.eclipse.ditto.signals.events.base.assertions;

import java.time.Instant;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.assertions.AbstractJsonifiableAssert;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.assertions.WithDittoHeadersChecker;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * An abstract Assert for {@link Event}s.
 */
public abstract class AbstractEventAssert<S extends AbstractJsonifiableAssert<S, E>, E extends Event>
        extends AbstractJsonifiableAssert<S, E> {

    private final WithDittoHeadersChecker withDittoHeadersChecker;

    /**
     * Constructs a new {@code AbstractEventAssert} object.
     *
     * @param actual the event to be checked.
     * @param selfType the type of the actual assert.
     */
    protected AbstractEventAssert(final E actual, final Class<? extends AbstractEventAssert> selfType) {
        super(actual, selfType);
        withDittoHeadersChecker = new WithDittoHeadersChecker(actual);
    }

    public S hasDittoHeaders(final DittoHeaders expectedDittoHeaders) {
        isNotNull();
        withDittoHeadersChecker.hasDittoHeaders(expectedDittoHeaders);
        return myself;
    }

    public S hasCorrelationId(final CharSequence expectedCorrelationId) {
        isNotNull();
        withDittoHeadersChecker.hasCorrelationId(expectedCorrelationId);
        return myself;
    }

    public S hasId(final CharSequence expectedId) {
        isNotNull();
        final String actualId = actual.getId();
        Assertions.assertThat(actualId)
                .overridingErrorMessage("Expected Event to have ID\n<%s> but it had\n<%s>", expectedId, actualId)
                .isEqualTo(expectedId.toString());
        return myself;
    }

    public S hasEntity(final JsonValue expectedEntity) {
        isNotNull();
        final Optional<JsonValue> actualEntity = actual.getEntity();
        Assertions.assertThat(actualEntity)
                .overridingErrorMessage("Expected Event to have entity\n<%s> but it had\n<%s>", expectedEntity,
                        actualEntity.orElse(null))
                .contains(expectedEntity);
        return myself;
    }

    public S hasEntity(final JsonValue expectedEntity, final JsonSchemaVersion schemaVersion) {
        isNotNull();
        final Optional<JsonValue> actualEntity = actual.getEntity(schemaVersion);
        Assertions.assertThat(actualEntity)
                .overridingErrorMessage("Expected Event to have entity\n<%s> for schema version <%s> but it had\n<%s>",
                        expectedEntity, schemaVersion, actualEntity.orElse(null))
                .contains(expectedEntity);
        return myself;
    }

    public S hasNoEntity() {
        isNotNull();
        final Optional<JsonValue> actualEntity = actual.getEntity();
        Assertions.assertThat(actualEntity)
                .overridingErrorMessage("Expected Event to have no entity but it had\n<%s>", actualEntity.orElse(null))
                .isEmpty();
        return myself;
    }

    public S hasResourcePath(final JsonPointer expectedResourcePath) {
        isNotNull();
        final JsonPointer actualResourcePath = actual.getResourcePath();
        Assertions.assertThat((CharSequence) actualResourcePath)
                .overridingErrorMessage("Expected Event to have resource path\n<%s> but it had\n<%s>",
                        expectedResourcePath, actualResourcePath)
                .isEqualTo(expectedResourcePath);
        return myself;
    }

    public S hasManifest(final CharSequence expectedManifest) {
        isNotNull();
        final String actualManifest = actual.getManifest();
        Assertions.assertThat(actualManifest)
                .overridingErrorMessage("Expected Event to have manifest\n<%s> but it had\n<%s>", expectedManifest,
                        actualManifest)
                .isEqualTo(expectedManifest);
        return myself;
    }

    public S hasType(final CharSequence expectedType) {
        isNotNull();
        final String actualType = actual.getType();
        Assertions.assertThat(actualType)
                .overridingErrorMessage("Expected Event to have type\n<%s> but it had\n<%s>", expectedType, actualType)
                .isEqualTo(expectedType.toString());
        return myself;
    }

    public S hasName(final CharSequence expectedName) {
        isNotNull();
        final String actualName = actual.getName();
        Assertions.assertThat(actualName)
                .overridingErrorMessage("Expected Event to have name\n<%s> but it had\n<%s>", expectedName, actualName)
                .isEqualTo(expectedName.toString());
        return myself;
    }

    public S hasRevision(final long expectedRevision) {
        isNotNull();
        final long actualRevision = actual.getRevision();
        Assertions.assertThat(actualRevision)
                .overridingErrorMessage("Expected Event to have revision\n<%s> but it had\n<%s>", expectedRevision,
                        actualRevision)
                .isEqualTo(expectedRevision);
        return myself;
    }

    public S hasTimestamp(final Instant expectedTimestamp) {
        isNotNull();
        @SuppressWarnings("unchecked") final Optional<Instant> actualTimestamp = actual.getTimestamp();
        Assertions.assertThat(actualTimestamp)
                .overridingErrorMessage("Expected Event to have timestamp\n<%s> but it had\n<%s>", expectedTimestamp,
                        actualTimestamp.orElse(null))
                .contains(expectedTimestamp);
        return myself;
    }

}
