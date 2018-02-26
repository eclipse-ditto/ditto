package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.amqpbridge.InternalMessage;
import org.eclipse.ditto.protocoladapter.Adaptable;

class ContentTypeRestrictedMessageMapper implements MessageMapper {

    public static final String CONTENT_TYPE_KEY = "Content-Type";

    private final MessageMapper delegate;

    private ContentTypeRestrictedMessageMapper(final MessageMapper delegate) {
        this.delegate = checkNotNull(delegate);
    }

    public static MessageMapper of(final MessageMapper mapper) {
        return new ContentTypeRestrictedMessageMapper(mapper);
    }

    @Override
    public Optional<String> getContentType() {
        return delegate.getContentType();
    }

    @Override
    public void configure(final MessageMapperConfiguration configuration) {
        delegate.configure(configuration);
        requireConfiguredContentType();
    }

    @Override
    public Adaptable map(@Nullable final InternalMessage message) {
        requireMatchingContentType(message);
        return delegate.map(message);
    }

    @Override
    public InternalMessage map(@Nullable final Adaptable adaptable) {
        return delegate.map(adaptable);
    }

    private void requireConfiguredContentType() {
        if (!getContentType().isPresent()) {
            throw new IllegalArgumentException("Required configuration property missing: '%s'" +
                    MessageMapperConfigurationProperties.CONTENT_TYPE);
        }
    }

    private void requireMatchingContentType(@Nullable final InternalMessage internalMessage) {
        if (Objects.isNull(internalMessage)) return;

        final String contentType = getContentType().filter(s -> !s.isEmpty()).orElseThrow(
                () -> new IllegalArgumentException(String.format(
                        "A matching content type is required, but none configured. Set a content type with the following key in configuration: %s",
                        MessageMapperConfigurationProperties.CONTENT_TYPE))
        );

        final String actualContentType = findContentType(internalMessage)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Message headers do not contain a value for %s", CONTENT_TYPE_KEY)));

        if (!contentType.equalsIgnoreCase(actualContentType)) {
            throw new IllegalArgumentException(
                    String.format("Unsupported value for %s: actual='%s', expected='%s'",
                            CONTENT_TYPE_KEY, actualContentType, contentType));
        }

    }

    private static Optional<String> findContentType(final InternalMessage internalMessage) {
        checkNotNull(internalMessage);
        return internalMessage.findHeaderIgnoreCase(CONTENT_TYPE_KEY);
    }

    /**
     * Identifies and gets a configured content type of a protocol adaptable.
     *
     * @param adaptable the message
     * @return the content type if found
     */
    private static Optional<String> findContentType(final Adaptable adaptable) {
        checkNotNull(adaptable);
        return adaptable.getHeaders().map(h -> h.entrySet().stream()
                .filter(e -> CONTENT_TYPE_KEY.equalsIgnoreCase(e.getKey()))
                .findFirst()
                .map(Map.Entry::getValue).orElse(null));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ContentTypeRestrictedMessageMapper that = (ContentTypeRestrictedMessageMapper) o;

        return delegate.equals(that.delegate);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return "ContentTypeRestrictedMessageMapper{" +
                "delegate=" + delegate +
                '}';
    }
}
