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
package org.eclipse.ditto.base.model.assertions;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Specific Assert for {@link org.eclipse.ditto.base.model.headers.DittoHeaders} objects.
 */
public final class DittoHeadersAssert extends AbstractJsonifiableAssert<DittoHeadersAssert, DittoHeaders> {

    /**
     * Constructs a new {@code DittoHeadersAssert} object.
     *
     * @param actual the DittoHeaders to be checked.
     */
    public DittoHeadersAssert(final DittoHeaders actual) {
        super(actual, DittoHeadersAssert.class);
    }

    public DittoHeadersAssert hasCorrelationId(final CharSequence expectedCorrelationId) {
        return assertContains(actual.getCorrelationId(), String.valueOf(expectedCorrelationId), "correlation-id");
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private <T> DittoHeadersAssert assertContains(final Optional<T> actual, final T expected, final String
            entityName) {
        final String errorMessage =
                MessageFormat.format("Expected {0} of DittoHeaders to be \n<%s> but it was\n<%s>", entityName);
        Assertions.assertThat(actual)
                .overridingErrorMessage(errorMessage, expected, actual.orElse(null))
                .contains(expected);
        return myself;
    }

    public DittoHeadersAssert hasCorrelationId() {
        isNotNull();
        final Optional<String> actualCorrelationId = actual.getCorrelationId();
        Assertions.assertThat(actualCorrelationId)
                .overridingErrorMessage("Expected DittoHeaders to have a correlation-id but it had not")
                .isPresent();
        return myself;
    }

    public DittoHeadersAssert hasNoCorrelationId() {
        return assertIsEmpty(actual.getCorrelationId(), "correlation-id");
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private <T> DittoHeadersAssert assertIsEmpty(final Optional<T> actual, final String entityName) {
        final String errorMessage = MessageFormat.format("Expected DittoHeaders not to have a {0} but it had <%s>",
                entityName);
        Assertions.assertThat(actual)
                .overridingErrorMessage(errorMessage, actual.orElse(null))
                .isEmpty();
        return myself;
    }

    public DittoHeadersAssert hasSchemaVersion(final JsonSchemaVersion expectedSchemaVersion) {
        return assertContains(actual.getSchemaVersion(), expectedSchemaVersion, "schema version");
    }

    public DittoHeadersAssert hasNoSchemaVersion() {
        return assertIsEmpty(actual.getSchemaVersion(), "schema version");
    }

    public DittoHeadersAssert hasNoAuthorizationSubjects() {
        isNotNull();
        final AuthorizationContext authorizationContext = actual.getAuthorizationContext();
        Assertions.assertThat(authorizationContext)
                .overridingErrorMessage("Expected DittoHeaders not to have authorization subjects but it had <%s>",
                        authorizationContext)
                .isEmpty();
        return myself;
    }

    public DittoHeadersAssert hasAuthorizationContext(final AuthorizationContext expectedAuthorizationContext) {
        isNotNull();
        final AuthorizationContext actualAuthorizationContext = actual.getAuthorizationContext();
        Assertions.assertThat(actualAuthorizationContext)
                .overridingErrorMessage("Expected AuthorizationContext of DittoHeaders to be\n<%s> but it " +
                        "was\n<%s>", expectedAuthorizationContext, actualAuthorizationContext)
                .isEqualTo(expectedAuthorizationContext);
        return myself;
    }

    public DittoHeadersAssert hasReadGrantedSubject(final AuthorizationSubject expectedReadSubject,
            final AuthorizationSubject... furtherExpectedReadSubjects) {

        isNotNull();
        final Set<AuthorizationSubject> actualReadSubjects = actual.getReadGrantedSubjects();
        Assertions.assertThat(actualReadSubjects)
                .contains(expectedReadSubject)
                .contains(furtherExpectedReadSubjects);
        return myself;
    }

    public DittoHeadersAssert hasNoReadGrantedSubjects() {
        isNotNull();
        final Set<AuthorizationSubject> actualReadSubjects = actual.getReadGrantedSubjects();
        Assertions.assertThat(actualReadSubjects)
                .overridingErrorMessage("Expected DittoHeaders not to have read subjects but it had <%s>",
                        actualReadSubjects)
                .isEmpty();
        return myself;
    }

    public DittoHeadersAssert hasIsResponseRequired(final boolean expected) {
        return assertContains(Optional.of(actual.isResponseRequired()), expected,
                "flag indicating if a response is required");
    }

    public DittoHeadersAssert hasAllowPolicyLockout(final boolean expected) {
        return assertContains(Optional.of(actual.isAllowPolicyLockout()), expected,
                "flag indicating if policy lockout is allowed");
    }

}
