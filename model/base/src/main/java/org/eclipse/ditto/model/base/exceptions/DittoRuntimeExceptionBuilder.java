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
package org.eclipse.ditto.model.base.exceptions;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields.DESCRIPTION;
import static org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields.HREF;
import static org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields.MESSAGE;

import java.net.URI;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Abstract base implementation of a mutable builder with a fluent API for a {@link DittoRuntimeException}.
 */
@NotThreadSafe
public abstract class DittoRuntimeExceptionBuilder<T extends DittoRuntimeException> {

    private DittoHeaders dittoHeaders = DittoHeaders.empty();
    private String message = null;
    private String description = null;
    private Throwable cause = null;
    private URI href = null;

    /**
     * Constructs a new {@code DittoRuntimeExceptionBuilder} object.
     */
    protected DittoRuntimeExceptionBuilder() {
        super();
    }

    private static void checkSupplier(final Supplier<?> supplier) {
        checkNotNull(supplier, "supplier");
    }

    /**
     * Sets the command headers with which the the exception to be built should be reported to the user.
     *
     * @param dittoHeaders the command headers to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public DittoRuntimeExceptionBuilder<T> dittoHeaders(final DittoHeaders dittoHeaders) {
        this.dittoHeaders = checkNotNull(dittoHeaders, "Ditto Headers");
        return this;
    }

    /**
     * Sets the detail message of the exception to be built.
     *
     * @param message the detail message to be set.
     * @return this builder to allow method chaining.
     */
    public DittoRuntimeExceptionBuilder<T> message(@Nullable final String message) {
        this.message = message;
        return this;
    }

    /**
     * Sets the detail message of the exception to be built.
     *
     * @param messageSupplier supplier of the message to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code messageSupplier} is {@code null}.
     */
    public DittoRuntimeExceptionBuilder<T> message(final Supplier<String> messageSupplier) {
        checkSupplier(messageSupplier);
        return message(messageSupplier.get());
    }

    /**
     * Sets a description with further information about the exception to be built.
     *
     * @param description the description to be set.
     * @return this builder to allow method chaining.
     */
    public DittoRuntimeExceptionBuilder<T> description(@Nullable final String description) {
        this.description = description;
        return this;
    }

    /**
     * Sets a description with further information about the exception to be built.
     *
     * @param descriptionSupplier supplier of the description to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code messageSupplier} is {@code null}.
     */
    public DittoRuntimeExceptionBuilder<T> description(final Supplier<String> descriptionSupplier) {
        checkSupplier(descriptionSupplier);
        return description(descriptionSupplier.get());
    }

    /**
     * Sets the cause which led to the exception to be built.
     *
     * @param cause the cause to be set.
     * @return this builder to allow method chaining.
     */
    public DittoRuntimeExceptionBuilder<T> cause(@Nullable final Throwable cause) {
        this.cause = cause;
        return this;
    }

    /**
     * Sets the cause which led to the exception to be built.
     *
     * @param causeSupplier supplier of the cause to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code messageSupplier} is {@code null}.
     */
    public DittoRuntimeExceptionBuilder<T> cause(final Supplier<Throwable> causeSupplier) {
        checkSupplier(causeSupplier);
        return cause(causeSupplier.get());
    }

    /**
     * Sets a link to a resource which provides further information about the exception to be built.
     *
     * @param href a link to further information.
     * @return this builder to allow method chaining.
     */
    public DittoRuntimeExceptionBuilder<T> href(@Nullable final URI href) {
        this.href = href;
        return this;
    }

    /**
     * Sets a link to a resource which provides further information about the exception to be built.
     *
     * @param hrefSupplier supplier of the link to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code messageSupplier} is {@code null}.
     */
    public DittoRuntimeExceptionBuilder<T> href(final Supplier<URI> hrefSupplier) {
        checkSupplier(hrefSupplier);
        return href(hrefSupplier.get());
    }

    /**
     * Builds an instance of the target exception type using the provided data.
     *
     * @return the new exception.
     */
    public T build() {
        return doBuild(dittoHeaders, message, description, cause, href);
    }

    /**
     * This method must be implemented by subclasses. It is responsible for actually building the exception object.
     * Therefore it receives the data which was provided during the building process.
     *
     * @param dittoHeaders the command headers with which this Exception should be reported back to the user.
     * @param message the detail message or {@code null}.
     * @param description the description or {@code null}.
     * @param cause the cause or {@code null}.
     * @param href the link to further information or {@code null}. @return a new exception of the target type.
     * @return the new exception.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    protected abstract T doBuild(final DittoHeaders dittoHeaders,
            @Nullable String message,
            @Nullable String description,
            @Nullable Throwable cause,
            @Nullable URI href);

    /**
     * Sets message, description and link from a JSON object of it has matching fields with valid values.
     *
     * @param jsonObject The JSON object to read from.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public DittoRuntimeExceptionBuilder<T> loadJson(final JsonObject jsonObject) {
        jsonObject.getValue(MESSAGE).ifPresent(this::message);
        jsonObject.getValue(DESCRIPTION).ifPresent(this::description);
        jsonObject.getValue(HREF).map(URI::create).ifPresent(this::href);
        return this;
    }

}
