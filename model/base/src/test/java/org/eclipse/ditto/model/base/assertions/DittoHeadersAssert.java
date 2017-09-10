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
package org.eclipse.ditto.model.base.assertions;

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Specific Assert for {@link DittoHeaders} objects.
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

    public DittoHeadersAssert hasSource(final CharSequence expectedSource) {
        return assertContains(actual.getSource(), String.valueOf(expectedSource), "source");
    }

    public DittoHeadersAssert hasNoSource() {
        return assertIsEmpty(actual.getSource(), "source");
    }

    public DittoHeadersAssert hasSchemaVersion(final JsonSchemaVersion expectedSchemaVersion) {
        return assertContains(actual.getSchemaVersion(), expectedSchemaVersion, "schema version");
    }

    public DittoHeadersAssert hasNoSchemaVersion() {
        return assertIsEmpty(actual.getSchemaVersion(), "schema version");
    }

    public DittoHeadersAssert hasNoAuthorizationSubjects() {
        isNotNull();
        final List<String> actualAuthorizationSubjects = actual.getAuthorizationSubjects();
        Assertions.assertThat(actualAuthorizationSubjects)
                .overridingErrorMessage("Expected DittoHeaders not to have authorization subjects but it had <%s>",
                        actualAuthorizationSubjects)
                .isEmpty();
        return myself;
    }

    public DittoHeadersAssert hasAuthorizationSubject(final String expectedAuthorizationSubject,
            final String... furtherExpectedAuthorizationSubjects) {
        isNotNull();
        final List<String> actualAuthorizationSubjects = actual.getAuthorizationSubjects();
        Assertions.assertThat(actualAuthorizationSubjects)
                .contains(expectedAuthorizationSubject)
                .contains(furtherExpectedAuthorizationSubjects);
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

    public DittoHeadersAssert hasReadSubject(final String expectedReadSubject,
            final String... furtherExpectedReadSubjects) {
        isNotNull();
        final Set<String> actualReadSubjects = actual.getReadSubjects();
        Assertions.assertThat(actualReadSubjects)
                .contains(expectedReadSubject)
                .contains(furtherExpectedReadSubjects);
        return myself;
    }

    public DittoHeadersAssert hasNoReadSubjects() {
        isNotNull();
        final Set<String> actualReadSubjects = actual.getReadSubjects();
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

}
