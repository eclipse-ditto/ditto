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
package org.eclipse.ditto.internal.models.signal.common;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds an instance for each actual sub-class of the interface class provided to {@link #newInstance(Class)}.
 */
@Immutable
public final class SignalInterfaceImplementations implements Iterable<SignalWithEntityId<?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignalInterfaceImplementations.class);

    private final List<SignalWithEntityId<?>> signals;
    private final List<Throwable> failures;

    private SignalInterfaceImplementations(final List<SignalWithEntityId<?>> signals,
            final List<Throwable> failures) {

        this.signals = List.copyOf(signals);
        this.failures = List.copyOf(failures);
    }

    public static <T extends SignalWithEntityId<?>> SignalInterfaceImplementations newInstance(
            final Class<T> signalInterfaceClass
    ) {
        ConditionChecker.checkNotNull(signalInterfaceClass, "signalInterfaceClass");
        if (!signalInterfaceClass.isInterface()) {
            throw new IllegalArgumentException(MessageFormat.format("<{0}> is not an interface.",
                    signalInterfaceClass.getName()));
        }

        final List<SignalWithEntityId<?>> successful = new ArrayList<>();
        final List<Throwable> failed = new ArrayList<>();

        SignalImplementationClassFinder.findImplementationClasses(signalInterfaceClass)
                .map(ReflectionBasedSignalInstantiator::tryToInstantiateSignal)
                .forEach(aTry -> {
                    if (aTry.isSuccess()) {
                        successful.add(aTry.get());
                    } else {
                        final var failure = aTry.failed();
                        failed.add(failure.get());
                    }
                });

        return new SignalInterfaceImplementations(successful, failed);
    }

    public Optional<SignalWithEntityId<?>> getSignalBySimpleClassName(final CharSequence expectedSimpleClassName) {
        final var result = signals.stream()
                .filter(signal -> {
                    final var signalClass = signal.getClass();
                    return Objects.equals(signalClass.getSimpleName(), expectedSimpleClassName.toString());
                })
                .findAny();
        if (result.isEmpty()) {
            LOGGER.info("Found no signal for type <{}>.", expectedSimpleClassName);
        }
        return result;
    }

    public List<Throwable> getFailures() {
        return failures;
    }

    @Override
    public Iterator<SignalWithEntityId<?>> iterator() {
        return signals.iterator();
    }

    public Stream<SignalWithEntityId<?>> stream() {
        return signals.stream();
    }

}
