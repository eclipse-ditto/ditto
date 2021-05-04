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
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.assertions.AbstractJsonifiableAssert;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.assertions.WithDittoHeadersChecker;

/**
 * An abstract Assert for {@link org.eclipse.ditto.base.model.signals.commands.Command}s.
 */
public abstract class AbstractCommandAssert<S extends AbstractCommandAssert<S, C>, C extends Command>
        extends AbstractJsonifiableAssert<S, C> {

    private final WithDittoHeadersChecker withDittoHeadersChecker;

    /**
     * Constructs a new {@code AbstractCommandAssert} object.
     *
     * @param actual the command to be checked.
     * @param selfType the type of the actual Assert.
     */
    protected AbstractCommandAssert(final C actual, final Class<? extends AbstractCommandAssert> selfType) {
        super(actual, selfType);
        withDittoHeadersChecker = new WithDittoHeadersChecker(actual);
    }

    public S withType(final CharSequence expectedType) {
        return assertThatEquals(actual.getType(), String.valueOf(expectedType), "type");
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

    public S withDittoHeaders(final DittoHeaders expectedDittoHeaders) {
        return assertThatEquals(actual.getDittoHeaders(), expectedDittoHeaders, "command headers");
    }

    public S withResourcePath(final JsonPointer expectedResourcePath) {
        return assertThatEquals(actual.getResourcePath(), expectedResourcePath, "resource path");
    }

    public S withManifest(final CharSequence expectedManifest) {
        return assertThatEquals(actual.getManifest(), String.valueOf(expectedManifest), "manifest");
    }

    protected <T> S assertThatEquals(final T actual, final T expected, final String propertyName) {
        Assertions.assertThat(actual)
                .overridingErrorMessage("Expected Command to have %s\n<%s> but it had\n<%s>", propertyName, expected,
                        actual)
                .isEqualTo(expected);
        return myself;
    }

}
