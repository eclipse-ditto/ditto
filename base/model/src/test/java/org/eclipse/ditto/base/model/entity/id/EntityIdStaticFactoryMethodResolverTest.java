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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.reflect.Method;
import java.util.Optional;

import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.junit.Test;

/**
 * Unit test for {@link EntityIdStaticFactoryMethodResolver}.
 */
public final class EntityIdStaticFactoryMethodResolverTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(EntityIdStaticFactoryMethodResolver.class, areImmutable());
    }

    @Test
    public void getStaticFactoryMethodForNullClassFails() {
        assertThatNullPointerException()
                .isThrownBy(() -> EntityIdStaticFactoryMethodResolver.getStaticFactoryMethod(null))
                .withMessage("The entityIdClass must not be null!")
                .withNoCause();
    }

    @Test
    public void getStaticFactoryMethodForAppropriateEntityIdClassReturnsExpectedMethod() throws NoSuchMethodException {
        final Class<EntityIdForTests> entityIdClass = EntityIdForTests.class;
        final Method expectedStaticFactoryMethod = entityIdClass.getDeclaredMethod("of", CharSequence.class);
        final Optional<Method> staticFactoryMethod =
                EntityIdStaticFactoryMethodResolver.getStaticFactoryMethod(entityIdClass);

        assertThat(staticFactoryMethod).hasValue(expectedStaticFactoryMethod);
    }

    @Test
    public void getStaticFactoryMethodForClassWithFactoryMethodThatReturnsSupertypeReturnsExpectedMethod()
            throws NoSuchMethodException {

        final Class<StaticFactoryMethodReturnsSuperclass> entityIdClass = StaticFactoryMethodReturnsSuperclass.class;
        final Method expectedStaticFactoryMethod = entityIdClass.getDeclaredMethod("of", CharSequence.class);
        final Optional<Method> staticFactoryMethod =
                EntityIdStaticFactoryMethodResolver.getStaticFactoryMethod(entityIdClass);

        assertThat(staticFactoryMethod).hasValue(expectedStaticFactoryMethod);
    }

    @Test
    public void getStaticFactoryMethodForClassWithoutMethodsReturnsEmptyOptional() {
        final class ClassWithoutDeclaredMethods extends AbstractEntityId {

            private ClassWithoutDeclaredMethods(final EntityType entityType, final CharSequence id) {
                super(entityType, id);
            }
        }

        final Optional<Method> staticFactoryMethod =
                EntityIdStaticFactoryMethodResolver.getStaticFactoryMethod(ClassWithoutDeclaredMethods.class);

        assertThat(staticFactoryMethod).isEmpty();
    }

    @Test
    public void getStaticFactoryMethodForClassWithoutMethodsReturningTheDeclaredClassReturnsEmptyOptional() {
        final Optional<Method> staticFactoryMethod =
                EntityIdStaticFactoryMethodResolver.getStaticFactoryMethod(StaticFactoryMethodReturnsString.class);

        assertThat(staticFactoryMethod).isEmpty();
    }

    @Test
    public void getStaticFactoryMethodForClassWithStaticFactoryMethodWithSoleIntParameterReturnsEmptyOptional() {
        final Optional<Method> staticFactoryMethod =
                EntityIdStaticFactoryMethodResolver.getStaticFactoryMethod(StaticFactoryMethodHasSoleIntParameter.class);

        assertThat(staticFactoryMethod).isEmpty();
    }

    @Test
    public void getStaticFactoryMethodForClassWithStaticFactoryMethodWithTwoParametersReturnsEmptyOptional() {
        final Optional<Method> staticFactoryMethod =
                EntityIdStaticFactoryMethodResolver.getStaticFactoryMethod(StaticFactoryMethodHasTwoParameters.class);

        assertThat(staticFactoryMethod).isEmpty();
    }

    @Test
    public void getStaticFactoryMethodForClassWithNonPublicStaticFactoryMethodReturnsEmptyOptional() {
        final Optional<Method> staticFactoryMethod =
                EntityIdStaticFactoryMethodResolver.getStaticFactoryMethod(StaticFactoryMethodIsNotPublic.class);

        assertThat(staticFactoryMethod).isEmpty();
    }

    private static final class StaticFactoryMethodReturnsSuperclass extends AbstractEntityId {

        private StaticFactoryMethodReturnsSuperclass(final EntityType entityType, final CharSequence id) {
            super(entityType, id);
        }

        public static AbstractEntityId of(final CharSequence id) {
            return new StaticFactoryMethodReturnsSuperclass(EntityType.of(id), id);
        }

    }

    private static final class StaticFactoryMethodReturnsString extends AbstractEntityId {

        private StaticFactoryMethodReturnsString(final EntityType entityType, final CharSequence id) {
            super(entityType, id);
        }

        public static String of(final CharSequence id) {
            return StaticFactoryMethodReturnsString.class.getSimpleName();
        }

    }

    private static final class StaticFactoryMethodHasSoleIntParameter extends AbstractEntityId {

        private StaticFactoryMethodHasSoleIntParameter(final EntityType entityType, final CharSequence id) {
            super(entityType, id);
        }

        public static StaticFactoryMethodHasSoleIntParameter of(final int id) {
            final String idAsString = String.valueOf(id);
            return new StaticFactoryMethodHasSoleIntParameter(EntityType.of(idAsString), idAsString);
        }

    }

    private static final class StaticFactoryMethodHasTwoParameters extends AbstractEntityId {

        private StaticFactoryMethodHasTwoParameters(final EntityType entityType, final CharSequence id) {
            super(entityType, id);
        }

        public static StaticFactoryMethodHasTwoParameters of(final CharSequence id, final boolean isGrault) {
            return new StaticFactoryMethodHasTwoParameters(EntityType.of(id + String.valueOf(isGrault)), id);
        }

    }

    private static final class StaticFactoryMethodIsNotPublic extends AbstractEntityId {

        private StaticFactoryMethodIsNotPublic(final EntityType entityType, final CharSequence id) {
            super(entityType, id);
        }

        static StaticFactoryMethodIsNotPublic of(final CharSequence id) {
            return new StaticFactoryMethodIsNotPublic(EntityType.of(id), id);
        }

    }

}