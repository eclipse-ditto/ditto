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
package org.eclipse.ditto.utils.jsr305.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;

/**
 * This annotation can be applied to a package, class or method to indicate that
 * all values should be treated as {@link Nonnull} by default. This applies to
 * <ul>
 *     <li>fields,</li>
 *     <li>method return values (constructors must not return {@code null} anyway),</li>
 *     <li>and method parameters.</li>
 * </ul>
 * <p>
 * NOTE: Package-level annotations are not passed to lower-level packages. Thus the annotation has to be provided for
 * each package to become effective.
 */
@Documented
@Nonnull
@TypeQualifierDefault( {ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllValuesAreNonnullByDefault {
    ElementType[] value() default {};
}
