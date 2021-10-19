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
package org.eclipse.ditto.placeholders;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Visitor to evaluate pipeline elements.
 *
 * @param <T> type of results.
 */
public interface PipelineElementVisitor<T> {

    /**
     * Evaluate a resolved value.
     *
     * @param value the resolved value.
     * @return the result.
     */
    T resolved(String value);

    /**
     * Evaluate the unique pipeline element signifying failed resolution.
     *
     * @return the result.
     */
    T unresolved();

    /**
     * Evaluate the unique pipeline element signifying deletion of the whole string containing the pipeline.
     *
     * @return the result.
     */
    T deleted();

    /**
     * Builder of a visitor to evaluate pipeline elements.
     *
     * @param <T> the type of results.
     */
    interface Builder<T> {

        /**
         * Create the visitor.
         *
         * @return the visitor.
         */
        PipelineElementVisitor<T> build();

        /**
         * Set callback to handle resolved values.
         *
         * @param onResolution what to do on resolved values.
         * @return this builder.
         */
        Builder<T> resolved(Function<String, T> onResolution);

        /**
         * Set callback to handle resolution failure.
         *
         * @param onIrresolution what to do on resolution failure.
         * @return this builder.
         */
        Builder<T> unresolved(Supplier<T> onIrresolution);

        /**
         * Set callback to handle deletion.
         *
         * @param onDeletion what to do when deletion is signalled.
         * @return this builder.
         */
        Builder<T> deleted(Supplier<T> onDeletion);
    }
}
