package com.github.aaronbittel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PercentDecoderTest {

    @Test
    void returns_empty_string_for_empty_input() {
        assertThat(PercentDecoder.decode("")).isEmpty();
    }

    @Test
    void preserves_input_without_percent_encoding() {
        assertThat(PercentDecoder.decode("hello-world")).isEqualTo("hello-world");
    }

    @Test
    void decodes_multiple_percent_encoded_bytes() {
        assertThat(PercentDecoder.decode("hello%20world%21")).isEqualTo("hello world!");
    }

    @Test
    void decodes_utf8_sequences() {
        assertThat(PercentDecoder.decode("%C3%A9")).isEqualTo("é");
    }

    @Test
    void preserves_plus_signs() {
        assertThat(PercentDecoder.decode("hello+world")).isEqualTo("hello+world");
    }

    @Test
    void rejects_non_hexadecimal_escape_digits() {
        assertThatIllegalArgumentException().isThrownBy(() -> PercentDecoder.decode("hello%G0world"));
    }

    @Test
    void rejects_incomplete_percent_escape_at_end() {
        assertThatIllegalArgumentException().isThrownBy(() -> PercentDecoder.decode("hello%"));
    }

    @Test
    void rejects_percent_escape_with_only_one_hex_digit() {
        assertThatIllegalArgumentException().isThrownBy(() -> PercentDecoder.decode("hello%A"));
    }

    @Test
    void rejects_invalid_utf8_sequence() {
        assertThatIllegalArgumentException().isThrownBy(() -> PercentDecoder.decode("%FF"));
    }
}
