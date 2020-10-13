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
package org.eclipse.ditto.services.connectivity.messaging.mappingoutcome;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;

/**
 * Outcome of inbound and outbound message mapping.
 * This is an algebraic datatype. DO NOT inherit!
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
     * @param externalMessage external message for incoming mapping, or null for outgoing mapping.
     * @return the outcome.
     */
    static <T> MappingOutcome<T> mapped(final T mapped, final TopicPath topicPath,
            @Nullable final ExternalMessage externalMessage) {
        return new MappedOutcome<>(mapped, topicPath, externalMessage);
    }

    /**
     * Create a dropped outcome.
     *
     * @param droppedMessage the dropped message.
     * @param <T> type of any mapped message.
     * @return the outcome.
     */
    static <T> MappingOutcome<T> dropped(@Nullable final ExternalMessage droppedMessage) {
        return new DroppedOutcome<>(droppedMessage);
    }

    /**
     * Create an error outcome.
     *
     * @param error the error.
     * @param topicPath any topic path known at the time of error.
     * @param externalMessage external message for incoming mapping, or null for outgoing mapping.
     * @param <T> type of any mapped message.
     * @return the outcome.
     */
    static <T> MappingOutcome<T> error(final Exception error, @Nullable final TopicPath topicPath,
            @Nullable final ExternalMessage externalMessage) {
        return new ErrorOutcome<>(error, topicPath, externalMessage);
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
     * Functional interface for error callbacks.
     *
     * @param <R> type of evaluation results.
     */
    @FunctionalInterface
    interface OnError<R> {

        /**
         * Evaluate for an error.
         *
         * @param error the error.
         * @param topicPath the topic path, if known.
         * @param externalMessage the external message for incoming mapping, or null for outgoing mapping.
         * @return the evaluation result.
         */
        R apply(Exception error, @Nullable TopicPath topicPath, @Nullable ExternalMessage externalMessage);
    }

    /**
     * Visitor of a mapping outcome.
     *
     * @param <T> type of mapped messages.
     * @param <R> type of results.
     */
    interface Visitor<T, R> {

        /**
         * Evaluate a mapping outcome by this visitor.
         *
         * @param outcome the outcome being evaluated.
         * @return the evaluation result.
         */
        default R eval(final MappingOutcome<T> outcome) {
            return outcome.accept(this);
        }

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
         * @param droppedMessage the dropped message.
         * @return the result.
         */
        R onDropped(@Nullable ExternalMessage droppedMessage);

        /**
         * Get a result for failed mapping.
         *
         * @param error the error causing the mapping failure.
         * @param topicPath topic path of the signal being mapped, if any is known.
         * @param externalMessage the external message for incoming mapping, or null for outgoing mapping.
         * @return the result.
         */
        R onError(Exception error, @Nullable TopicPath topicPath, @Nullable ExternalMessage externalMessage);
    }

    /**
     * Builder of a visitor.
     *
     * @param <T> type of mapped messages.
     * @param <R> type of results.
     */
    final class VisitorBuilder<T, R> {

        @Nullable private Function<T, R> onMapped;
        @Nullable private Function<ExternalMessage, R> onDropped;
        @Nullable private OnError<R> onError;

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
        public VisitorBuilder<T, R> onDropped(final Function<ExternalMessage, R> onDropped) {
            this.onDropped = onDropped;
            return this;
        }

        /**
         * Set the error outcome evaluator.
         *
         * @param onError the evaluator.
         * @return this builder.
         */
        public VisitorBuilder<T, R> onError(final OnError<R> onError) {
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
                public R onDropped(@Nullable final ExternalMessage droppedMessage) {
                    return onDropped.apply(droppedMessage);
                }

                @Override
                public R onError(final Exception error, @Nullable final TopicPath topicPath,
                        @Nullable final ExternalMessage externalMessage) {
                    return onError.apply(error, topicPath, externalMessage);
                }
            };
        }
    }
}
