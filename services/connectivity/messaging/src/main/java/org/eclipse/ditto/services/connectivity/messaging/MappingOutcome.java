/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.protocoladapter.TopicPath;

/**
 * Outcome of inbound and outbound message mapping.
 *
 * @param <T> type of mapped messages.
 */
public interface MappingOutcome<T> {

    /**
     * Evaluate this mapping outcome using a visitor.
     *
     * @param visitor the visitor.
     * @param <R> the result type.
     * @return the evaluation result.
     */
    <R> R accept(Visitor<T, R> visitor);

    /**
     * Create a mapped outcome.
     *
     * @param <T> type of the mapped message.
     * @param mapped the mapped message.
     * @param topicPath TopicPath of the mapped message.
     * @return the outcome.
     */
    static <T> MappingOutcome<T> mapped(final T mapped, final TopicPath topicPath) {
        return new MappedOutcome<>(mapped, topicPath);
    }

    /**
     * Create a dropped outcome.
     *
     * @param <T> type of any mapped message.
     * @return the outcome.
     */
    static <T> MappingOutcome<T> dropped() {
        return new DroppedOutcome<>();
    }

    /**
     * Create an error outcome.
     *
     * @param error the error.
     * @param topicPath any topic path known at the time of error.
     * @param <T> type of any mapped message.
     * @return the outcome.
     */
    static <T> MappingOutcome<T> error(final Exception error, @Nullable final TopicPath topicPath) {
        return new ErrorOutcome<>(error, topicPath);
    }

    /**
     * Create a new visitor builder.
     *
     * @param <T> type of mapped messages.
     * @param <R> type of evaluation results.
     * @return the new visitor builder.
     */
    static <T, R> VisitorBuilder<T, R> newVisitorBuilder() {
        return new VisitorBuilder<>();
    }

    /**
     * Visitor of a mapping outcome.
     *
     * @param <T> type of mapped messages.
     * @param <R> type of results.
     */
    interface Visitor<T, R> {

        /**
         * Evaluate a mapped result.
         *
         * @param mapped the mapped value.
         * @return the result.
         */
        R onMapped(T mapped);

        /**
         * Get the result for dropped messages.
         *
         * @return the result.
         */
        R onDropped();

        /**
         * Get a result for failed mapping.
         *
         * @param error the error causing the mapping failure.
         * @param topicPath topic path of the signal being mapped, if any is known.
         * @return the result.
         */
        R onError(Exception error, @Nullable TopicPath topicPath);
    }

    /**
     * Builder of a visitor.
     *
     * @param <T> type of mapped messages.
     * @param <R> type of results.
     */
    final class VisitorBuilder<T, R> {

        @Nullable private Function<T, R> onMapped;
        @Nullable private Supplier<R> onDropped;
        @Nullable private BiFunction<Exception, TopicPath, R> onError;

        /**
         * Set the mapped outcome evaluator.
         *
         * @param onMapped the evaluator.
         * @return this builder.
         */
        public VisitorBuilder<T, R> onMapped(final Function<T, R> onMapped) {
            this.onMapped = onMapped;
            return this;
        }

        /**
         * Set the dropped outcome evaluator.
         *
         * @param onDropped the evaluator.
         * @return this builder.
         */
        public VisitorBuilder<T, R> onDropped(final Supplier<R> onDropped) {
            this.onDropped = onDropped;
            return this;
        }

        /**
         * Set the error outcome evaluator.
         *
         * @param onError the evaluator.
         * @return this builder.
         */
        public VisitorBuilder<T, R> onError(final BiFunction<Exception, TopicPath, R> onError) {
            this.onError = onError;
            return this;
        }

        /**
         * Create a visitor from the evaluators of this builder.
         *
         * @return the visitor.
         * @throws java.lang.NullPointerException if any evaluator is not set.
         */
        public Visitor<T, R> build() {
            checkNotNull(onMapped, "onMapped");
            checkNotNull(onDropped, "onDropped");
            checkNotNull(onError, "onError");
            return new Visitor<>() {

                @Override
                public R onMapped(final T mapped) {
                    return onMapped.apply(mapped);
                }

                @Override
                public R onDropped() {
                    return onDropped.get();
                }

                @Override
                public R onError(final Exception error, @Nullable final TopicPath topicPath) {
                    return onError.apply(error, topicPath);
                }
            };
        }
    }
}

// private to MappingOutcome. Do NOT use directly.
final class MappedOutcome<T> implements MappingOutcome<T> {

    private final T mapped;
    private final TopicPath topicPath;

    MappedOutcome(final T mapped, final TopicPath topicPath) {
        this.mapped = mapped;
        this.topicPath = topicPath;
    }

    @Override
    public <R> R accept(final Visitor<T, R> visitor) {
        try {
            return visitor.onMapped(mapped);
        } catch (final Exception e) {
            return visitor.onError(e, topicPath);
        }
    }
}

// private to MappingOutcome. Do NOT use directly.
final class DroppedOutcome<T> implements MappingOutcome<T> {

    DroppedOutcome() {}

    @Override
    public <R> R accept(final Visitor<T, R> visitor) {
        try {
            return visitor.onDropped();
        } catch (final Exception e) {
            return visitor.onError(e, null);
        }
    }
}

// private to MappingOutcome. Do NOT use directly.
final class ErrorOutcome<T> implements MappingOutcome<T> {

    private final Exception error;
    @Nullable private final TopicPath topicPath;


    ErrorOutcome(final Exception error, @Nullable final TopicPath topicPath) {
        this.error = error;
        this.topicPath = topicPath;
    }

    @Override
    public <R> R accept(final Visitor<T, R> visitor) {
        return visitor.onError(error, topicPath);
    }
}
