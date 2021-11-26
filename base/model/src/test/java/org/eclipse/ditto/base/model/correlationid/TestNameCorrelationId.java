/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.correlationid;

import java.text.MessageFormat;
import java.util.UUID;

import javax.annotation.concurrent.NotThreadSafe;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * This {@link TestWatcher} can be used as {@link org.junit.Rule} to get a {@link CorrelationId} that consists of the
 * qualified name of the current test method.
 */
@NotThreadSafe
public final class TestNameCorrelationId extends TestWatcher {

    private static final String CORRELATION_ID_PATTERN = "{0}.{1}-{2}";

    private CorrelationId correlationId;

    private TestNameCorrelationId() {
        correlationId = null;
    }

    /**
     * Returns a new instance of {@code TestNameCorrelationId}.
     *
     * @return the instance.
     */
    public static TestNameCorrelationId newInstance() {
        return new TestNameCorrelationId();
    }

    @Override
    protected void starting(final Description description) {
        final Class<?> testClass = description.getTestClass();
        correlationId = CorrelationId.of(MessageFormat.format(CORRELATION_ID_PATTERN,
                testClass.getSimpleName(),
                description.getMethodName(),
                getRandomPart()));
    }

    private static String getRandomPart() {
        final String randomUuid = String.valueOf(UUID.randomUUID());
        return randomUuid.substring(0, 7);
    }

    /**
     * Returns the {@link CorrelationId} consisting of the qualified name of the current test method and a random
     * suffix.
     * Without random suffix the back-end would create one if the same correlation ID was used shortly after.
     * This could break things for cases where the correlation ID is subject to assertion.
     *
     * @return the correlation ID.
     */
    public CorrelationId getCorrelationId() {
        return correlationId;
    }

    /**
     * Returns the {@link CorrelationId} consisting of the qualified name of the current test method with the specified
     * suffix(es) appended.
     *
     * @param suffix the suffix to be appended to the current qualified test name.
     * @param moreSuffixes further suffixes to be appended to the current qualified test name.
     * @return the correlation ID.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public CorrelationId getCorrelationId(final CharSequence suffix, final CharSequence... moreSuffixes) {
        return correlationId.withSuffix(suffix, moreSuffixes);
    }

}
