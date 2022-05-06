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
package org.eclipse.ditto.internal.utils.health;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link StatusInfo}.
 */
public final class StatusInfoTest {

    private static StatusDetailMessage KNOWN_MESSAGE_ERROR =
            StatusDetailMessage.of(StatusDetailMessage.Level.ERROR, "errorMsg");
    private static StatusDetailMessage KNOWN_MESSAGE_WARN =
            StatusDetailMessage.of(StatusDetailMessage.Level.WARN, "warnMsg");
    private static StatusDetailMessage KNOWN_MESSAGE_INFO =
            StatusDetailMessage.of(StatusDetailMessage.Level.INFO, "infoMsg");

    private static List<StatusDetailMessage> KNOWN_DETAILS_WITHOUT_ERR =
            Arrays.asList(KNOWN_MESSAGE_INFO, KNOWN_MESSAGE_WARN);
    private static List<StatusDetailMessage> KNOWN_DETAILS_WITH_ERR =
            Arrays.asList(KNOWN_MESSAGE_INFO, KNOWN_MESSAGE_ERROR, KNOWN_MESSAGE_WARN);
    private static StatusInfo.Status KNOWN_STATUS = StatusInfo.Status.DOWN;

    private static final String KNOWN_STATUS_INFO_LABEL = "knownHealthStatus";
    private static StatusInfo KNOWN_STATUS_INFO =
            StatusInfo.of(KNOWN_STATUS, KNOWN_DETAILS_WITH_ERR, Collections.emptyList(), KNOWN_STATUS_INFO_LABEL);

    private static final String KNOWN_COMPOSITE_STATUS_INFO_LABEL = "knownCompositeHealthStatus";
    private static StatusInfo KNOWN_COMPOSITE_STATUS_INFO =
            StatusInfo.of(KNOWN_STATUS, KNOWN_DETAILS_WITH_ERR, Collections.singletonList(KNOWN_STATUS_INFO),
                    KNOWN_COMPOSITE_STATUS_INFO_LABEL);

    private static final List<StatusInfo> KNOWN_CHILDREN =
            Arrays.asList(KNOWN_STATUS_INFO, KNOWN_COMPOSITE_STATUS_INFO);
    private static StatusInfo KNOWN_COMPOSITE_COMPOSITE_STATUS_INFO =
            StatusInfo.of(KNOWN_STATUS, KNOWN_DETAILS_WITH_ERR, KNOWN_CHILDREN, null);

    private static StatusInfo KNOWN_UP_STATUS_INFO_WITH_WARN_MSG =
            StatusInfo.of(StatusInfo.Status.UP, KNOWN_DETAILS_WITHOUT_ERR, Collections.emptyList(), "labelUp");
    private static StatusInfo KNOWN_DOWN_STATUS_INFO_WITH_ERR_MSG =
            StatusInfo.of(StatusInfo.Status.DOWN, KNOWN_DETAILS_WITH_ERR, Collections.emptyList(), "labelDown");
    private static StatusInfo KNOWN_UNKNOWN_STATUS_INFO =
            StatusInfo.of(StatusInfo.Status.UNKNOWN, Collections.emptyList(), Collections.emptyList(),
                    "labelUnknown");

    private static JsonObject KNOWN_STATUS_INFO_JSON = JsonObject.newBuilder()
            .set(StatusInfo.JSON_KEY_LABEL, KNOWN_STATUS_INFO_LABEL)
            .set(StatusInfo.JSON_KEY_STATUS, KNOWN_STATUS.toString())
            .set(StatusInfo.JSON_KEY_DETAILS, toJsonArray(KNOWN_DETAILS_WITH_ERR, StatusDetailMessage::toJson))
            .build();

    private static JsonObject KNOWN_COMPOSITE_STATUS_INFO_JSON = JsonObject.newBuilder()
            .set(StatusInfo.JSON_KEY_LABEL, KNOWN_COMPOSITE_STATUS_INFO_LABEL)
            .set(StatusInfo.JSON_KEY_STATUS, KNOWN_STATUS.toString())
            .set(StatusInfo.JSON_KEY_DETAILS, toJsonArray(KNOWN_DETAILS_WITH_ERR, StatusDetailMessage::toJson))
            .set(StatusInfo.JSON_KEY_CHILDREN,
                    JsonFactory.newArrayBuilder(Collections.singleton(KNOWN_STATUS_INFO_JSON)).build())
            .build();

