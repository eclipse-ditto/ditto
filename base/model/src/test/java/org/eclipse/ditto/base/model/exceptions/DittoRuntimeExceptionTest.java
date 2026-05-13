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
package org.eclipse.ditto.base.model.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException}.
 */
public final class DittoRuntimeExceptionTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DittoRuntimeException.class)
                .withIgnoredFields("cause", "stackTrace", "suppressedExceptions")
                .withRedefinedSuperclass()
                .usingGetClass()
                .suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }

    @Test
    public void defaultBuildOf4xxExceptionHasEmptyStackTrace() {
        final DittoRuntimeException underTest = new TestDittoRuntimeException(HttpStatus.NOT_FOUND);

        assertThat(underTest.getStackTrace()).isEmpty();
        assertThat(underTest.getSuppressed()).isEmpty();
    }

    @Test
    public void defaultBuildOf5xxExceptionHasNonEmptyStackTrace() {
        final DittoRuntimeException underTest = new TestDittoRuntimeException(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(underTest.getStackTrace()).isNotEmpty();
    }

    @Test
    public void defaultBuildOf3xxExceptionHasEmptyStackTrace() {
        // Locks down the rule: any HTTP status < 500 → stackless.
        final DittoRuntimeException underTest = new TestDittoRuntimeException(HttpStatus.NOT_MODIFIED);

        assertThat(underTest.getStackTrace()).isEmpty();
    }

    @Test
    public void explicit8ArgCtorWithWritableStackTraceTrueCapturesStackEvenFor4xx() {
        final DittoRuntimeException underTest =
                new TestDittoRuntimeException(HttpStatus.NOT_FOUND, true);

        assertThat(underTest.getStackTrace()).isNotEmpty();
    }

    @Test
    public void explicit8ArgCtorWithWritableStackTraceFalseSuppressesStackEvenFor5xx() {
        final DittoRuntimeException underTest =
                new TestDittoRuntimeException(HttpStatus.INTERNAL_SERVER_ERROR, false);

        assertThat(underTest.getStackTrace()).isEmpty();
    }

    @Test
    public void addSuppressedIsNoOpForStacklessException() {
        final DittoRuntimeException underTest = new TestDittoRuntimeException(HttpStatus.BAD_REQUEST);
        underTest.addSuppressed(new RuntimeException("suppressed"));

        assertThat(underTest.getSuppressed()).isEmpty();
    }

    @Test
    public void jsonRoundTripUnaffectedByStacklessness() {
        final DittoRuntimeException original = new TestDittoRuntimeException(HttpStatus.NOT_FOUND);

        // toJson uses the wire fields only - status / errorCode / message / description / href.
        // The wire format must be identical regardless of whether the source exception is stackless.
        final org.eclipse.ditto.json.JsonObject json = original.toJson();

        assertThat(json.getValue(DittoRuntimeException.JsonFields.STATUS).get().intValue())
                .isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(json.getValue(DittoRuntimeException.JsonFields.ERROR_CODE).get())
                .isEqualTo(TestDittoRuntimeException.ERROR_CODE);
        // No stack-trace-related field in the JSON.
        assertThat(json.contains(org.eclipse.ditto.json.JsonKey.of("stackTrace"))).isFalse();
    }

    /**
     * Concrete subclass used purely for testing the {@link DittoRuntimeException} construction policy.
     */
    private static final class TestDittoRuntimeException extends DittoRuntimeException {

        private static final String ERROR_CODE = "test.dittoRuntimeException";
        private static final long serialVersionUID = 1L;

        private TestDittoRuntimeException(final HttpStatus httpStatus) {
            super(ERROR_CODE, httpStatus, DittoHeaders.empty(), "test-message", null, null, null);
        }

        private TestDittoRuntimeException(final HttpStatus httpStatus, final boolean writableStackTrace) {
            super(ERROR_CODE, httpStatus, DittoHeaders.empty(), "test-message", null, null, null,
                    writableStackTrace);
        }

        @Override
        public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
            return this;
        }
    }

}
