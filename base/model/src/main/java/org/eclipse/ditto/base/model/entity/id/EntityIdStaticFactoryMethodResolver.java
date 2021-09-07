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
package org.eclipse.ditto.base.model.entity.id;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * This class resolves the static factory method of a particular implementation class of {@link EntityId}.
 *
 * @see #getStaticFactoryMethod(Class)
 * @since 2.1.0
 */
@Immutable
final class EntityIdStaticFactoryMethodResolver {

    private EntityIdStaticFactoryMethodResolver() {
        throw new AssertionError();
    }

    /**
     * Tries to resolve the appropriate static factory method for the specified {@code Class} argument.
     * <em>Appropriate</em> in this context means:
     * <ul>
     *     <li>The method is declared as {@code static}.</li>
     *     <li>The method's visibility is {@code public}.</li>
     *     <li>The method's return type is either the same as the specified class or a supertype thereof.</li>
     *     <li>The method has exactly one parameter.</li>
     *     <li>The method's parameter type is either {@code CharSequence} or a subtype thereof.</li>
     * </ul>
     * The name of the method is taken into account if multiple appropriate methods are found.
     * The method name order is defined as follows:
     * <ul>
     *     <li>"of",</li>
     *     <li>"getInstance",</li>
     *     <li>"newInstance",</li>
     *     <li>alphabetical order.</li>
     * </ul>
     *
     * @param entityIdClass the class to get the appropriate static factory method for.
     * @return an Optional containing the found static factory method. The Optional is empty if {@code entityIdClass}
     * contains no appropriate static factory method.
     * @throws NullPointerException if {@code entityIdClass} is {@code null}.
     */
    public static Optional<Method> getStaticFactoryMethod(final Class<? extends EntityId> entityIdClass) {
        return declaredMethods(ConditionChecker.checkNotNull(entityIdClass, "entityIdClass"))
                .filter(isStatic())
                .filter(isPublic())
                .filter(returnsTypeAssignableFromDeclaringClass())
                .filter(hasExactlyOneCharSequenceParameter())
                .min(preferredMethodNamesFirst());
    }

    private static Stream<Method> declaredMethods(final Class<?> clazz) {
        return Stream.of(clazz.getDeclaredMethods());
    }

    private static Predicate<Method> isStatic() {
        return method -> Modifier.isStatic(method.getModifiers());
    }

    private static Predicate<Method> isPublic() {
        return method -> Modifier.isPublic(method.getModifiers());
    }

    private static Predicate<Method> returnsTypeAssignableFromDeclaringClass() {
        return method -> {
            final Class<?> returnType = method.getReturnType();
            return returnType.isAssignableFrom(method.getDeclaringClass());
        };
    }

    private static Predicate<Method> hasExactlyOneCharSequenceParameter() {
        return method -> {
            final boolean result;
            if (1 == method.getParameterCount()) {
                final Class<?>[] parameterTypes = method.getParameterTypes();
                final Class<?> parameterType = parameterTypes[0];
                result = parameterType.isAssignableFrom(CharSequence.class);
            } else {
                result = false;
            }
            return result;
        };
    }

    private static Comparator<Method> preferredMethodNamesFirst() {
        final Comparator<String> staticFactoryMethodNameComparator = new StaticFactoryMethodNameComparator();
        return (o1, o2) -> staticFactoryMethodNameComparator.compare(o1.getName(), o2.getName());
    }

    static final class StaticFactoryMethodNameComparator implements Comparator<String> {

        static final List<String> PREFERRED_METHOD_NAMES_ASCENDING =
                Collections.unmodifiableList(Arrays.asList("of", "getInstance", "newInstance"));

        @Override
        public int compare(final String o1, final String o2) {
            if (o1.equals(o2)) {
                return 0;
            } else {
                for (final String preferredMethodName : PREFERRED_METHOD_NAMES_ASCENDING) {
                    if (preferredMethodName.equals(o1)) {
                        return -1;
                    } else if (preferredMethodName.equals(o2)) {
                        return 1;
                    }
                }
                return o1.compareTo(o2);
            }
        }

    }

}