    private static JsonObject KNOWN_COMPOSITE_COMPOSITE_STATUS_INFO_JSON = JsonObject.newBuilder()
            .set(StatusInfo.JSON_KEY_STATUS, KNOWN_STATUS.toString())
            .set(StatusInfo.JSON_KEY_DETAILS, toJsonArray(KNOWN_DETAILS_WITH_ERR, StatusDetailMessage::toJson))
            .set(StatusInfo.JSON_KEY_CHILDREN,
                    JsonFactory.newArrayBuilder(Arrays.asList(KNOWN_STATUS_INFO_JSON,
                            KNOWN_COMPOSITE_STATUS_INFO_JSON)).build())
            .build();

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(StatusInfo.class, areImmutable(),
                provided(StatusInfo.class, StatusDetailMessage.class)
                        .areAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(StatusInfo.class)
                .withPrefabValues(StatusInfo.class, KNOWN_STATUS_INFO, KNOWN_COMPOSITE_COMPOSITE_STATUS_INFO)
                .verify();
    }


    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actualJson = KNOWN_COMPOSITE_COMPOSITE_STATUS_INFO.toJson();

        assertThat(actualJson).isEqualTo(KNOWN_COMPOSITE_COMPOSITE_STATUS_INFO_JSON);
    }

    @Test
    public void fromValidJsonString() {
        final StatusInfo actual = StatusInfo.fromJson(KNOWN_COMPOSITE_COMPOSITE_STATUS_INFO_JSON.toString());

        assertThat(actual).isEqualTo(KNOWN_COMPOSITE_COMPOSITE_STATUS_INFO);
    }

    @Test
    public void fromValidJsonObject() {
        final StatusInfo actual = StatusInfo.fromJson(KNOWN_COMPOSITE_COMPOSITE_STATUS_INFO_JSON);

        assertThat(actual).isEqualTo(KNOWN_COMPOSITE_COMPOSITE_STATUS_INFO);
    }

    @Test
    public void label() {
        final StatusInfo withoutLabel = StatusInfo.fromStatus(StatusInfo.Status.DOWN);
        assertThat(withoutLabel.getLabel()).isEmpty();

        final String knownLabel = "knownLabel";
        final StatusInfo actual = withoutLabel.label(knownLabel);

        final StatusInfo expected = StatusInfo.of(withoutLabel.getStatus(), withoutLabel.getDetails(),
                withoutLabel.getChildren(), knownLabel);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void labelMayBeSetToNull() {
        final String knownLabel = "knownLabel";
        final StatusInfo withLabel = StatusInfo.fromStatus(StatusInfo.Status.DOWN).label(knownLabel);
        assertThat(withLabel.getLabel()).hasValue(knownLabel);

        final StatusInfo actual = withLabel.label(null);

        final StatusInfo expected = StatusInfo.of(withLabel.getStatus(), withLabel.getDetails(),
                withLabel.getChildren(), null);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromDetailReturnsStatusDownWhenItIsAnErrorDetail() {
        final StatusInfo actual = StatusInfo.fromDetail(KNOWN_MESSAGE_ERROR);

        final StatusInfo expected =
                StatusInfo.of(StatusInfo.Status.DOWN, Collections.singletonList(KNOWN_MESSAGE_ERROR),
                        Collections.emptyList(), null);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromDetailReturnsStatusUpWhenItIsAWarnDetail() {
        final StatusInfo actual = StatusInfo.fromDetail(KNOWN_MESSAGE_WARN);

        final StatusInfo expected =
                StatusInfo.of(StatusInfo.Status.UP, Collections.singletonList(KNOWN_MESSAGE_WARN),
                        Collections.emptyList(), null);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromDetailReturnsStatusUpWhenItIsAnInfoDetail() {
        final StatusInfo actual = StatusInfo.fromDetail(KNOWN_MESSAGE_INFO);

        final StatusInfo expected =
                StatusInfo.of(StatusInfo.Status.UP, Collections.singletonList(KNOWN_MESSAGE_INFO),
                        Collections.emptyList(), null);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromDetailsReturnsStatusDownWhenThereIsAtLeastOneErrorDetail() {
        final StatusInfo actual = StatusInfo.fromDetails(KNOWN_DETAILS_WITH_ERR);

        final StatusInfo expected =
                StatusInfo.of(StatusInfo.Status.DOWN, KNOWN_DETAILS_WITH_ERR, Collections.emptyList(), null);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromDetailsReturnsStatusUpWhenThereIsNoErrorDetail() {
        final StatusInfo actual = StatusInfo.fromDetails(KNOWN_DETAILS_WITHOUT_ERR);

        final StatusInfo expected =
                StatusInfo.of(StatusInfo.Status.UP, KNOWN_DETAILS_WITHOUT_ERR, Collections.emptyList(), null);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromStatusCorrectlyMapsStatus() {
        final StatusInfo.Status status = StatusInfo.Status.UP;

        final StatusInfo actual = StatusInfo.fromStatus(status);

        final StatusInfo expected =
                StatusInfo.of(status, Collections.emptyList(), Collections.emptyList(), null);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromStatusWithMessageReturnsStatusUpWithInfoDetailWhenStatusIsUp() {
        final String infoMsg = "infoMsg";

        final StatusInfo actual = StatusInfo.fromStatus(StatusInfo.Status.UP, infoMsg);

        final StatusInfo expected =
                StatusInfo.of(StatusInfo.Status.UP,
                        Collections.singleton(StatusDetailMessage.of(StatusDetailMessage.Level.INFO, infoMsg)),
                        Collections.emptyList(), null);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromStatusWithMessageReturnsStatusDownWithErrorDetailWhenStatusIsDown() {
        final String errorMsg = "errorMsg";

        final StatusInfo actual = StatusInfo.fromStatus(StatusInfo.Status.DOWN, errorMsg);

        final StatusInfo expected =
                StatusInfo.of(StatusInfo.Status.DOWN,
                        Collections.singleton(StatusDetailMessage.of(StatusDetailMessage.Level.ERROR, errorMsg)),
                        Collections.emptyList(), null);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromStatusWithMessageReturnsStatusUnknownWithInfoDetailWhenStatusIsUnknown() {
        final String infoMsg = "infoMsg";

        final StatusInfo actual = StatusInfo.fromStatus(StatusInfo.Status.UNKNOWN, infoMsg);

        final StatusInfo expected =
                StatusInfo.of(StatusInfo.Status.UNKNOWN,
                        Collections.singleton(StatusDetailMessage.of(StatusDetailMessage.Level.INFO, infoMsg)),
                        Collections.emptyList(), null);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void unknownStatusReturnsStatusUnknown() {
        final StatusInfo actual = StatusInfo.unknown();

        final StatusInfo expected = StatusInfo.fromStatus(StatusInfo.Status.UNKNOWN);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void compositeReturnsStatusUpWhenAllChildrenAreUp() {
        final Map<String, StatusInfo> childrenMap = new LinkedHashMap<>();
        final String upChild1Label = "up-child-1-label";
        childrenMap.put(upChild1Label, KNOWN_UP_STATUS_INFO_WITH_WARN_MSG);
        final String upChild2Label = "up-child-2-label";
        childrenMap.put(upChild2Label, KNOWN_UP_STATUS_INFO_WITH_WARN_MSG);

        final StatusInfo actual = StatusInfo.composite(childrenMap);

        final List<StatusDetailMessage> expectedDetails = Collections.singletonList(
                (createExpectedCompositeDetailMessage(KNOWN_MESSAGE_WARN.getLevel(),
                        Arrays.asList(upChild1Label, upChild2Label))));
        final List<StatusInfo> expectedLabeledChildren =
                Arrays.asList(KNOWN_UP_STATUS_INFO_WITH_WARN_MSG.label(upChild1Label),
                        KNOWN_UP_STATUS_INFO_WITH_WARN_MSG.label(upChild2Label));
        final StatusInfo expected =
                StatusInfo.of(StatusInfo.Status.UP, expectedDetails, expectedLabeledChildren, null);
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.isComposite()).isTrue();
    }

    @Test
    public void compositeReturnsStatusUpWhenChildrenAreEmpty() {
        final StatusInfo actual = StatusInfo.composite(Collections.emptyMap());

        final StatusInfo expected =
                StatusInfo.of(StatusInfo.Status.UP, Collections.emptyList(), Collections.emptyList(), null);
        assertThat(actual).isEqualTo(expected);
        // a composite without children is not really a composite:
        assertThat(actual.isComposite()).isFalse();
    }

    @Test
    public void compositeReturnsStatusUpWhenAllChildrenAreUpAndOneIsUnknown() {
        final Map<String, StatusInfo> childrenMap = new LinkedHashMap<>();
        final String upChildLabel = "up-child-label1";
        childrenMap.put(upChildLabel, KNOWN_UP_STATUS_INFO_WITH_WARN_MSG);
        final String unknownChildLabel = "unknown-child-label";
        childrenMap.put(unknownChildLabel, KNOWN_UNKNOWN_STATUS_INFO);

        final StatusInfo actual = StatusInfo.composite(childrenMap);

        final List<StatusDetailMessage> expectedDetails = Collections.singletonList(
                (createExpectedCompositeDetailMessage(KNOWN_MESSAGE_WARN.getLevel(),
                        Collections.singletonList(upChildLabel))));
        final List<StatusInfo> expectedLabeledChildren =
                Arrays.asList(KNOWN_UP_STATUS_INFO_WITH_WARN_MSG.label(upChildLabel),
                        KNOWN_UNKNOWN_STATUS_INFO.label(unknownChildLabel));
        final StatusInfo expected =
                StatusInfo.of(StatusInfo.Status.UP, expectedDetails, expectedLabeledChildren, null);
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.isComposite()).isTrue();
    }

    @Test
    public void compositeReturnsStatusDownWhenAtLeastOneChildIsDown() {
        final Map<String, StatusInfo> children = new LinkedHashMap<>();
        final String downChild1Label = "down-child-1-label";
        children.put(downChild1Label, KNOWN_DOWN_STATUS_INFO_WITH_ERR_MSG);
        final String upChildLabel = "up-child-label";
        children.put(upChildLabel, KNOWN_UP_STATUS_INFO_WITH_WARN_MSG);
        final String downChild2Label = "down-child-2-label";
        children.put(downChild2Label, KNOWN_DOWN_STATUS_INFO_WITH_ERR_MSG);

        final StatusInfo actual = StatusInfo.composite(children);

        final List<StatusDetailMessage> expectedDetails = Arrays.asList(
                (createExpectedCompositeDetailMessage(KNOWN_MESSAGE_ERROR.getLevel(),
                        Arrays.asList(downChild1Label, downChild2Label))),
                createExpectedCompositeDetailMessage
                        (KNOWN_MESSAGE_WARN.getLevel(),
                        Arrays.asList(downChild1Label, upChildLabel, downChild2Label)));
        final List<StatusInfo> expectedLabeledChildren =
                Arrays.asList(KNOWN_DOWN_STATUS_INFO_WITH_ERR_MSG.label(downChild1Label),
                        KNOWN_UP_STATUS_INFO_WITH_WARN_MSG.label(upChildLabel),
                        KNOWN_DOWN_STATUS_INFO_WITH_ERR_MSG.label(downChild2Label));
        final StatusInfo expected =
                StatusInfo.of(StatusInfo.Status.DOWN, expectedDetails, expectedLabeledChildren, null);
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.isComposite()).isTrue();
    }

    @Test
    public void mergeStatus() {
        assertMergeStatuses(StatusInfo.Status.UP, StatusInfo.Status.UP, StatusInfo.Status.UP);
        assertMergeStatuses(StatusInfo.Status.UP, StatusInfo.Status.UNKNOWN, StatusInfo.Status.UP);
        assertMergeStatuses(StatusInfo.Status.UP, StatusInfo.Status.DOWN, StatusInfo.Status.DOWN);

        assertMergeStatuses(StatusInfo.Status.UNKNOWN, StatusInfo.Status.UP, StatusInfo.Status.UP);
        assertMergeStatuses(StatusInfo.Status.UNKNOWN, StatusInfo.Status.UNKNOWN, StatusInfo.Status.UNKNOWN);
        assertMergeStatuses(StatusInfo.Status.UNKNOWN, StatusInfo.Status.DOWN, StatusInfo.Status.DOWN);

        assertMergeStatuses(StatusInfo.Status.DOWN, StatusInfo.Status.UP, StatusInfo.Status.DOWN);
        assertMergeStatuses(StatusInfo.Status.DOWN, StatusInfo.Status.UNKNOWN, StatusInfo.Status.DOWN);
        assertMergeStatuses(StatusInfo.Status.DOWN, StatusInfo.Status.DOWN, StatusInfo.Status.DOWN);
    }

    private void assertMergeStatuses(final StatusInfo.Status status1, final StatusInfo.Status status2,
            final StatusInfo.Status expectedMergedStatus) {
        assertThat(status1.mergeWith(status2)).isEqualTo(expectedMergedStatus);
    }

    private static StatusDetailMessage createExpectedCompositeDetailMessage(
            final StatusDetailMessage.Level level,
            final Collection<String> locations) {
        return StatusDetailMessage.of(level, "See detailed messages for: " +
                String.join(", ", locations) + "" + ".");
    }

    private static <T> JsonArray toJsonArray(final List<T> list, final Function<T, JsonValue> objectMapper) {
        return JsonArray.newBuilder()
                .addAll(list.stream()
                        .map(objectMapper)
                        .toList())
                .build();
    }
}
