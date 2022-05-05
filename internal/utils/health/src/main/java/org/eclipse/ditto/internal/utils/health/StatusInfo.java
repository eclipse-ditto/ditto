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

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains the {@code status} and a {@code detail} in case the Status was not {@link StatusInfo.Status#UP} of a
 * single health "entry".
 */
@Immutable
public final class StatusInfo implements Jsonifiable<JsonObject> {

    /**
     * An enumeration of status codes for the health status in the style of spring boot actuator.
     */
    public enum Status {
        /**
         * Signals the application is in an unknown state.
         */
        UNKNOWN,

        /**
         * Signals the application is up and running.
         */
        UP,

        /**
         * Signals the application is down.
         */
        DOWN;

        /**
         * Merges this status with another status.
         *
         * @param otherStatus the other status to merge this status with.
         * @return the merged status.
         */
        public Status mergeWith(final Status otherStatus) {
            return mergeStatuses(this, otherStatus);
        }

        private static Status mergeStatuses(final Status firstStatus, final Status secondStatus) {
            requireNonNull(firstStatus);
            requireNonNull(secondStatus);

            if (firstStatus == Status.UP && secondStatus == Status.UP) {
                return Status.UP;
            }

            if (firstStatus == Status.UNKNOWN && secondStatus == Status.UNKNOWN) {
                return Status.UNKNOWN;
            }

            if ((firstStatus == Status.UP && secondStatus == Status.UNKNOWN) ||
                    (secondStatus == Status.UP && firstStatus == Status.UNKNOWN)) {
                return Status.UP;
            }

            return Status.DOWN;
        }
    }

    /**
     * JSON field of the label.
     */
    public static final JsonFieldDefinition<String> JSON_KEY_LABEL =
            JsonFactory.newStringFieldDefinition("label");

    /**
     * JSON field of the status.
     */
    public static final JsonFieldDefinition<String> JSON_KEY_STATUS =
            JsonFactory.newStringFieldDefinition("status");

    /**
     * JSON field of the detailed status messages.
     */
    public static final JsonFieldDefinition<JsonArray> JSON_KEY_DETAILS =
            JsonFactory.newJsonArrayFieldDefinition("details");

    /**
     * JSON field of the children (sub-statuses).
     */
    public static final JsonFieldDefinition<JsonArray> JSON_KEY_CHILDREN =
            JsonFactory.newJsonArrayFieldDefinition("children");

    private static final Set<StatusDetailMessage.Level> COMPOSITE_DETAILS_INCLUDE_LEVELS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(StatusDetailMessage.Level.ERROR,
                    StatusDetailMessage.Level.WARN)));

    /**
     * The default empty status: This defines the state of a composite without children.
     */
    private static final Status DEFAULT_EMPTY_STATUS = Status.UP;

    private static final StatusInfo UNKNOWN_INSTANCE = fromStatus(Status.UNKNOWN);

    private static final Logger LOGGER = LoggerFactory.getLogger(StatusInfo.class);

    private final Status status;
    private final List<StatusDetailMessage> details;
    private final List<StatusInfo> children;
    @Nullable
    private final String label;

    private StatusInfo(final Status status, final Collection<StatusDetailMessage> details,
            final Collection<StatusInfo> children, @Nullable final String label) {
        this.status = status;
        this.details = Collections.unmodifiableList(new ArrayList<>(details));
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
        this.label = label;
    }

    static StatusInfo of(final Status status, final Collection<StatusDetailMessage> details,
            final Collection<StatusInfo> children, @Nullable final String label) {
        return new StatusInfo(status, details, children, label);
    }

    /**
     * Returns a {@code StatusInfo} with {@code status} {@link Status#UNKNOWN}.
     *
     * @return the StatusInfo instance.
     */
    public static StatusInfo unknown() {
        return UNKNOWN_INSTANCE;
    }

    /**
     * Returns a new {@code StatusInfo} instance with the specified {@code status}.
     *
     * @param status the status.
     * @return the StatusInfo instance.
     */
    public static StatusInfo fromStatus(final Status status) {
        return fromStatus(status, Collections.emptyList());
    }

    /**
     * Returns a new {@code StatusInfo} instance with the specified {@code status} and {@code message}.
     *
     * @param status the status.
     * @param message the message.
     * @return the StatusInfo instance.
     */
    public static StatusInfo fromStatus(final Status status, @Nullable final String message) {
        requireNonNull(status, "The Status must not be null!");

        return mapToStatusInfo(status, message);
    }

    /**
     * Returns a new {@code StatusInfo} instance with the specified {@code status} and {@code details}.
     *
     * @param status the status.
     * @param details the details.
     * @return the StatusInfo instance.
     */
    public static StatusInfo fromStatus(final Status status, final Collection<StatusDetailMessage> details) {
        requireNonNull(status, "The Status must not be null!");
        requireNonNull(details, "The Details must not be null!");

        return of(status, details, Collections.emptyList(), null);
    }

    /**
     * Returns a new {@code StatusInfo} instance with the specified {@code detail}. The status will be computed.
     *
     * @param detail the detail.
     * @return the StatusInfo instance.
     */
    public static StatusInfo fromDetail(final StatusDetailMessage detail) {
        requireNonNull(detail, "The Detail must not be null!");

        final Status status = mapToStatusInfo(detail);
        return of(status, Collections.singletonList(detail), Collections.emptyList(), null);
    }

    /**
     * Returns a new {@code StatusInfo} instance with the specified {@code details}. The status will be computed.
     *
     * @param details the detail.
     * @return the StatusInfo instance.
     */
    public static StatusInfo fromDetails(final Collection<StatusDetailMessage> details) {
        requireNonNull(details, "The Details must not be null!");

        final Status status = mapToStatusInfo(details);
        return of(status, details, Collections.emptyList(), null);
    }

    /**
     * Returns a new {@code StatusInfo} instance with the specified {@code children}. The composite status will be
     * computed based on the children's statuses.
     *
     * @param children the children.
     * @return the StatusInfo instance.
     */
    public static StatusInfo composite(final List<StatusInfo> children) {
        requireNonNull(children, "The Children must not be null!");

        return createCompositeStatusInfo(children);
    }

    /**
     * Returns a new {@code StatusInfo} instance with the specified {@code labeledChildren}. The map's key will label
     * the {@link StatusInfo} values. The composite status will be computed based on the children's statuses.
     *
     * @param labeledChildren the children.
     * @return the StatusInfo instance.
     */
    public static StatusInfo composite(final Map<String, StatusInfo> labeledChildren) {
        requireNonNull(labeledChildren, "The Children must not be null!");

        final List<StatusInfo> labeledChildrenList = labelStatuses(labeledChildren);
        return createCompositeStatusInfo(labeledChildrenList);
    }

    /**
     * Creates a new {@link StatusInfo} from a JSON string.
     *
     * @param jsonString the JSON string.
     * @return the created instance.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} does not contain a JSON
     * object or if it is not valid JSON.
     * @throws JsonMissingFieldException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static StatusInfo fromJson(final String jsonString) {
        return fromJson(JsonFactory.newObject(jsonString));
    }

    /**
     * Creates a new {@link StatusInfo} from a JSON object.
     *
     * @param jsonObject the JSON object.
     * @return the created instance.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws JsonMissingFieldException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static StatusInfo fromJson(final JsonObject jsonObject) {
        final String label = jsonObject.getValue(JSON_KEY_LABEL).orElse(null);
        final Status status = Status.valueOf(jsonObject.getValueOrThrow(JSON_KEY_STATUS));

        final JsonArray detailsArray = jsonObject.getValue(JSON_KEY_DETAILS).orElse(JsonFactory.newArray());
        final List<StatusDetailMessage> details = detailsArray.stream()
                .map(JsonValue::asObject)
                .map(StatusDetailMessage::fromJson)
                .toList();

        final JsonArray childrenArray = jsonObject.getValue(JSON_KEY_CHILDREN).orElse(JsonFactory.newArray());
        final List<StatusInfo> children = childrenArray.stream()
                .map(JsonValue::asObject)
                .map(StatusInfo::fromJson)
                .toList();

        return of(status, details, children, label);
    }

    /**
     * Returns the {@code Status}.
     *
     * @return the status.
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Returns the details.
     *
     * @return the details, which may be empty.
     */
    public List<StatusDetailMessage> getDetails() {
        return details;
    }

    /**
     * Returns the children.
     *
     * @return the children, which may be empty.
     * @see #isComposite()
     */
    public List<StatusInfo> getChildren() {
        return children;
    }

    /**
     * Returns the (optional) label.
     *
     * @return the (optional) label.
     */
    public Optional<String> getLabel() {
        return Optional.ofNullable(label);
    }

    /**
     * Creates a copy of this instance with the given {@code label} as label.
     * @param label the label, may be {@code null}.
     *
     * @return the labeled copy.
     */
    public StatusInfo label(@Nullable final String label) {
        return of(status, details, children, label);
    }

    /**
     * Returns whether this instance is a composite, i.e. whether it has children.
     *
     * @return whether this instance is a composite.
     * @see #getChildren()
     */
    public boolean isComposite() {
        return !children.isEmpty();
    }

    /**
     * Creates a new {@link StatusInfo} from this {@code StatusInfo}, extended with the specified {@code detail}. Note
     * that adding an error detail might change the {@link StatusInfo}'s status.
     *
     * @param detail the detail.
     * @return the StatusInfo instance.
     */
    public StatusInfo addDetail(final StatusDetailMessage detail) {
        requireNonNull(detail, "The Detail must not be null!");

        final List<StatusDetailMessage> newDetails = new ArrayList<>(details.size() + 1);
        newDetails.addAll(details);
        newDetails.add(detail);

        final Status newStatus = mapToStatusInfo(newDetails);

        return of(newStatus, newDetails, children, label);
    }

    /**
     * Returns whether this status is considered as healthy.
     *
     * @return {@code true}, when healthy; {@code false}, otherwise.
     */
    public boolean isHealthy() {
        return status != Status.DOWN;
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        if (label != null) {
            jsonObjectBuilder.set(JSON_KEY_LABEL, label);
        }

        jsonObjectBuilder.set(JSON_KEY_STATUS, status.toString());

        final JsonArray detailsArray = getDetailsAsArray();
        if (!detailsArray.isEmpty()) {
            jsonObjectBuilder.set(JSON_KEY_DETAILS, detailsArray);
        }

        final JsonArray childrenArray = getChildrenAsArray();
        if (!childrenArray.isEmpty()) {
            jsonObjectBuilder.set(JSON_KEY_CHILDREN, childrenArray);
        }

        return jsonObjectBuilder.build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, details, children, label);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final StatusInfo that = (StatusInfo) obj;
        return Objects.equals(status, that.status) && Objects.equals(details, that.details) &&
                Objects.equals(children, that.children) && Objects.equals(label, that.label);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "status=" + status + ", details=" + details + ", children=" +
                children + ", label=" + label + "]";
    }

    private JsonArray getDetailsAsArray() {
        // performance shortcut
        if (details.isEmpty()) {
            return JsonFactory.newArray();
        }

        final JsonArrayBuilder arrayBuilder = JsonArray.newBuilder();
        details.forEach(detailMessage -> arrayBuilder.add(detailMessage.toJson()));
        return arrayBuilder.build();
    }

    private JsonArray getChildrenAsArray() {
        // performance shortcut
        if (children.isEmpty()) {
            return JsonFactory.newArray();
        }

        final JsonArrayBuilder arrayBuilder = JsonArray.newBuilder();
        children.forEach(child -> arrayBuilder.add(child.toJson()));
        return arrayBuilder.build();
    }

    private String getLabelOrIndexInfo(final int index) {
        return (label != null) ? label : "[" + index + "]";
    }


    private static StatusInfo mapToStatusInfo(final Status status, final @Nullable String message) {
        final List<StatusDetailMessage> details;
        if (message == null) {
            details = Collections.emptyList();
        } else {
            final StatusDetailMessage.Level level = mapToMessageLevel(status);
            final StatusDetailMessage detailMessage = StatusDetailMessage.of(level, JsonValue.of(message));
            details = Collections.singletonList(detailMessage);
        }

        return of(status, details, Collections.emptyList(), null);
    }

    private static StatusDetailMessage.Level mapToMessageLevel(final Status status) {
        switch (status) {
            case UP:
            case UNKNOWN:
                return StatusDetailMessage.Level.INFO;
            case DOWN:
                return StatusDetailMessage.Level.ERROR;
            default:
                // defensive code in case Status enum will be extended and mapping is not adjusted
                final StatusDetailMessage.Level fallbackLevel = StatusDetailMessage.Level.WARN;
                LOGGER.warn("Unknown status <{}>, mapping to message level <{}>.", status, fallbackLevel);
                return fallbackLevel;
        }
    }

    private static Status mapToStatusInfo(final StatusDetailMessage detail) {
        final StatusDetailMessage.Level level = detail.getLevel();
        return mapToStatusInfo(level);
    }

    private static Status mapToStatusInfo(final StatusDetailMessage.Level level) {
        switch (level) {
            case INFO:
            case WARN:
                return Status.UP;
            case ERROR:
                return Status.DOWN;
            default:
                // defensive code in case Level enum will be extended and mapping is not adjusted
                final Status fallbackStatus = Status.UNKNOWN;
                LOGGER.warn("Unknown message level <{}>, mapping to status <{}>.", level, fallbackStatus);
                return fallbackStatus;
        }
    }

    private static Status mapToStatusInfo(final Collection<StatusDetailMessage> details) {
        Status resultingStatus = Status.UP;

        for (final StatusDetailMessage detail : details) {
            final Status currentStatus = mapToStatusInfo(detail);
            resultingStatus = resultingStatus.mergeWith(currentStatus);
        }

        return resultingStatus;
    }

    private static List<StatusInfo> labelStatuses(final Map<String, StatusInfo> statuses) {
        return statuses.entrySet().stream()
                .map(entry -> entry.getValue().label(entry.getKey()))
                .toList();
    }

    private static StatusInfo createCompositeStatusInfo(final List<StatusInfo> children) {
        Status resultingStatus = DEFAULT_EMPTY_STATUS;
        final Map<StatusDetailMessage.Level, List<String>> pathsPerLevel = new EnumMap<>
                (StatusDetailMessage.Level.class);

        final Iterator<StatusInfo> childrenIterator = children.iterator();
        if (childrenIterator.hasNext()) {
            final int childrenSize = children.size();
            for (int i = 0; i < childrenSize; i++) {
                final StatusInfo child = childrenIterator.next();

                if (i == 0) {
                    // for the first child we do not yet have a valid status to merge with
                    resultingStatus = child.status;
                } else {
                    resultingStatus = resultingStatus.mergeWith(child.status);
                }

                for (StatusDetailMessage detail : child.details) {
                    if (COMPOSITE_DETAILS_INCLUDE_LEVELS.contains(detail.getLevel())) {
                        final String path = child.getLabelOrIndexInfo(i);

                        final List<String> pathsForLevel = pathsPerLevel.computeIfAbsent(detail.getLevel(),
                                unused -> new ArrayList<>());
                        pathsForLevel.add(path);
                    }
                }
            }
        }

        final List<StatusDetailMessage> details = pathsPerLevel.entrySet()
                .stream()
                .map(entry -> StatusDetailMessage.of(entry.getKey(), JsonValue.of(
                        "See detailed messages for: " + String.join(", ", entry.getValue()) + ".")))
                // order by level ascending, e.g. ("ERROR", "WARN", ...)
                .sorted(Comparator.comparing(StatusDetailMessage::getLevel).reversed())
                .toList();


        return of(resultingStatus, details, children, null);
    }

}
