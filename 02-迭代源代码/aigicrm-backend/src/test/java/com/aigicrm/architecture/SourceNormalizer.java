package com.aigicrm.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test-only source normalizer for P0-5 batch A (Sonar java:S1192).
 * Architecture guards assert on source substrings; after duplicated literals are
 * extracted into LIT_* constants, normalize those constants back to their literal
 * values so the guards keep checking the same semantic patterns.
 */
final class SourceNormalizer {

    private static final Pattern CONST = Pattern.compile(
            "(?:private\\s+|public\\s+|protected\\s+)?(?:static\\s+)?(?:final\\s+)?String\\s+"
                    + "(LIT_[A-Za-z0-9_]+)\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*;");

    private SourceNormalizer() {
    }

    static String read(Path path) throws IOException {
        String text = Files.readString(path);
        Map<String, String> consts = new LinkedHashMap<>();
        Matcher m = CONST.matcher(text);
        while (m.find()) {
            consts.putIfAbsent(m.group(1), m.group(2));
        }
        text = CONST.matcher(text).replaceAll("");
        for (Map.Entry<String, String> e : consts.entrySet()) {
            text = text.replaceAll("\\b" + Pattern.quote(e.getKey()) + "\\b",
                    Matcher.quoteReplacement("\"" + e.getValue() + "\""));
        }
        return text;
    }
}
