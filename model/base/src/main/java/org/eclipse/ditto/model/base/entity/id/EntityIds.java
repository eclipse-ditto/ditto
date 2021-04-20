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
package org.eclipse.ditto.model.base.entity.id;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.atteo.classindex.ClassIndex;
import org.eclipse.ditto.model.base.entity.type.EntityType;

/**
 * Factory class to instantiate the correct entity type based on the given {@link EntityType}.
 * Uses Reflection to find all EntityIds annotated with {@link TypedEntityId} and expects one static method
 * which accepts a {@link CharSequence} and returns something which is a subtype of itself.
 */
final class EntityIds {

    /**
     * Holds all instantiation methods for entity IDs with known entity types (including NamespacedEntityIds).
     */
    private static final Map<String, Method> ID_FACTORIES;

    /**
     * Holds all instantiation methods for namespaced entity IDs with known entity types.
     */
    private static final Map<String, Method> NAMESPACED_ID_FACTORIES;

    static {
        ID_FACTORIES = getFactoriesFor(EntityId.class);
        NAMESPACED_ID_FACTORIES = getFactoriesFor(NamespacedEntityId.class);
    }

    private EntityIds() {
        throw new AssertionError("Should never be called");
    }

    /**
     * Best effort to initialize the most concrete type of a {@link NamespacedEntityId} based on the given entityType.
     *
     * @param entityType The type of the entity which should be identified by the given entity ID.
     * @param entityId The ID of an entity.
     * @return the namespaced entity ID.
     */
    public static NamespacedEntityId getNamespacedEntityId(final EntityType entityType, final CharSequence entityId) {
        return Optional.ofNullable(NAMESPACED_ID_FACTORIES.get(entityType.toString()))
                .map(method -> {
                    try {
                        return (NamespacedEntityId) method.invoke(null, entityId);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        Logger.getLogger(EntityIds.class.getName()).log(Level.WARNING, e.getMessage(), e);

                        return null;
                    }
                }).orElseGet(() -> {
                    Logger.getLogger(EntityIds.class.getName()).warning(
                            String.format("Could not find implementation for entity ID with type <%s>. " +
                                    "This indicates an architectural flaw, because the ID seems not to be on the classpath",
                            entityType));
                    return FallbackNamespacedEntityId.of(entityType, entityId);
                });
    }

    /**
     * Best effort to initialize the most concrete type of a {@link EntityId} based on the given entityType.
     *
     * @param entityType The type of the entity which should be identified by the given entity ID.
     * @param entityId The ID of an entity.
     * @return the entity ID.
     */
    public static EntityId getEntityId(final EntityType entityType, final CharSequence entityId) {
        return Optional.ofNullable(ID_FACTORIES.get(entityType.toString()))
                .map(method -> {
                    try {
                        return (EntityId) method.invoke(null, entityId);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        Logger.getLogger(EntityIds.class.getName()).log(Level.WARNING, e.getMessage(), e);

                        return null;
                    }
                }).orElseGet(() -> {
                    Logger.getLogger(EntityIds.class.getName()).warning(
                            String.format("Could not find implementation for entity ID with type <%s>. " +
                                    "This indicates an architectural flaw, because the ID seems not to be on the classpath",
                                    entityType));
                    return FallbackEntityId.of(entityType, entityId);
                });
    }

    private static Map<String, Method> getFactoriesFor(final Class<?> baseClass) {
        return StreamSupport.stream(ClassIndex.getAnnotated(TypedEntityId.class).spliterator(), false)
                .filter(baseClass::isAssignableFrom)
                .collect(Collectors.<Class<?>, String, Method>toMap(
                        classToInstantiate -> classToInstantiate.getAnnotation(TypedEntityId.class).type(),
                        EntityIds::getFactoryMethodWithCharSequenceParameter));
    }

    private static Method getFactoryMethodWithCharSequenceParameter(final Class<?> classToInstantiate) {
        return Arrays.stream(classToInstantiate.getMethods())
                .filter(innerMethod -> Modifier.isStatic(innerMethod.getModifiers()))
                .filter(innerMethod -> innerMethod.getParameterCount() == 1)
                .filter(innerMethod -> innerMethod.getParameterTypes()[0].isAssignableFrom(CharSequence.class))
                .filter(innerMethod -> classToInstantiate.isAssignableFrom(innerMethod.getReturnType()))
                .findAny()
                .orElseThrow(() -> {
                    final String message = String.format(
                            "Could not find required instantiation method <of> with parameter <CharSequence>" +
                                    " for class <%s>", classToInstantiate.getSimpleName());
                    throw new IllegalArgumentException(message);
                });
    }

}
