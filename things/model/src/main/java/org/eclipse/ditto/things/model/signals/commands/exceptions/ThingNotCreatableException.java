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
package org.eclipse.ditto.things.model.signals.commands.exceptions;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingException;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Thrown if a Thing could not be created because a linked Policy ID was not existing for example.
 */
@Immutable
@JsonParsableException(errorCode = ThingNotCreatableException.ERROR_CODE)
public final class ThingNotCreatableException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "thing.notcreatable";

    static final String MESSAGE_TEMPLATE = "The Thing with ID ''{0}'' could not be created as the Policy with "
            + "ID ''{1}'' is not existing.";

    static final String MESSAGE_TEMPLATE_POLICY_CREATION_FAILURE =
            "The Thing with ID ''{0}'' could not be created because creation of its " +
                    "implicit Policy ID ''{1}'' failed.";

    static final String DEFAULT_DESCRIPTION_NOT_EXISTING =
            "Check if the ID of the Policy you created the Thing with is correct and that the Policy is existing.";

    static final String DEFAULT_DESCRIPTION_POLICY_CREATION_FAILED =
            "If you want to use an existing Policy, specify it as 'policyId' in the Thing JSON you create.";

    private static final String DEFAULT_DESCRIPTION_GENERIC =
            "Either check if the ID of the Policy you created the Thing with is correct and that the " +
                    "Policy is existing or If you want to use the existing Policy, specify it as 'policyId' " +
                    "in the Thing JSON you create.";

    static final String MESSAGE_WRONG_CHANNEL = "Thing could not be created via channel <live>.";

    static final String DEFAULT_DESCRIPTION_WRONG_CHANNEL = "Creating a thing via <live> channel is not supported." +
            " If you want to create a digital twin instead, please use channel <twin>.";

    private static final long serialVersionUID = 2153912949789822362L;

    private ThingNotCreatableException(final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, httpStatus, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ThingNotCreatableException} thrown if a Thing could not be created because a
     * referenced Policy in the Thing to be created is missing.
     *
     * @param thingId the ID of the Thing.
     * @param policyId the ID of the Policy which was used when creating the Thing.
     * @return the builder.
     */
    public static Builder newBuilderForPolicyMissing(final ThingId thingId, final PolicyId policyId) {
        return new Builder(thingId, policyId, true);
    }

    /**
     * A mutable builder for a {@code ThingNotCreatableException} thrown if a Thing could not be created because
     * the creation of its implicit Policy failed.
     *
     * @param thingId the ID of the Thing.
     * @param policyId the ID of the Policy which was used when creating the Thing.
     * @return the builder.
     */
    public static Builder newBuilderForPolicyExisting(final ThingId thingId, final PolicyId policyId) {
        return new Builder(thingId, policyId, false);
    }

    /**
     * Returns a new instance of {@code ThingNotCreatableException} that is caused by using the unsupported "live"
     * channel for sending a {@code CreateThing} command.
     *
     * @param dittoHeaders the headers of the command which resulted in the returned exception.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     * @since 2.3.0
     */
    public static ThingNotCreatableException forLiveChannel(final DittoHeaders dittoHeaders) {
        return new Builder()
                .httpStatus(HttpStatus.METHOD_NOT_ALLOWED)
                .dittoHeaders(dittoHeaders)
                .message(MESSAGE_WRONG_CHANNEL)
                .description(DEFAULT_DESCRIPTION_WRONG_CHANNEL)
                .build();
    }

    /**
     * Constructs a new {@code ThingNotCreatableException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param description the description which may be {@code null}.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingNotCreatableException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ThingNotCreatableException fromMessage(@Nullable final String message,
            @Nullable final String description,
            final DittoHeaders dittoHeaders) {

        final Builder builder = new Builder();
        builder.dittoHeaders(dittoHeaders);
        builder.message(message);

        if (null == description) {
            final String derivedDescription;
            if (MESSAGE_WRONG_CHANNEL.equals(message)) {
                derivedDescription = DEFAULT_DESCRIPTION_WRONG_CHANNEL;
            } else {
                derivedDescription = DEFAULT_DESCRIPTION_GENERIC;
            }
            builder.description(derivedDescription);
        } else {
            builder.description(description);
        }

        return builder.build();
    }

    /**
     * Constructs a new {@code ThingNotCreatableException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingNotCreatableException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ThingNotCreatableException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        ConditionChecker.checkNotNull(jsonObject, "jsonObject");
        ConditionChecker.checkNotNull(dittoHeaders, "dittoHeaders");

        final Builder builder = new Builder();
        builder.dittoHeaders(dittoHeaders);
        builder.message(jsonObject.getValueOrThrow(JsonFields.MESSAGE));
        jsonObject.getValue(JsonFields.STATUS).flatMap(HttpStatus::tryGetInstance).ifPresent(builder::httpStatus);
        jsonObject.getValue(JsonFields.DESCRIPTION).ifPresent(builder::description);
        jsonObject.getValue(JsonFields.HREF).map(URI::create).ifPresent(builder::href);

        return builder.build();
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new Builder()
                .httpStatus(getHttpStatus())
                .message(getMessage())
                .description(getDescription().orElse(null))
                .cause(getCause())
                .href(getHref().orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link ThingNotCreatableException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ThingNotCreatableException> {

        private HttpStatus httpStatus;

        private Builder() {
            httpStatus = HttpStatus.BAD_REQUEST;
            description(DEFAULT_DESCRIPTION_GENERIC);
        }

        private Builder(final boolean policyMissing) {
            httpStatus = HttpStatus.BAD_REQUEST;
            if (policyMissing) {
                description(DEFAULT_DESCRIPTION_NOT_EXISTING);
            } else {
                description(DEFAULT_DESCRIPTION_POLICY_CREATION_FAILED);
            }
        }

        private Builder(final ThingId thingId, final PolicyId policyId, final boolean policyMissing) {
            this(policyMissing);
            if (policyMissing) {
                message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(thingId), policyId));
            } else {
                message(MessageFormat.format(MESSAGE_TEMPLATE_POLICY_CREATION_FAILURE,
                        String.valueOf(thingId),
                        policyId));
            }
        }

        private Builder httpStatus(final HttpStatus httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        @Override
        protected ThingNotCreatableException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new ThingNotCreatableException(httpStatus, dittoHeaders, message, description, cause, href);
        }

    }

}
