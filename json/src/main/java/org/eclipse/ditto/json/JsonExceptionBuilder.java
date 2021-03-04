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
package org.eclipse.ditto.json;

import java.net.URI;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/**
 * A mutable builder for a {@link JsonException}.
 *
 * @param <T> type of the exception this builder builds.
 */
public interface JsonExceptionBuilder<T extends JsonException> {

    /**
     * Sets the detail message of the exception to be built.
     *
     * @param message the detail message.
     * @return this builder to allow method chaining.
     */
    JsonExceptionBuilder<T> message(@Nullable String message);

    /**
     * Sets the detail message of the exception to be built.
     *
     * @param messageSupplier supplier of the message to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code messageSupplier} is {@code null}.
     */
    JsonExceptionBuilder<T> message(Supplier<String> messageSupplier);

    /**
     * Sets a description with further information about the exception to be built.
     *
     * @param description a description.
     * @return this builder to allow method chaining.
     */
    JsonExceptionBuilder<T> description(@Nullable String description);

    /**
     * Sets a description with further information about the exception to be built.
     *
     * @param descriptionSupplier supplier of the description to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code messageSupplier} is {@code null}.
     */
    JsonExceptionBuilder<T> description(Supplier<String> descriptionSupplier);

    /**
     * Sets the cause which led to the exception to be built.
     *
     * @param cause the cause.
     * @return this builder to allow method chaining.
     */
    JsonExceptionBuilder<T> cause(@Nullable Throwable cause);

    /**
     * Sets the cause which led to the exception to be built.
     *
     * @param causeSupplier supplier of the cause to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code messageSupplier} is {@code null}.
     */
    JsonExceptionBuilder<T> cause(Supplier<Throwable> causeSupplier);

    /**
     * Sets a link to a resource which provides further information about the exception to be built.
     *
     * @param href a link to further information.
     * @return this builder to allow method chaining.
     */
    JsonExceptionBuilder<T> href(@Nullable URI href);

    /**
     * Sets a link to a resource which provides further information about the exception to be built.
     *
     * @param hrefSupplier supplier of the link to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code messageSupplier} is {@code null}.
     */
    JsonExceptionBuilder<T> href(Supplier<URI> hrefSupplier);

    /**
     * Builds an instance of the target exception type using the provided data.
     *
     * @return a new exception of the target type.
     */
    T build();

}
