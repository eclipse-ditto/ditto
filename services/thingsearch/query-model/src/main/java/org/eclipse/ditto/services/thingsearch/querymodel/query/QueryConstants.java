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
package org.eclipse.ditto.services.thingsearch.querymodel.query;

import javax.annotation.concurrent.Immutable;

/**
 * Constants which are related to search queries.
 */
@Immutable
public final class QueryConstants {

    /**
     * The default value of the "limit" parameter.
     */
    public static final int DEFAULT_LIMIT = 25;

    /**
     * The maximum value of the "limit" parameter.
     */
    public static final int MAX_LIMIT = 200;

    private QueryConstants() {
        throw new AssertionError();
    }

}
