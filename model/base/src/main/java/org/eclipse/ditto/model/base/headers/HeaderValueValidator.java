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
package org.eclipse.ditto.model.base.headers;

import java.util.function.BiConsumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;

/**
 * Checks if a specified CharSequence is a valid representation of a {@link HeaderDefinition}'s Java type. If a checked
 * value is invalid a {@link DittoHeaderInvalidException} is thrown. This class recognises a defined set of types
 * and provides validation for these. For all other types, the validation succeeds.
 *
 * @deprecated as of version 1.1.0 this class is deprecated and should not be used anymore.
 * Please use the provided validators of {@link HeaderValueValidators} instead or provide your own implementations of
 * {@link ValueValidator} resp. {@link AbstractHeaderValueValidator}.
 */
@Deprecated
@Immutable
public final class HeaderValueValidator implements BiConsumer<HeaderDefinition, CharSequence> {

    private static final HeaderValueValidator INSTANCE = new HeaderValueValidator();

    private HeaderValueValidator() {
        super();
    }

    /**
     * Returns an instance of {@code HeaderValueValidator}.
     *
     * @return the instance.
     */
    public static HeaderValueValidator getInstance() {
        return INSTANCE;
    }

    @Override
    public void accept(@Nonnull final HeaderDefinition definition, @Nullable final CharSequence charSequence) {
        final ValueValidator validatorChain = HeaderValueValidators.getIntValidator()
                .andThen(HeaderValueValidators.getLongValidator())
                .andThen(HeaderValueValidators.getBooleanValidator())
                .andThen(HeaderValueValidators.getJsonArrayValidator())
                .andThen(HeaderValueValidators.getEntityTagValidator())
                .andThen(HeaderValueValidators.getEntityTagMatchersValidator());

        validatorChain.accept(definition, charSequence);
    }

}
