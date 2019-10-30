/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.placeholders;

import java.util.function.Function;

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

    interface Builder<T> {

        PipelineElementVisitor<T> build();

        Builder<T> resolved(Function<String, T> onResolution);

        Builder<T> unresolved(T onIrresolution);

        Builder<T> deleted(T onDeletion);
    }
}
