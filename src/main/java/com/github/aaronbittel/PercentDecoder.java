package com.github.aaronbittel;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class PercentDecoder {

    private PercentDecoder() {}

    public static String decode(String s) {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        var sb = new StringBuilder();
        var buf = new ByteArrayOutputStream();
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) != '%') {
                appendDecodedBytes(sb, buf, decoder);
                sb.append(s.charAt(i));
                i++;
                continue;
            }

            if (i + 3 > s.length()) {
                throw new IllegalArgumentException("Incomplete percent encoding");
            }
            buf.write(parseHexByte(s.charAt(i + 1), s.charAt(i + 2)));
            i += 3;
        }

        appendDecodedBytes(sb, buf, decoder);
        return sb.toString();
    }

    private static void appendDecodedBytes(StringBuilder sb, ByteArrayOutputStream buf, CharsetDecoder decoder) {
        if (buf.size() == 0) {
            return;
        }

        try {
            sb.append(decoder.decode(ByteBuffer.wrap(buf.toByteArray())));
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Invalid UTF-8 in percent encoding", e);
        }

        buf.reset();
    }

    private static int parseHexByte(char highNibble, char lowNibble) {
        return (hexValue(highNibble) << 4) | hexValue(lowNibble);
    }

    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        throw new IllegalArgumentException("Invalid percent encoding");
    }
}
