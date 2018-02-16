/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.common.base.Converter;

import kamon.trace.Segment;

/**
 * Wraps a converter and executes all conversion actions in a trace segment.
 * @param <A> a
 * @param <B> b
 */
public class ConverterTraceWrapper<A, B> extends Converter<A, B> {

    private final Converter<A, B> converter;
    private final Supplier<Segment> forwardSegment;
    private final Supplier<Segment> backwardSegment;

    private ConverterTraceWrapper(Converter<A, B> converter, Supplier<Segment> forwardSegment,
            Supplier<Segment> backwardSegment) {
        this.converter = converter;
        this.forwardSegment = forwardSegment;
        this.backwardSegment = backwardSegment;
    }

    /**
     * Wraps a converter within a {@link ConverterTraceWrapper}.
     * @param converter the converter
     * @param segment a trace segment supplier for the forward and backward action
     * @param <A> a
     * @param <B> b
     * @return the wrapped converter
     */
    public static <A, B> Converter<A, B> wrap(Converter<A, B> converter, Supplier<Segment> segment) {
        return wrap(converter, segment, segment);
    }


    /**
     * Wraps a converter within a {@link ConverterTraceWrapper}.
     * @param converter the converter
     * @param forwardSegment a trace segment supplier for the forward action
     * @param backwardSegment a trace segment supplier for the backward action
     * @param <A> a
     * @param <B> b
     * @return the wrapped converter
     */
    @SuppressWarnings("WeakerAccess")
    public static <A, B> Converter<A, B> wrap(Converter<A, B> converter, Supplier<Segment> forwardSegment,
            Supplier<Segment> backwardSegment) {
        return new ConverterTraceWrapper<>(converter, forwardSegment, backwardSegment);
    }

    @Override
    protected B doForward(final A a) {
        return convertTraced(converter, forwardSegment, a);

    }

    @Override
    protected A doBackward(final B b) {
        return convertTraced(converter.reverse(), backwardSegment, b);
    }

    private static <A, B> B convertTraced(final Converter<A, B> converter, final Supplier<Segment> segmentSupplier,
            final A a) {
        final Segment segment = segmentSupplier.get();
        try {
            final B b = converter.convert(a);
            segment.finish();
            //noinspection ConstantConditions (will never be null, when 'a' is nonnull)
            return b;
        } catch (Exception e) {
            segment.finishWithError(e);
            throw e;
        }
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final ConverterTraceWrapper<?, ?> that = (ConverterTraceWrapper<?, ?>) o;
        return Objects.equals(converter, that.converter) &&
                Objects.equals(forwardSegment, that.forwardSegment) &&
                Objects.equals(backwardSegment, that.backwardSegment);
    }

    @Override
    public int hashCode() {

        return Objects.hash(converter, forwardSegment, backwardSegment);
    }
}
