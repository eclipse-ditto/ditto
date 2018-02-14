package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.amqpbridge.messaging.InternalMessage;

import com.google.common.base.Converter;

public class DittoMessageMapper extends MessageMapper {

    private static final Converter<String, Adaptable> STRING_ADAPTABLE_CONVERTER = Converter.from(
            s -> {
                try {
                    return ProtocolFactory.jsonifiableAdaptableFromJson(JsonFactory.newObject(s));
                } catch (Exception e) {
                    throw new IllegalArgumentException(String.format("Failed to map '%s'", s), e);
                }
            },
            a -> {
                try {
                    return ProtocolFactory.wrapAsJsonifiableAdaptable(a).toJsonString();
                } catch (Exception e) {
                    throw new IllegalArgumentException(String.format("Failed to map '%s'", a), e);
                }
            }
    );

    public DittoMessageMapper() {
        this(true);
    }

    public DittoMessageMapper(final boolean isContentTypeRequired) {
        super(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE, isContentTypeRequired);
    }

    @Override
    public void configure(final Map<String, String> options) {
        findOption(options, OPT_CONTENT_TYPE_REQUIRED)
                .map(Boolean::valueOf)
                .ifPresent(this::setContentTypeRequired);
    }

    @Override
    protected Adaptable doForward(final InternalMessage message) {
        requireMatchingContentType(message);

        if (!message.isTextMessage()) {
            throw new IllegalArgumentException("Message is not a text message");
        }

        return message.getTextPayload().filter(s -> !s.isEmpty()).map(STRING_ADAPTABLE_CONVERTER::convert)
                .orElseThrow(() -> new IllegalArgumentException("Message contains no payload"));
    }

    @Override
    protected InternalMessage doBackward(final Adaptable adaptable) {
        final Map<String, String> headers = new LinkedHashMap<>(adaptable.getHeaders().orElse(DittoHeaders.empty()));
        return new InternalMessage.Builder(headers)
                .withText(STRING_ADAPTABLE_CONVERTER.reverse().convert(adaptable))
                .build();
    }


}
