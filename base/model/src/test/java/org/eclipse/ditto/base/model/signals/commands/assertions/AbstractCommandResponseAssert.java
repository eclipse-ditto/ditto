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
package org.eclipse.ditto.base.model.signals.commands.assertions;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.assertions.AbstractJsonifiableAssert;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.assertions.WithDittoHeadersChecker;

/**
 * An abstract Assert for {@link org.eclipse.ditto.base.model.signals.commands.CommandResponse}s.
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

    /**
     * @since 2.0.0
     */
    public S hasStatus(final HttpStatus expectedHttpStatus) {
        return assertThatEquals(actual.getHttpStatus(), expectedHttpStatus, "HTTP status");
    }

    public S hasStatusCode(final int expectedStatusCodeValue) {
        final HttpStatus actualHttpStatus = actual.getHttpStatus();
        return assertThatEquals(actualHttpStatus.getCode(), expectedStatusCodeValue, "HTTP status code");
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
