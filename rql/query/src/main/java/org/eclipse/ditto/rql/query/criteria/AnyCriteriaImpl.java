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
package org.eclipse.ditto.rql.query.criteria;

import org.eclipse.ditto.rql.query.criteria.visitors.CriteriaVisitor;

/**
 * This Criteria matches any document.
 */
final class AnyCriteriaImpl implements Criteria {

    private static final AnyCriteriaImpl INSTANCE = new AnyCriteriaImpl();

    private AnyCriteriaImpl() {
    }

    /**
     * Gets the single instance of this class.
     *
     * @return the single instance.
     */
    public static AnyCriteriaImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public <T> T accept(final CriteriaVisitor<T> visitor) {
        return visitor.visitAny();
    }
}
