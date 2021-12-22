/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.mappingstrategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.net.URI;
import java.net.URISyntaxException;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link IllegalAdaptableException}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class IllegalAdaptableExceptionTest {

    private static final String MESSAGE = "Omnis reiciendis tenetur non et.";
    private static final String DESCRIPTION =
            "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat pariatur.";
    private static final String SIGNAL_TYPE = "things.responses:modifyFeature";

    private static URI href;

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    private Adaptable adaptable;

    private DittoHeaders dittoHeaders;

    @BeforeClass
    public static void beforeClass() throws URISyntaxException {
        href = new URI("https://eclipse.org/ditto");
    }

    @Before
    public void before() {
        dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(String.valueOf(testNameCorrelationId.getCorrelationId()))
                .build();

        final TopicPath topicPath =
                TopicPath.newBuilder(ThingId.generateRandom()).live().things().commands().modify().build();
        adaptable = ProtocolFactory.newAdaptableBuilder(topicPath)
                .withHeaders(dittoHeaders)
                .withPayload(Payload.newBuilder()
                        .withPath(JsonPointer.of("/features/thermostat"))
                        .withValue(JsonValue.of(23.42D))
                        .withStatus(HttpStatus.IM_A_TEAPOT)
                        .build())
                .build();
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(IllegalAdaptableException.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .suppress(Warning.NONFINAL_FIELDS, Warning.TRANSIENT_FIELDS, Warning.NULL_FIELDS)
                .withIgnoredFields("cause", "stackTrace", "suppressedExceptions")
                .verify();
    }

    @Test
    public void newInstanceWithNullMessageThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> IllegalAdaptableException.newInstance(null, adaptable))
                .withMessage("The message must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithBlankMessageThrowsException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> IllegalAdaptableException.newInstance(" ", adaptable))
                .withMessage("The message must not be blank.")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullAdaptableThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> IllegalAdaptableException.newInstance(MESSAGE, null))
                .withMessage("The adaptable must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceReturnsNotNull() {
        final IllegalAdaptableException illegalAdaptableException =
                IllegalAdaptableException.newInstance(MESSAGE, adaptable);

        assertThat(illegalAdaptableException).isNotNull();
    }

    @Test
    public void getMessageReturnsExpected() {
        final String message = MESSAGE;
        final IllegalAdaptableException underTest = IllegalAdaptableException.newInstance(message, adaptable);

        assertThat(underTest.getMessage()).isEqualTo(message);
    }

    @Test
    public void getTopicPathReturnsExpected() {
        final IllegalAdaptableException underTest = IllegalAdaptableException.newInstance(MESSAGE, adaptable);

        assertThat(underTest.getTopicPath()).isEqualTo(adaptable.getTopicPath());
    }

    @Test
    public void newInstanceWithoutDescriptionReturnsDefaultDescription() {
        final IllegalAdaptableException underTest = IllegalAdaptableException.newInstance(MESSAGE, adaptable);

        assertThat(underTest.getDescription()).contains(IllegalAdaptableException.DEFAULT_DESCRIPTION);
    }

    @Test
    public void newInstanceWithDescriptionReturnsExpected() {
        final String description = DESCRIPTION;
        final IllegalAdaptableException underTest =
                IllegalAdaptableException.newInstance(MESSAGE, description, adaptable);

        assertThat(underTest.getDescription()).contains(description);
    }

    @Test
    public void newInstanceViaBuilderReturnsExpected() {
        final String description = "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia" +
                " deserunt mollit anim id est laborum.";
        final JsonParseException cause = new JsonParseException("JSON object could not be parsed.");
        final IllegalAdaptableException underTest = IllegalAdaptableException.newBuilder(MESSAGE, adaptable)
                .withDescription(description)
                .withSignalType(SIGNAL_TYPE)
                .withHref(href)
                .withCause(cause)
                .build();

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(underTest.getErrorCode())
                    .as("error code")
                    .isEqualTo(IllegalAdaptableException.ERROR_CODE);
            softly.assertThat(underTest.getHttpStatus())
                    .as("HTTP status")
                    .isEqualTo(IllegalAdaptableException.HTTP_STATUS);
            softly.assertThat(underTest.getMessage()).as("message").isEqualTo(MESSAGE);
            softly.assertThat(underTest.getTopicPath()).as("topic path").isEqualTo(adaptable.getTopicPath());
            softly.assertThat(underTest.getDittoHeaders()).as("Ditto headers").isEqualTo(dittoHeaders);
            softly.assertThat(underTest.getSignalType()).as("signal type").contains(SIGNAL_TYPE);
            softly.assertThat(underTest.getDescription()).as("description").contains(description);
            softly.assertThat(underTest.getHref()).as("href").contains(href);
            softly.assertThat(underTest.getCause()).as("cause").isEqualTo(cause);
        }
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonParseException cause = new JsonParseException("JSON object could not be parsed.");
        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(DittoRuntimeException.JsonFields.ERROR_CODE, IllegalAdaptableException.ERROR_CODE)
                .set(DittoRuntimeException.JsonFields.STATUS, IllegalAdaptableException.HTTP_STATUS.getCode())
                .set(DittoRuntimeException.JsonFields.MESSAGE, MESSAGE)
                .set(DittoRuntimeException.JsonFields.DESCRIPTION, DESCRIPTION)
                .set(DittoRuntimeException.JsonFields.HREF, href.toString())
                .set(IllegalAdaptableException.JSON_FIELD_TOPIC_PATH, adaptable.getTopicPath().getPath())
                .set(IllegalAdaptableException.JSON_FIELD_SIGNAL_TYPE, SIGNAL_TYPE)
                .build();
        final IllegalAdaptableException underTest = IllegalAdaptableException.newBuilder(MESSAGE, adaptable)
                .withDescription(DESCRIPTION)
                .withSignalType(SIGNAL_TYPE)
                .withHref(href)
                .withCause(cause)
                .build();

        assertThat(underTest.toJson()).isEqualTo(jsonObject);
    }

    @Test
    public void fromValidJsonReturnsExpected() {
        final IllegalAdaptableException illegalAdaptableException =
                IllegalAdaptableException.newBuilder(MESSAGE, adaptable)
                        .withDescription(DESCRIPTION)
                        .withSignalType(SIGNAL_TYPE)
                        .withHref(href)
                        .build();

        final IllegalAdaptableException underTest =
                IllegalAdaptableException.fromJson(illegalAdaptableException.toJson(), adaptable.getDittoHeaders());

        assertThat(underTest).isEqualTo(illegalAdaptableException);
    }

}