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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import java.text.MessageFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.util.Try;

/**
 * Holds an instance for each actual sub-class of the interface class provided to {@link #newInstance(Class)}.
 */
@Immutable
final class SignalInterfaceImplementations implements Iterable<SignalWithEntityId<?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignalInterfaceImplementations.class);

    private final List<SignalWithEntityId<?>> signals;

    private SignalInterfaceImplementations(final List<SignalWithEntityId<?>> signals) {
        this.signals = signals;
    }

    static <T extends SignalWithEntityId<?>> SignalInterfaceImplementations newInstance(final Class<T> signalInterfaceClass) {
        ConditionChecker.checkNotNull(signalInterfaceClass, "signalInterfaceClass");
        if (!signalInterfaceClass.isInterface()) {
            throw new IllegalArgumentException(MessageFormat.format("<{0}> is not an interface.",
                    signalInterfaceClass.getName()));
        }
        return new SignalInterfaceImplementations(instantiateImplementingSignals(signalInterfaceClass));
    }

    private static List<SignalWithEntityId<?>> instantiateImplementingSignals(
            final Class<? extends SignalWithEntityId<?>> signalInterfaceClass
    ) {
        return SignalImplementationClassFinder.findImplementationClasses(signalInterfaceClass)
                .map(ReflectionBasedSignalInstantiator::tryToInstantiateSignal)
                .filter(Try::isSuccess)
                .map(Try::get)
                .collect(Collectors.toUnmodifiableList());
    }

    Optional<SignalWithEntityId<?>> getSignalBySimpleClassName(final CharSequence expectedSimpleClassName) {
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

    @NotNull
    @Override
    public Iterator<SignalWithEntityId<?>> iterator() {
        return signals.iterator();
    }

}
