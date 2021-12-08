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
import java.util.function.Function;

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
final class AdaptableToSignalMapper<T extends Signal<T>> implements JsonifiableMapper<T> {


    private final Class<? extends T> targetType;
    private final Function<MappingContext, T> mappingFunction;

    private AdaptableToSignalMapper(final Class<? extends T> targetType,
            final Function<MappingContext, T> mappingFunction) {

        this.targetType = targetType;
        this.mappingFunction = mappingFunction;
    }

    /**
     * Returns an instance of {@code AdaptableToSignalMapper} that wraps the specified mapping function argument.
     *
     * @param targetType the type of the signals the returned mapper produces.
     * @param mappingFunction the actual mapping function.
     * @param <T> type of the signals that the returned mapper produces.
     * @return the {@code AdaptableToSignalMapper} instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static <T extends Signal<T>> AdaptableToSignalMapper<T> of(final Class<? extends T> targetType,
            final Function<MappingContext, T> mappingFunction) {

        return new AdaptableToSignalMapper<>(ConditionChecker.checkNotNull(targetType, "targetType"),
                ConditionChecker.checkNotNull(mappingFunction, "mappingFunction"));
    }

    @Override
    public T map(final Adaptable adaptable) {
        return tryToMapAdaptableToSignal(adaptable);
    }

    private T tryToMapAdaptableToSignal(final Adaptable adaptable) {
        try {
            return mappingFunction.apply(MappingContext.of(adaptable));
        } catch (final DittoRuntimeException e) {
            throw new IllegalAdaptableException(getDetailMessage(adaptable, e),
                    e.getDescription().orElse(null),
                    e,
                    e.getDittoHeaders());
        } catch (final Exception e) {
            throw new IllegalAdaptableException(getDetailMessage(adaptable, e), null, e, adaptable.getDittoHeaders());
        }
    }

    private String getDetailMessage(final Adaptable adaptable, final Throwable cause) {
        return MessageFormat.format("Failed to get <{0}> for <{1}>: {2}",
                targetType.getSimpleName(),
                adaptable,
                cause.getMessage());
    }

}
