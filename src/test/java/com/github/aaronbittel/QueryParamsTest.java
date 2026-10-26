package com.github.aaronbittel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
