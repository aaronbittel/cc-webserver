package com.github.aaronbittel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class QueryParamsTest {

    @Test
    void handles_one_key_value_pair() {
        assertThat(QueryParams.parse("key=value").get("key")).containsExactly("value");
    }

    @Test
    void handles_two_distinct_key_value_pairs() {
        var query = QueryParams.parse("key=value&key2=value2");
        assertThat(query.get("key")).containsExactly("value");
        assertThat(query.get("key2")).containsExactly("value2");
    }

    @Test
    void handles_multiple_values_for_same_key() {
        var query = QueryParams.parse("key=value&key=value2");
        assertThat(query.get("key")).containsExactly("value", "value2");
    }

    @Test
    void handles_empty_values() {
        assertThat(QueryParams.parse("key=").get("key")).containsExactly("");
    }

    @Test
    void treats_key_without_equals_as_empty_value() {
        assertThat(QueryParams.parse("key").get("key")).containsExactly("");
    }

    @Test
    void preserves_equals_signs_in_value() {
        assertThat(QueryParams.parse("key=asldfj=lasjdf").get("key")).containsExactly("asldfj=lasjdf");
    }

    @Test
    void handles_empty_key() {
        assertThat(QueryParams.parse("=value").get("")).containsExactly("value");
    }

    @Test
    void returns_no_values_for_empty_query() {
        assertThat(QueryParams.parse("").get("key")).isEmpty();
    }

    @Test
    void decodes_percent_encoded_key_and_value() {
        var query = QueryParams.parse("first%20name=John%20Doe");
        assertThat(query.get("first name")).containsExactly("John Doe");
    }

    @Test
    void decodes_utf8_percent_encoded_values() {
        assertThat(QueryParams.parse("name=%C3%A9").get("name")).containsExactly("é");
    }

    @Test
    void preserves_encoded_query_delimiters() {
        assertThat(QueryParams.parse("key=one%26two%3Dthree").get("key")).containsExactly("one&two=three");
    }

    @Test
    void preserves_plus_signs() {
        assertThat(QueryParams.parse("key=one+two").get("key")).containsExactly("one+two");
    }

    @Test
    void rejects_invalid_percent_encoding() {
        assertThatIllegalArgumentException().isThrownBy(() -> QueryParams.parse("key=%GG"));
    }

    @Test
    void rejects_invalid_utf8_in_percent_encoding() {
        assertThatIllegalArgumentException().isThrownBy(() -> QueryParams.parse("key=%FF"));
    }
}
