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
package org.eclipse.ditto.model.query.criteria.visitors;

import java.util.List;

import org.eclipse.ditto.model.query.criteria.Predicate;

/**
 * Compositional interpreter of {@link Predicate}.
 */
public interface PredicateVisitor<T> {

    T visitEq(Object value);

    T visitGe(Object value);

    T visitGt(Object value);

    T visitIn(List<?> values);

    T visitLe(Object value);

    T visitLike(String value);

    T visitLt(Object value);

    T visitNe(Object value);

}
