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
package org.eclipse.ditto.services.models.streaming;

/**
 * Visitor to evaluate start-stream-request commands.
 *
 * @param <T> the result type.
 */
public interface StartStreamRequestVisitor<T> {

    /**
     * Evaluate {@code SudoStreamModifiedEntities}.
     *
     * @param command the command.
     * @return the evaluation result.
     */
    T visit(SudoStreamModifiedEntities command);

    /**
     * Evaluate {@code SudoStreamSnapshotRevisions}.
     *
     * @param command the command.
     * @return the evaluation result.
     */
    T visit(SudoStreamSnapshotRevisions command);
}
