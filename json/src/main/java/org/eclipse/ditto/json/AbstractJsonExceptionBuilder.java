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

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.text.MessageFormat;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Abstract base implementation for builders which create objects of subtypes of {@link JsonRuntimeException}.
 *
 * @param <T> type of the subclass of {@code JsonRuntimeException}.
 */
@NotThreadSafe
public abstract class AbstractJsonExceptionBuilder<T extends JsonException> implements JsonExceptionBuilder<T> {

    private final String errorCode;
    private String message;
    private String description;
    private Throwable cause;
    private URI href;

    /**
     * Constructs a new {@code AbstractJsonExceptionBuilder} object.
     *
     * @param errorCode a code which uniquely identifies the exception.
     * @throws NullPointerException if {@code errorCode} is {@code null}.
     * @throws IllegalArgumentException if {@code errorCode} is empty.
     */
    protected AbstractJsonExceptionBuilder(final String errorCode) {
        checkErrorCode(errorCode);
        this.errorCode = errorCode;
        message = null;
        description = null;
        cause = null;
        href = null;
    }

    private static void checkErrorCode(final String errorCode) {
        final String msgTemplate = "The error code of this exception must not be {0}!";
        requireNonNull(errorCode, MessageFormat.format(msgTemplate, "null"));
        if (errorCode.isEmpty()) {
            throw new IllegalArgumentException(MessageFormat.format(msgTemplate, "empty"));
        }
    }

    private static void checkSupplier(final Supplier<?> supplier) {
        requireNonNull(supplier, "The supplier must not be null!");
    }

    @Override
    public JsonExceptionBuilder<T> message(@Nullable final String message) {
        this.message = message;
        return this;
    }

    @Override
    public JsonExceptionBuilder<T> message(final Supplier<String> messageSupplier) {
        checkSupplier(messageSupplier);
        return message(messageSupplier.get());
    }

    @Override
    public JsonExceptionBuilder<T> description(@Nullable final String description) {
        this.description = description;
        return this;
    }

    @Override
    public JsonExceptionBuilder<T> description(final Supplier<String> descriptionSupplier) {
        checkSupplier(descriptionSupplier);
        return description(descriptionSupplier.get());
    }

    @Override
    public JsonExceptionBuilder<T> cause(@Nullable final Throwable cause) {
        this.cause = cause;
        return this;
    }

    @Override
    public JsonExceptionBuilder<T> cause(final Supplier<Throwable> causeSupplier) {
        checkSupplier(causeSupplier);
        return cause(causeSupplier.get());
    }

    @Override
    public JsonExceptionBuilder<T> href(@Nullable final URI href) {
        this.href = href;
        return this;
    }

    @Override
    public JsonExceptionBuilder<T> href(final Supplier<URI> hrefSupplier) {
        checkSupplier(hrefSupplier);
        return href(hrefSupplier.get());
    }

    @Override
    public T build() {
        return doBuild(errorCode, message, description, cause, href);
    }

    /**
     * This method must be implemented by subclasses. It is responsible for actually building the exception object.
     * Therefore it receives the data which was provided during the building process.
     *
     * @param errorCode the error code.
     * @param message the detail message or {@code null}.
     * @param description the description or {@code null}.
     * @param cause the cause or {@code null}.
     * @param href the link to further information or {@code null}.
     * @return a new exception of the target type.
     */
    protected abstract T doBuild(String errorCode, String message, String description, Throwable cause, URI href);

}
