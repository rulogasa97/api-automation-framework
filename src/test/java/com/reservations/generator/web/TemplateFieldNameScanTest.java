package com.reservations.generator.web;

import com.reservations.generator.domain.flow.FlowRegistry;
import com.reservations.generator.domain.model.FlowDefinition;
import com.reservations.generator.domain.model.Passenger;
import com.reservations.generator.domain.model.PassengerFieldDescriptor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the spec's "zero hardcoded field name" requirement at the template
 * source level (not just at render time): no {@code templates/**.html} file
 * may hardcode a registered passenger field name as a literal, static
 * {@code name="..."} HTML attribute — every input's {@code name}/{@code id}
 * must instead come from {@code th:name}/{@code th:id} bound to a {@link
 * PassengerFieldDescriptor}. Adding a field to the schema must never require
 * a template edit (see design's rationale for {@code
 * FlowRegistry#describePassengerFields}).
 *
 * <p>Uses the same {@link FlowRegistry#describePassengerFields} the
 * production templates render from, so the registered field-name list this
 * test scans for is always the real one, not a hand-maintained guess.
 */
class TemplateFieldNameScanTest {

    private static final Path TEMPLATES_ROOT = Path.of("src", "main", "resources", "templates");

    @Test
    void noTemplateHardcodesARegisteredPassengerFieldNameAsAStaticNameAttribute() throws IOException {
        List<String> fieldNames = defaultFlowFieldNames();
        assertThat(fieldNames).as("sanity check: the default flow must describe at least one field").isNotEmpty();

        List<Path> htmlFiles = allTemplateHtmlFiles();
        assertThat(htmlFiles).as("sanity check: templates must exist for this scan to be meaningful").isNotEmpty();

        for (Path file : htmlFiles) {
            String source = Files.readString(file);
            for (String fieldName : fieldNames) {
                assertThat(templateHardcodesFieldName(source, fieldName))
                        .as(file + " must not hardcode a static name=\"" + fieldName + "\" attribute")
                        .isFalse();
            }
        }
    }

    @Test
    void scannerCatchesAHardcodedStaticNameAttributeLiteral() {
        String fixture = "<input type=\"text\" name=\"name\">";

        assertThat(templateHardcodesFieldName(fixture, "name"))
                .as("the scanner must flag a literal, non-Thymeleaf-bound name=\"name\" attribute")
                .isTrue();
    }

    @Test
    void scannerIgnoresADynamicThNameBinding() {
        String fixture = "<input type=\"text\" th:name=\"${field.name()}\">";

        assertThat(templateHardcodesFieldName(fixture, "name"))
                .as("th:name is schema-driven, not a hardcoded literal, and must not be flagged")
                .isFalse();
    }

    private static List<String> defaultFlowFieldNames() {
        FlowRegistry registry = new FlowRegistry();
        FlowDefinition flow = new FlowDefinition("ual-create-v1", "1", Passenger.class);
        return registry.describePassengerFields(flow).stream().map(PassengerFieldDescriptor::name).toList();
    }

    private static List<Path> allTemplateHtmlFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(TEMPLATES_ROOT)) {
            return paths.filter(p -> p.toString().endsWith(".html")).toList();
        }
    }

    /**
     * Flags a literal {@code name="<fieldName>"} attribute that is NOT
     * prefixed by {@code th:} (i.e. not schema-driven).
     */
    private static boolean templateHardcodesFieldName(String templateSource, String fieldName) {
        Pattern pattern = Pattern.compile("(?<!th:)\\bname\\s*=\\s*\"" + Pattern.quote(fieldName) + "\"");
        return pattern.matcher(templateSource).find();
    }
}
