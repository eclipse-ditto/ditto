/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.json;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.atteo.classindex.IndexAnnotated;

/**
 * This annotated marks a class as deserializable from Json when calling the specified
 * {@link org.eclipse.ditto.base.model.json.JsonParsableCommand#method()} with {@link org.eclipse.ditto.json.JsonObject} as first
 * and {@link org.eclipse.ditto.base.model.headers.DittoHeaders} as second argument.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@IndexAnnotated
public @interface JsonParsableCommandResponse {

    /**
     * Used as identifier of the CommandResponse.
     *
     * @return the type.
     */
    String type();

    /**
     * The name of the method accepting a {@link org.eclipse.ditto.json.JsonObject} as first argument and
     * {@link org.eclipse.ditto.base.model.headers.DittoHeaders} as seconds argument.
     * The Method must return an instance of the CommandResponse annotated with this annotation.
     *
     * @return the name of this method.
     */
    String method() default "fromJson";
}
