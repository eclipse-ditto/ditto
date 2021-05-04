/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals;

/**
 * Implementations of this interface are associated to a {@code Feature} identified by the
 * value returned from {@link #getFeatureId()}.
 */
public interface WithFeatureId {

    /**
     * Returns the identifier of the associated Feature.
     *
     * @return the identifier of the associated Feature.
     */
    String getFeatureId();

}
