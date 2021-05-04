/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers;

import javax.annotation.Nullable;

/**
 * A validator for a header value.
 * This interface allows to use a sole ValueValidator or to chain more validators as a
 * <a href="https://en.wikipedia.org/wiki/Chain-of-responsibility_pattern">Chain-of-responsibility</a>
 * (see {@link #andThen(org.eclipse.ditto.base.model.headers.ValueValidator)}.
 *
 * @since 1.1.0
 */
public interface ValueValidator {

    /**
     * Validates the given value if this validator is responsible regarding the given definition.
     *
     * @param definition the definition which determines if this validator is responsible.
     * @param value the value to be validated.
     * @throws NullPointerException if {@code definition} is {@code null}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException if the value is invalid.
     */
    void accept(HeaderDefinition definition, @Nullable CharSequence value);

    /**
     * Indicates whether this validator can validate values of the given type.
     *
     * @param valueType the type in question.
     * @return {@code true} if this validator can validate values of {@code valueType}, {@code false} else or if
     * {@code valueType} is {@code null}.
     */
    boolean canValidate(@Nullable Class<?> valueType);

    /**
     * Returns a composed validator that first applies this validator to its input, and then applies the given
     * validator.
     * If any validator throws an exception, it is relayed to the caller of the composed validator.
     *
     * @param after the validator to be applied after this validator is applied.
     * @return the composed validator.
     * @throws NullPointerException if {@code after} is {@code null}.
     */
    ValueValidator andThen(ValueValidator after);

}
