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
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.signals.SignalWithEntityId;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;

/**
 * Uses {@link ClassGraph} to find all concrete sub-classes implementing the signal interface class that gets provided
 * to {@link #findImplementationClasses(Class)}.
 */
@Immutable
final class SignalImplementationClassFinder {

    private static final byte NUMBER_SCAN_THREADS = 2;

    private SignalImplementationClassFinder() {
        throw new AssertionError();
    }

    static <T extends SignalWithEntityId<?>> Stream<Class<T>> findImplementationClasses(final Class<T> signalInterfaceClass) {
        if (!signalInterfaceClass.isInterface()) {
            throw new IllegalArgumentException(MessageFormat.format("<{0}> is not an interface.",
                    signalInterfaceClass.getName()));
        }

        return getImplementationClasses(getClassGraph(signalInterfaceClass), signalInterfaceClass);
    }

    private static ClassGraph getClassGraph(final Class<?> signalInterfaceClass) {
        return new ClassGraph()
                .enableClassInfo()
                .ignoreClassVisibility()
                .acceptPackages(signalInterfaceClass.getPackageName());
    }

    @SuppressWarnings("unchecked")
    private static <T extends SignalWithEntityId<?>> Stream<Class<T>> getImplementationClasses(final ClassGraph classGraph,
            final Class<T> signalInterfaceClass) {

        final Predicate<ClassInfo> isConcreteImplementation = classInfo -> classInfo.isStandardClass() &&
                !classInfo.isAbstract() &&
                !classInfo.isEnum();

        final var scanResult = classGraph.scan(NUMBER_SCAN_THREADS);
        final var implementingClasses = scanResult.getClassesImplementing(signalInterfaceClass);

        return implementingClasses.stream()
                .filter(isConcreteImplementation)
                .map(ClassInfo::loadClass)
                .map(clazz -> (Class<T>) clazz)
                .onClose(scanResult::close);
    }

}
