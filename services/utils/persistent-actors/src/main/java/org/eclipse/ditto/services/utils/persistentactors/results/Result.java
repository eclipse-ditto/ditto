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
package org.eclipse.ditto.services.utils.persistentactors.results;

/**
 * The result of applying the strategy to the given command.
 */
public interface Result<E> {

    /**
     * Evaluate the result by a visitor.
     *
     * @param visitor the visitor to evaluate the result, typically the persistent actor itself.
     */
    void accept(final ResultVisitor<E> visitor);

    /**
     * @return the empty result
     */
    static Result empty() {
        return ResultFactory.emptyResult();
    }

}
