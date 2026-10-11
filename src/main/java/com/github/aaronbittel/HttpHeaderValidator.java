package com.github.aaronbittel;

import java.util.regex.Pattern;

final class HttpHeaderValidator {

    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("[!#$%&'*+\\-.^_`|~A-Za-z0-9]+");
    private static final Pattern FIELD_VALUE_PATTERN = Pattern.compile("[\\t\\x20-\\x7E\\x80-\\xFF]*");

    private HttpHeaderValidator() {
    }

    static boolean isValidFieldName(String name) {
        return name != null && FIELD_NAME_PATTERN.matcher(name).matches();
    }

    static boolean isValidFieldValue(String value) {
        return value != null && FIELD_VALUE_PATTERN.matcher(value).matches();
    }
}
