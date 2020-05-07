package org.eclipse.ditto.model.things;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointerInvalidException;

@Immutable
final class ImmutablePatternValidator<T> {

    private final Pattern pattern;
    private final T t;

    public static ImmutablePatternValidatorBuilder toBuilder() {
        return new ImmutablePatternValidatorBuilder();
    }

    private ImmutablePatternValidator(final Pattern pattern, final T t) {
        this.pattern = pattern;
        this.t = t;
    }

    private void validateCharSequence(final CharSequence featureId) {
        if (!pattern.matcher(featureId).matches()) {
            throw JsonPointerInvalidException.newBuilder().build();
        }
    }

    private void validateJsonKeys(final JsonObject jsonObject) {
        for (final JsonKey key : jsonObject.getKeys()) {
            if (!pattern.matcher(key).matches()) {
                throw JsonPointerInvalidException.newBuilder().build();
            }
        }
    }

    public void validate() {
        if (t instanceof JsonObject) {
            this.validateJsonKeys((JsonObject) t);
        }

        if (t instanceof CharSequence) {
            this.validateCharSequence((CharSequence) t);
        }
    }

    static final class ImmutablePatternValidatorBuilder {

        @Nullable
        private Pattern pattern;

        private final Pattern ATTRIBUTE_FEATURE_PATTERN = Pattern.compile("^[^/<>\\\\&%$\\s]*$");

        public ImmutablePatternValidatorBuilder withPattern(final Pattern pattern) {
            checkNotNull(pattern, "pattern to be set");
            this.pattern = pattern;
            return this;
        }

        public ImmutablePatternValidatorBuilder withAttributePattern() {
            this.pattern = ATTRIBUTE_FEATURE_PATTERN;
            return this;
        }

        public ImmutablePatternValidatorBuilder withFeaturePattern() {
            return withAttributePattern();
        }

        public ImmutablePatternValidator<?> buildFor(JsonObject target) {
            checkNotNull(target, "target to check must be set");
            return new ImmutablePatternValidator<JsonObject>(this.pattern, target);
        }

        public ImmutablePatternValidator<?> buildFor(CharSequence target) {
            checkNotNull(target, "target to check must be set");
            return new ImmutablePatternValidator<CharSequence>(this.pattern, target);
        }

    }

}
