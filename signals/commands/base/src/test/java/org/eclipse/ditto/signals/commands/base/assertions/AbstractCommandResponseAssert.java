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
package org.eclipse.ditto.signals.commands.base.assertions;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.assertions.AbstractJsonifiableAssert;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.assertions.WithDittoHeadersChecker;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

/**
 * An abstract Assert for {@link CommandResponse}s.
 */
public abstract class AbstractCommandResponseAssert<S extends AbstractCommandResponseAssert<S, R>,
        R extends CommandResponse> extends AbstractJsonifiableAssert<S, R> {

    private final WithDittoHeadersChecker withDittoHeadersChecker;

    /**
     * Constructs a new {@code AbstractCommandResponseAssert} object.
     *
     * @param actual the command response to be checked.
     * @param selfType the type of the actual Assert.
     */
    protected AbstractCommandResponseAssert(final R actual,
            final Class<? extends AbstractCommandResponseAssert> selfType) {

        super(actual, selfType);
        withDittoHeadersChecker = new WithDittoHeadersChecker(actual);
    }

    public S hasType(final CharSequence expectedType) {
        isNotNull();
        final String actualType = actual.getType();
        Assertions.assertThat(actualType)
                .overridingErrorMessage("Expected CommandResponse to have type\n<%s> but it had\n<%s>", expectedType,
                        actualType)
                .isEqualTo(expectedType.toString());
        return myself;
    }

    public S hasName(final CharSequence expectedName) {
        isNotNull();
        final String actualName = actual.getName();
        Assertions.assertThat(actualName)
                .overridingErrorMessage("Expected CommandResponse to have name\n<%s> but it had\n<%s>", expectedName,
                        actualName)
                .isEqualTo(expectedName.toString());
        return myself;
    }

    public S hasDittoHeaders(final DittoHeaders expectedDittoHeaders) {
        isNotNull();
        withDittoHeadersChecker.hasDittoHeaders(expectedDittoHeaders);
        return myself;
    }

    public S hasEmptyDittoHeaders() {
        isNotNull();
        withDittoHeadersChecker.hasEmptyDittoHeaders();
        return myself;
    }

    public S hasCorrelationId(final CharSequence expectedCorrelationId) {
        isNotNull();
        withDittoHeadersChecker.hasCorrelationId(expectedCorrelationId);
        return myself;
    }

    public S hasJsonSchemaVersion(final JsonSchemaVersion expectedJsonSchemaVersion) {
        isNotNull();
        withDittoHeadersChecker.hasSchemaVersion(expectedJsonSchemaVersion);
        return myself;
    }

    public S containsAuthorizationSubject(final String... expectedAuthSubject) {
        isNotNull();
        withDittoHeadersChecker.containsAuthorizationSubject(expectedAuthSubject);
        return myself;
    }

    public S hasStatus(final HttpStatusCode expectedStatusCode) {
        return assertThatEquals(actual.getStatusCode(), expectedStatusCode, "HTTP status");
    }

    public S hasStatusCode(final int expectedStatusCodeValue) {
        return assertThatEquals(actual.getStatusCodeValue(), expectedStatusCodeValue, "HTTP status code");
    }

    private <T> S assertThatEquals(final T actual, final T expected, final String propertyName) {
        Assertions.assertThat(actual)
                .overridingErrorMessage("Expected CommandResponse to have %s \n<%s> but it had\n<%s>",
                        propertyName, expected, actual)
                .isEqualTo(expected);
        return myself;
    }

    public S hasResourcePath(final JsonPointer expectedResourcePath) {
        isNotNull();
        final JsonPointer actualResourcePath = actual.getResourcePath();
        Assertions.assertThat((Object) actualResourcePath)
                .overridingErrorMessage("Expected CommandResponse to have resource path\n<%s> but it had\n<%s>",
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

}
