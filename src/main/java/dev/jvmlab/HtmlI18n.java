package dev.jvmlab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Shared language controls; reports embed the script to remain usable offline. */
final class HtmlI18n {
    private HtmlI18n() {}

    static String text(String en, String ko) {
        return "<span data-en=\"" + escape(en) + "\" data-ko=\"" + escape(ko) + "\">" + escape(en) + "</span>";
    }

    static String controls() {
        return "<nav class=\"languages\" aria-label=\"Language / 언어\">" +
            "<button type=\"button\" data-language=\"en\" lang=\"en\" aria-pressed=\"true\">English</button>" +
            "<button type=\"button\" data-language=\"ko\" lang=\"ko\" aria-pressed=\"false\">한국어</button></nav>";
    }

    static String script() throws IOException {
        try (var input = HtmlI18n.class.getResourceAsStream("/language.js")) {
            if (input == null) throw new IOException("Missing language.js resource");
            return "<script>" + new String(input.readAllBytes(), StandardCharsets.UTF_8) + "</script>";
        }
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
