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
package org.eclipse.ditto.services.thingsearch.common.model;

import java.util.List;

/**
 * ResultList defines the offset of the next page in addition to the standard list operations.
 *
 * @param <E> the type of the items
 */
public interface ResultList<E> extends List<E> {

    /**
     * Signals that there is no next page.
     */
    long NO_NEXT_PAGE = -1;

    /**
     * Gets the offset of the next page or {@link ResultList#NO_NEXT_PAGE}.
     *
     * @return the offset of the next page
     */
    long nextPageOffset();
}
