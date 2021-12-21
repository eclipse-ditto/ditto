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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.AskException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

/**
 * Uses Java's reflection functionality to instantiate an object for a particular class of a {@link SignalWithEntityId}.
 * The arguments that are used for the static factory method are statically provided.
 * Their only purpose is to technically enable a valid call to static factory method.
 * If an argument is {@code @Nullable}, {@code null} will be used.
 */
@Immutable
final class ReflectionBasedSignalInstantiator {

    private static final Map<Class<?>, Object> PARAMETER_VALUES_PER_TYPE;
    private static final Logger LOGGER = LoggerFactory.getLogger(ReflectionBasedSignalInstantiator.class);

    static {
        final var stringValue = "myAttributeOrFeature";
        final var feature = Feature.newBuilder().withId(stringValue).build();
        final var thingId = ThingId.generateRandom();
        final var messageHeaders = MessageHeaders.newBuilder(MessageDirection.TO, thingId, "mySubject")
                .featureId(stringValue)
                .randomCorrelationId()
                .build();
        final var definitionIdentifier = "org.example:myDefinition:1.0.0";
        final var policyId = PolicyId.inNamespaceWithRandomName("");
        PARAMETER_VALUES_PER_TYPE = Map.ofEntries(
                Map.entry(Attributes.class, Attributes.newBuilder().set("manufacturer", "Bosch.IO").build()),
                Map.entry(CharSequence.class, stringValue),
                Map.entry(DittoHeaders.class, DittoHeaders.newBuilder().randomCorrelationId().build()),
                Map.entry(DittoRuntimeException.class, AskException.newBuilder().build()),
                Map.entry(Feature.class, feature),
                Map.entry(Features.class, Features.newBuilder().set(feature).build()),
                Map.entry(FeatureDefinition.class, FeatureDefinition.fromIdentifier(definitionIdentifier)),
                Map.entry(FeatureProperties.class, FeatureProperties.newBuilder().set("fleeb", "noob").build()),
                Map.entry(HttpStatus.class, HttpStatus.OK),
                Map.entry(JsonArray.class, JsonArray.of(JsonValue.of(definitionIdentifier))),
                Map.entry(JsonObject.class, JsonObject.newBuilder().set("foo", "bar").build()),
                Map.entry(JsonPointer.class, JsonPointer.of("foo/bar/baz")),
                Map.entry(JsonValue.class, JsonObject.newBuilder().set("bar", "baz").build()),
                Map.entry(Message.class, Message.newBuilder(messageHeaders).payload("myPayload").build()),
                Map.entry(MessageHeaders.class, messageHeaders),
                Map.entry(Policy.class, Policy.newBuilder().setId(policyId).build()),
                Map.entry(PolicyId.class, policyId),
                Map.entry(String.class, stringValue),
                Map.entry(Thing.class, Thing.newBuilder().setId(thingId).build()),
                Map.entry(ThingDefinition.class, ThingsModelFactory.newDefinition(definitionIdentifier)),
                Map.entry(ThingId.class, thingId)
        );
    }

    private ReflectionBasedSignalInstantiator() {
        throw new AssertionError();
    }

    static <T extends SignalWithEntityId<?>> Try<T> tryToInstantiateSignal(final Class<T> signalImplementationClass) {
        try {
            return new Success<>(instantiateSignal(signalImplementationClass));
        } catch (final Exception e) {
            Throwable exception = e;
            if (null == e.getMessage()) {
                final var cause = e.getCause();
                if (null != cause) {
                    exception = cause;
                }
            }
            LOGGER.warn("Failed to instantiate Signal for <{}>: {}",
                    signalImplementationClass.getName(),
                    exception.getMessage(),
                    exception);
            final var instantiationExceptionMessage = MessageFormat.format("Failed to instantiate <{0}>: {1}",
                    signalImplementationClass.getName(),
                    exception.getMessage());
            final var instantiationException = new InstantiationException(instantiationExceptionMessage);
            instantiationException.initCause(exception);
            return new Failure<>(instantiationException);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends SignalWithEntityId<?>> T instantiateSignal(final Class<T> signalImplementationClass)
            throws InvocationTargetException, IllegalAccessException {

        final var staticFactoryMethod = getStaticFactoryMethodOrThrow(signalImplementationClass);
        final var parameterValues = Stream.of(staticFactoryMethod.getParameters())
                .map(ReflectionBasedSignalInstantiator::getSuitableParameterValue)
                .toArray();

        return (T) staticFactoryMethod.invoke(null, parameterValues);
    }

    private static Method getStaticFactoryMethodOrThrow(final Class<?> clazz) {
        final var acceptedStaticFactoryMethodNames = List.of("of", "created", "modified", "newInstance", "getInstance");
        final var declaredMethods = getDeclaredMethodsByName(clazz);

        for (final var acceptedStaticFactoryMethodName : acceptedStaticFactoryMethodNames) {
            @Nullable final var declaredMethodsByName = declaredMethods.get(acceptedStaticFactoryMethodName);
            if (null != declaredMethodsByName) {
                return declaredMethodsByName.get(0);
            }
        }
        final var pattern = "Found no static factory method with any name of {0}.";
        throw new NoSuchElementException(MessageFormat.format(pattern, acceptedStaticFactoryMethodNames));
    }

    private static Map<String, List<Method>> getDeclaredMethodsByName(final Class<?> clazz) {
        return Stream.of(clazz.getDeclaredMethods())
                .filter(ReflectionBasedSignalInstantiator::isPublicStatic)
                .collect(Collectors.groupingBy(Method::getName, Collectors.toList()));
    }

    private static boolean isPublicStatic(final Method method) {
        final var modifiers = method.getModifiers();
        return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers);
    }

    @Nullable
    private static Object getSuitableParameterValue(final Parameter parameter) {
        final var parameterType = parameter.getType();
        final Object result;
        if (parameter.isAnnotationPresent(Nullable.class)) {
            result = null;
        } else {
            result = PARAMETER_VALUES_PER_TYPE.get(parameterType);
            if (null == result) {
                LOGGER.warn("Found not value for parameter type <{}>. Using null instead.", parameterType.getName());
            }
        }

        return result;
    }

}
