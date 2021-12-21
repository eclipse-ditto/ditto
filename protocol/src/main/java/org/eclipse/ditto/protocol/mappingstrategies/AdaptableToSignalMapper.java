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
package org.eclipse.ditto.protocol.mappingstrategies;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;

/**
 * Wraps a function that creates a {@link Signal} from a {@link MappingContext}.
 * Any {@code Exception} that occurs in that function leads to an {@link IllegalAdaptableException} which adds context
 * to the failure.
 * The overall goal is to provide as much information as possible to identify the cause of a possible failure when
 * mapping an {@link Adaptable} to a {@code Signal}.
 *
 * @since 2.3.0
 */
final class AdaptableToSignalMapper<T extends Signal<?>> implements JsonifiableMapper<T> {

    private final String signalType;
    private final Function<MappingContext, T> mappingFunction;

    private AdaptableToSignalMapper(final String signalType, final Function<MappingContext, T> mappingFunction) {
        this.signalType = signalType;
        this.mappingFunction = mappingFunction;
    }

    /**
     * Returns an instance of {@code AdaptableToSignalMapper} that wraps the specified mapping function argument.
     *
     * @param signalType the type of the signals the returned mapper produces.
     * @param mappingFunction the actual mapping function.
     * @param <T> type of the signals that the returned mapper produces.
     * @return the {@code AdaptableToSignalMapper} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code signalType} is empty or blank.
     */
    static <T extends Signal<?>> AdaptableToSignalMapper<T> of(final CharSequence signalType,
            final Function<MappingContext, T> mappingFunction) {

        return new AdaptableToSignalMapper<>(
                ConditionChecker.checkArgument(ConditionChecker.checkNotNull(signalType, "signalType").toString(),
                        arg -> !arg.trim().isEmpty(),
                        () -> "The signalType must not be blank."),
                ConditionChecker.checkNotNull(mappingFunction, "mappingFunction")
        );
    }

    @Override
    public T map(final Adaptable adaptable) {
        return tryToMapAdaptableToSignal(adaptable);
    }

    private T tryToMapAdaptableToSignal(final Adaptable adaptable) {
        try {
            return mappingFunction.apply(MappingContext.of(adaptable));
        } catch (final DittoRuntimeException e) {
            throw IllegalAdaptableException.newBuilder(getDetailMessage(adaptable, e), adaptable)
                    .withDescription(e.getDescription().orElse(null))
                    .withSignalType(signalType)
                    .withCause(e)
                    .build();
        } catch (final Exception e) {
            throw IllegalAdaptableException.newBuilder(getDetailMessage(adaptable, e), adaptable)
                    .withSignalType(signalType)
                    .withCause(e)
                    .build();
        }
    }

    private String getDetailMessage(final Adaptable adaptable, final Throwable cause) {
        return MessageFormat.format("Failed to get Signal of type <{0}> for <{1}>: {2}",
                signalType,
                adaptable,
                cause.getMessage());
    }

    String getSignalType() {
        return signalType;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AdaptableToSignalMapper<?> that = (AdaptableToSignalMapper<?>) o;
        return Objects.equals(signalType, that.signalType) &&
                Objects.equals(mappingFunction, that.mappingFunction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signalType, mappingFunction);
    }

}
