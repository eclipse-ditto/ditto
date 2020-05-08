package org.eclipse.ditto.model.things;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointerInvalidException;

@Immutable
final class ImmutablePatternValidator {

    private final Pattern pattern;
    @Nullable
    private final String exceptionDescription;
    @Nullable
    private final String targetDescription;

    public static ImmutablePatternValidatorBuilder toBuilder() {
        return new ImmutablePatternValidatorBuilder();
    }

    private ImmutablePatternValidator(final Pattern pattern, @Nullable final String exception, @Nullable  String targetDescription) {
        this.pattern = pattern;
        this.exceptionDescription = exception;
        this.targetDescription = targetDescription;
    }

    public void validate(final JsonObject target) {
        for (final JsonKey key : target.getKeys()) {
            validate(key);
        }
    }

    public Matcher validate(final CharSequence target) {
        checkNotNull(target, this.targetDescription != null ? this.targetDescription : "Target");
        final Matcher matcher = pattern.matcher(target);
        if (!matcher.matches()) {
            throw this.exceptionDescription != null ?
                    JsonPointerInvalidException.newBuilderWithDescription(target, this.exceptionDescription).build() :
                    JsonPointerInvalidException.newBuilderWithoutDescription(target).build();
        }
        return matcher;
    }

    static final class ImmutablePatternValidatorBuilder {

        @Nullable
        private Pattern pattern;
        @Nullable
        private String exceptionDescription;
        @Nullable
        private String targetDescription;

        private final Pattern ATTRIBUTE_FEATURE_PATTERN = Pattern.compile("^[^/<>\\\\&%$?\\s]*$");

        public ImmutablePatternValidatorBuilder withPattern(final Pattern pattern) {
            checkNotNull(pattern, "Pattern");
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

        public ImmutablePatternValidatorBuilder withExceptionDescription(final String description) {
            this.exceptionDescription = description;
            return this;
        }

        public ImmutablePatternValidatorBuilder withTargetDescription(final String description) {
            this.targetDescription = description;
            return this;
        }

        public ImmutablePatternValidator build() {
            checkNotNull(this.pattern, "Pattern");
            return new ImmutablePatternValidator(this.pattern, this.exceptionDescription, this.targetDescription);
        }
    }

}
