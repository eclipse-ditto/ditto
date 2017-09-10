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
package org.eclipse.ditto.signals.commands.things.exceptions;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingException;

/**
 * Thrown if a Thing could not be created because a linked Policy ID was not existing for example.
 */
@Immutable
public final class ThingNotCreatableException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "thing.notcreatable";

    private static final String MESSAGE_TEMPLATE = "The Thing with ID ''{0}'' could not be created as the Policy with "
            + "ID ''{1}'' is not existing.";

    private static final String MESSAGE_TEMPLATE_POLICY_EXISTING =
            "The Thing with ID ''{0}'' could not be created with " +
                    "implicit Policy as the Policy with ID ''{1}'' is already existing.";

    private static final String DEFAULT_DESCRIPTION =
            "Check if the ID of the Policy you created the Thing with is correct and that the Policy is existing.";

    private static final String DEFAULT_DESCRIPTION_POLICY_EXISTING =
            "If you want to use the existing Policy, specify it as 'policyId' in the Thing JSON you create.";

    private static final long serialVersionUID = 2153912949789822362L;

    private ThingNotCreatableException(final DittoHeaders dittoHeaders, final String message,
            final String description, final Throwable cause, final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ThingNotCreatableException} thrown if a Thing could not be created because a
     * referenced Policy in the Thing to be created is missing.
     *
     * @param thingId the ID of the Thing.
     * @param policyId the ID of the Policy which was used when creating the Thing.
     * @return the builder.
     */
    public static Builder newBuilderForPolicyMissing(final String thingId, final String policyId) {
        return new Builder(thingId, policyId, true);
    }

    /**
     * A mutable builder for a {@code ThingNotCreatableException} thrown if a Thing could not be created because an
     * implicitly created Policy would collide with an already existing Policy with such an ID.
     *
     * @param thingId the ID of the Thing.
     * @param policyId the ID of the Policy which was used when creating the Thing.
     * @return the builder.
     */
    public static Builder newBuilderForPolicyExisting(final String thingId, final String policyId) {
        return new Builder(thingId, policyId, false);
    }

    /**
     * Constructs a new {@code ThingNotCreatableException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param description the description which may be {@code null}.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new
     * ThingNotCreatableException.
     */
    public static ThingNotCreatableException fromMessage(final String message, @Nullable final String description,
            final DittoHeaders dittoHeaders) {

        final DittoRuntimeExceptionBuilder<ThingNotCreatableException> exceptionBuilder = new Builder(true)
                .dittoHeaders(dittoHeaders)
                .message(message);
        if (description != null) {
            return exceptionBuilder.description(description).build();
        } else {
            return exceptionBuilder.build();
        }
    }

    /**
     * Constructs a new {@code ThingNotCreatableException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingNotCreatableException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static ThingNotCreatableException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), readDescription(jsonObject).orElse(null), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link ThingNotCreatableException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ThingNotCreatableException> {

        private Builder(final boolean policyMissing) {
            if (policyMissing) {
                description(DEFAULT_DESCRIPTION);
            } else {
                description(DEFAULT_DESCRIPTION_POLICY_EXISTING);
            }
        }

        private Builder(final String thingId, final String policyId, final boolean policyMissing) {
            this(policyMissing);
            if (policyMissing) {
                message(MessageFormat.format(MESSAGE_TEMPLATE, thingId, policyId));
            } else {
                message(MessageFormat.format(MESSAGE_TEMPLATE_POLICY_EXISTING, thingId, policyId));
            }
        }

        @Override
        protected ThingNotCreatableException doBuild(final DittoHeaders dittoHeaders, final String message,
                final String description, final Throwable cause, final URI href) {
            return new ThingNotCreatableException(dittoHeaders, message, description, cause, href);
        }
    }

}
