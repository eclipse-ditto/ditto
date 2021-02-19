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
package org.eclipse.ditto.model.connectivity;

/**
 * An interface to enforce something on something from type <code>M</code>.
 * The implementation defines the rules/properties or whatever shall be enforces on the subject. So for more
 * clarity the documentation of the implementation should be checked.
 *
 * @param <M> the type of the subject, for which the enforcement is applied to
 */
public interface EnforcementFilter<M> {

    /**
     * Enforces something of the given input or validates it, basically it's up to the implementation. Throws an
     * exception in case that the implementation is not happy with the given input.
     *
     * @param filterInput the source from which the the placeholders in the filters are resolved
     * @throws org.eclipse.ditto.model.base.exceptions.DittoRuntimeException if enforcement could not be
     * archived/input was not valid
     */
    void match(M filterInput);
}
