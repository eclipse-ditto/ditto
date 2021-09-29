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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.atteo.classindex.ClassIndex;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.entity.type.EntityType;

/**
 * Factory for getting instances of implementations of {@link EntityId}.
 * It uses Reflection to find all EntityIds annotated with {@link org.eclipse.ditto.base.model.entity.id.TypedEntityId}
 * and expects one static method which accepts a {@link CharSequence} and returns something which is a subtype of
 * itself.
 *
 * @param <T> the base type of the {@code EntityId}s this factory provides.
 *
 * @since 2.1.0
 */
abstract class BaseEntityIdFactory<T extends EntityId> {

    private final Class<T> entityIdBaseType;
    private final Logger logger;
    private final Map<EntityType, Method> staticFactoryMethods;

    /**
     * Constructs a {@code BaseEntityIdFactory} object.
     *
     * @param entityIdBaseType the base type of the entity IDs this factory creates.
     */
    protected BaseEntityIdFactory(final Class<T> entityIdBaseType) {
        this.entityIdBaseType = ConditionChecker.checkNotNull(entityIdBaseType, "entityIdBaseType");
        logger = Logger.getLogger(getClass().getName());
        staticFactoryMethods = Collections.unmodifiableMap(getStaticFactoryMethodsForEntityIdBaseType());
    }

    @SuppressWarnings("unchecked")
    private Map<EntityType, Method> getStaticFactoryMethodsForEntityIdBaseType() {
        return typedEntityIdAnnotatedClasses()
                .filter(entityIdBaseType::isAssignableFrom)
                .map(aClass -> getStaticFactoryMethodOrThrow((Class<? extends EntityId>) aClass))
                .collect(Collectors.toMap(BaseEntityIdFactory::resolveEntityType, Function.identity()));
    }

    private static Stream<Class<?>> typedEntityIdAnnotatedClasses() {
        final Iterable<Class<?>> classIterable = ClassIndex.getAnnotated(TypedEntityId.class);
        return StreamSupport.stream(classIterable.spliterator(), false);
    }

    private static Method getStaticFactoryMethodOrThrow(final Class<? extends EntityId> classToInstantiate) {
        return EntityIdStaticFactoryMethodResolver.getStaticFactoryMethod(classToInstantiate)
                .orElseThrow(() -> {
                    final String pattern = "Could not find required instantiation method named <of> with sole" +
                            " parameter of type <CharSequence> for class <{0}>.";
                    return new AssertionError(MessageFormat.format(pattern, classToInstantiate.getName()));
                });
    }

    private static EntityType resolveEntityType(final Method staticFactoryMethod) {
        final Class<?> classToInstantiate = staticFactoryMethod.getDeclaringClass();
        final TypedEntityId annotation = classToInstantiate.getAnnotation(TypedEntityId.class);
        return EntityType.of(annotation.type());
    }

    /**
     * Returns the entity ID with the best fitting type for the specified {@link EntityType} using a best effort
     * approach.
     *
     * @param entityType the type of the entity which should be identified by the given char sequence.
     * @param entityIdValue the ID of an entity, i.e. the value of the returned entity ID.
     * @return the entity ID.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws EntityIdInvalidException if {@code entityIdValue} represents an invalid ID for {@code entityType}.
     */
    T getEntityId(final EntityType entityType, final CharSequence entityIdValue) {
        ConditionChecker.checkNotNull(entityType, "entityType");
        ConditionChecker.checkNotNull(entityIdValue, "entityIdValue");

        final T result;
        @Nullable final Method staticFactoryMethod = staticFactoryMethods.get(entityType);
        if (null != staticFactoryMethod) {
            result = tryToCallFactoryMethod(staticFactoryMethod, entityType, entityIdValue);
        } else {
            result = getFallbackEntityId(entityType, entityIdValue);
            logger.log(Level.WARNING,
                    "Could not find implementation for entity ID with type <{0}>." +
                            " This indicates an architectural flaw, because the ID implementation seems not to be" +
                            " on the classpath." +
                            " Returning a <{1}> instead.",
                    new Object[]{entityType, result.getClass().getSimpleName()});
        }
        return result;
    }

    private T tryToCallFactoryMethod(final Method factoryMethod,
            final EntityType entityType,
            final CharSequence entityIdValue) {

        try {
            return callFactoryMethod(factoryMethod, entityType, entityIdValue);
        } catch (final IllegalAccessException | IllegalArgumentException e) {
            return logExceptionAndGetFallbackEntityId(e, factoryMethod, entityType, entityIdValue);
        } catch (final InvocationTargetException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof EntityIdInvalidException) {
                throw (EntityIdInvalidException) cause;
            } else {
                return logExceptionAndGetFallbackEntityId(e, factoryMethod, entityType, entityIdValue);
            }
        }
    }

    private T callFactoryMethod(final Method staticFactoryMethod,
            final EntityType entityType,
            final CharSequence entityIdValue)
            throws InvocationTargetException, IllegalAccessException {

        final Object entityIdAsObject = staticFactoryMethod.invoke(entityType, entityIdValue);
        return entityIdBaseType.cast(entityIdAsObject);
    }

    private T logExceptionAndGetFallbackEntityId(final Throwable exception,
            final Method staticFactoryMethod,
            final EntityType entityType,
            final CharSequence entityIdValue) {

        final T fallbackEntityId = getFallbackEntityId(entityType, entityIdValue);
        final String pattern = "Encountered exception <{0}> while calling <{1}>: {2}. Returning a <{3}> instead.";
        final String msg = MessageFormat.format(pattern,
                exception.getClass().getSimpleName(),
                staticFactoryMethod,
                exception.getMessage(),
                fallbackEntityId.getClass().getSimpleName());
        logger.log(Level.WARNING, msg, exception);
        return fallbackEntityId;
    }

    protected abstract T getFallbackEntityId(EntityType entityType, CharSequence entityIdValue);

}
