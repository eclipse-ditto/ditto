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
package org.eclipse.ditto.base.model.signals.assertions;

import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * This class performs assertions on an {@link org.eclipse.ditto.base.model.headers.WithDittoHeaders} object. It is meant to be used as delegation
 * target to avoid redundancy in Asserts which have to check DittoHeaders.
 */
public final class WithDittoHeadersChecker {

    private final WithDittoHeaders actual;

    /**
     * Constructs a new {@code WithDittoHeadersChecker} object.
     *
     * @param actual the actual object to be checked.
     */
    public WithDittoHeadersChecker(final WithDittoHeaders actual) {
        this.actual = actual;
    }

    public void hasDittoHeaders(final DittoHeaders expectedDittoHeaders) {
        final DittoHeaders actualDittoHeaders = actual.getDittoHeaders();
        Assertions.assertThat(actualDittoHeaders)
                .overridingErrorMessage("Expected object to have command headers\n<%s> but it had\n<%s>",
                        expectedDittoHeaders, actualDittoHeaders)
                .isEqualTo(expectedDittoHeaders);
    }

    public void hasEmptyDittoHeaders() {
        final DittoHeaders actualDittoHeaders = actual.getDittoHeaders();
        Assertions.assertThat(actualDittoHeaders)
                .overridingErrorMessage("Expected object not to have command headers but it had\n<%s>",
                        actualDittoHeaders)
                .isEqualTo(DittoHeaders.empty());
    }

    public void hasSchemaVersion(final JsonSchemaVersion expectedSchemaVersion) {
        final DittoHeaders actualDittoHeaders = actual.getDittoHeaders();
        final Optional<JsonSchemaVersion> actualSchemaVersion = actualDittoHeaders.getSchemaVersion();
        if (null != expectedSchemaVersion) {
            Assertions.assertThat(actualSchemaVersion)
                    .overridingErrorMessage("Expected schema version of command headers to be \n<%s> but it was\n<%s>",
                            expectedSchemaVersion, actualSchemaVersion.orElse(null))
                    .contains(expectedSchemaVersion);
        } else {
            Assertions.assertThat(actualSchemaVersion)
                    .overridingErrorMessage("Expected command headers not to contain a schema version but they " +
                            "contained <%s>", actualSchemaVersion.orElse(null))
                    .isEmpty();
        }
    }

    public void hasCorrelationId(final CharSequence expectedCorrelationId) {
        final DittoHeaders actualDittoHeaders = actual.getDittoHeaders();
        final Optional<String> actualCorrelationId = actualDittoHeaders.getCorrelationId();
        if (null != expectedCorrelationId) {
            Assertions.assertThat(actualCorrelationId)
                    .overridingErrorMessage("Expected correlation ID of object to be \n<%s> but it was\n<%s>",
                            expectedCorrelationId, actualCorrelationId.orElse(null))
                    .contains(expectedCorrelationId.toString());
        } else {
            Assertions.assertThat(actualCorrelationId)
                    .overridingErrorMessage("Expected object not to have a correlation ID but it had <%s>",
                            actualCorrelationId.orElse(null))
                    .isEmpty();
        }
    }

    public void containsAuthorizationSubject(final String... expectedAuthSubject) {
        final DittoHeaders actualDittoHeaders = actual.getDittoHeaders();
        final List<String> actualAuthorizationSubjects = actualDittoHeaders.getAuthorizationContext()
                .getAuthorizationSubjectIds();
        Assertions.assertThat(actualAuthorizationSubjects)
                .overridingErrorMessage("Expected command headers to contain <%s> but it did not", expectedAuthSubject)
                .contains(expectedAuthSubject);
    }

}
