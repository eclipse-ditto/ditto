/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.utils.jsr305.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;

/**
 * <p>
 * This annotation can be applied to a package, class or method to indicate that
 * all method return values and method parameters should be treated as {@link Nonnull} by default.
 * </p>
 * <p>Consider using {@link AllValuesAreNonnullByDefault} instead, because it also considers field values.</p>
 * <p>NOTE: Package-level annotations are not passed to lower-level packages.</p>
 *
 * @see AllValuesAreNonnullByDefault
 */
@Documented
@Nonnull
@TypeQualifierDefault({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllParametersAndReturnValuesAreNonnullByDefault {

    ElementType[] value() default {};
}
