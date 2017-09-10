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
package org.eclipse.ditto.signals.base;

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
