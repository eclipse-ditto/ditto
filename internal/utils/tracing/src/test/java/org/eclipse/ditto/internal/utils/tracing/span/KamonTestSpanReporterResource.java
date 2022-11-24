/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.tracing.span;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.junit.rules.ExternalResource;

import kamon.Kamon;
import kamon.module.Module;

/**
 * This ExternalResource helps to create instances of {@link TestSpanReporter} and to add them Kamon.
 * After the test the registrations are canceled.
 * Each {@code TestSpanReporter} must be registered with a unique name.
 * Both, cancelling registrations after test and enforcing a unique name stabilises tests which rely on span reporting.
 */
@NotThreadSafe
public final class KamonTestSpanReporterResource extends ExternalResource {

    private final Map<String, Module.Registration> reporterRegistrations;

    private KamonTestSpanReporterResource() {
        reporterRegistrations = new HashMap<>(3);
    }

    /**
     * Returns a new instance of {@code KamonTestSpanReporterResource}.
     *
     * @return the new instance.
     */
    public static KamonTestSpanReporterResource newInstance() {
        return new KamonTestSpanReporterResource();
    }

    /**
     * Creates a {@code TestSpanReporter} and registers it under the specified name argument at Kamon.
     *
     * @param reporterName the registration name of the returned reporter.
     * @return the new {@code TestSpanReporter} instance.
     * @throws NullPointerException if {@code reporterName} is {@code null}.
     * @throws IllegalArgumentException if a reporter with name {@code reporterName} was already registered.
     */
    public TestSpanReporter registerTestSpanReporter(final CharSequence reporterName) {
        final var reporterNameAsString = validateReporterName(reporterName);
        final var result = TestSpanReporter.newInstance();
        reporterRegistrations.put(reporterNameAsString, Kamon.addReporter(reporterNameAsString, result));
        return result;
    }

    private String validateReporterName(final CharSequence reporterName) {
        ConditionChecker.checkNotNull(reporterName, "reporterName");
        final var result = reporterName.toString();
        if (reporterRegistrations.containsKey(result)) {
            throw new IllegalArgumentException(
                    MessageFormat.format("A reporter with name <{0}> was already registered.", result)
            );
        }
        return result;
    }

    @Override
    protected void after() {
        final var registrationEntryIterator = getRegistrationEntryIterator();
        while (registrationEntryIterator.hasNext()) {
            cancelRegistration(registrationEntryIterator.next());
            registrationEntryIterator.remove();
        }
        super.after();
    }

    private Iterator<Map.Entry<String, Module.Registration>> getRegistrationEntryIterator() {
        final var reporterRegistrationEntries = reporterRegistrations.entrySet();
        return reporterRegistrationEntries.iterator();
    }

    private static void cancelRegistration(final Map.Entry<String, Module.Registration> registrationEntry) {
        final var registration = registrationEntry.getValue();
        registration.cancel();
    }

}
