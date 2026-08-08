package com.reservations.generator.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the "create-only" boundary (REQ-9) across the whole main source
 * tree, not just {@code api} — widened per design D4 once a second driving
 * adapter ({@code web}) was introduced, which could otherwise expose GET
 * routes vacuously outside {@code api}'s original scan scope. "Create-only"
 * is enforced by <em>capability</em>, not by literally banning GET
 * everywhere:
 * <ul>
 *     <li><b>R1</b> {@code api}: exactly one {@code @RestController}, exactly
 *     one {@code @PostMapping}, zero GET/PUT/PATCH/DELETE.</li>
 *     <li><b>R2</b> whole main tree: no PUT/PATCH/DELETE mapping anywhere —
 *     "no edit, no cancel" is a project-wide invariant, not just an
 *     {@code api}-scoped one.</li>
 *     <li><b>R3</b> {@code web}: GET is allowed, but only for rendering — no
 *     {@code @PathVariable} (a lookup needs an identifier) and every GET
 *     handler must return {@code String}/{@link ModelAndView}, never a
 *     domain or response type.</li>
 *     <li><b>R4</b> outside {@code api}: zero {@code @RestController} — every
 *     other driving adapter (e.g. {@code web}) uses {@code @Controller}.</li>
 * </ul>
 * Each new rule (R2-R4) has a companion test proving it actually flags a
 * violation, using a small fixture class local to this test — not just that
 * it passes vacuously against the current, compliant codebase.
 */
class CreateOnlyBoundaryTest {

    private static final String BASE_PACKAGE = "com.reservations.generator";
    private static final String API_PACKAGE = BASE_PACKAGE + ".api";
    private static final String WEB_PACKAGE = BASE_PACKAGE + ".web";

    private static final JavaClasses API_CLASSES = new ClassFileImporter().importPackages(API_PACKAGE);
    private static final JavaClasses WEB_CLASSES = new ClassFileImporter().importPackages(WEB_PACKAGE);

    // R2/R4 scan the whole main tree; tests are deliberately excluded since
    // test-only fixtures (including this file's own violation fixtures)
    // must never trip these rules.
    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    // ---------------------------------------------------------------
    // R1 (existing): api is exactly one create-only REST controller
    // ---------------------------------------------------------------

    @Test
    void apiExposesExactlyOneRestControllerWithExactlyOnePostReservationsRoute() {
        List<JavaClass> restControllers = API_CLASSES.stream()
                .filter(clazz -> clazz.isAnnotatedWith(RestController.class))
                .toList();

        assertThat(restControllers)
                .as("exactly one @RestController is expected under api/")
                .hasSize(1);

        JavaClass controller = restControllers.get(0);
        assertThat(controller.getFullName()).endsWith("ReservationController");

        List<JavaMethod> httpMappedMethods = controller.getMethods().stream()
                .filter(CreateOnlyBoundaryTest::isHttpMapped)
                .toList();

        assertThat(httpMappedMethods)
                .as("ReservationController must expose exactly one HTTP-mapped method")
                .hasSize(1);

        JavaMethod method = httpMappedMethods.get(0);
        assertThat(method.isAnnotatedWith(PostMapping.class))
                .as("the sole route must be a POST mapping")
                .isTrue();
        assertThat(method.isAnnotatedWith(GetMapping.class)).isFalse();
        assertThat(method.isAnnotatedWith(PutMapping.class)).isFalse();
        assertThat(method.isAnnotatedWith(PatchMapping.class)).isFalse();
        assertThat(method.isAnnotatedWith(DeleteMapping.class)).isFalse();
    }

    @Test
    void noMethodAnywhereInApiUsesAReadOrMutationHttpVerbOtherThanCreate() {
        Set<RequestMethod> forbiddenVerbs = Set.of(
                RequestMethod.GET, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

        API_CLASSES.forEach(clazz -> clazz.getMethods().forEach(method -> {
            assertThat(method.isAnnotatedWith(GetMapping.class))
                    .as(method.getFullName() + " must not be a GET (lookup/list) route")
                    .isFalse();
            assertThat(method.isAnnotatedWith(PutMapping.class))
                    .as(method.getFullName() + " must not be a PUT route")
                    .isFalse();
            assertThat(method.isAnnotatedWith(PatchMapping.class))
                    .as(method.getFullName() + " must not be a PATCH route")
                    .isFalse();
            assertThat(method.isAnnotatedWith(DeleteMapping.class))
                    .as(method.getFullName() + " must not be a DELETE (cancel) route")
                    .isFalse();

            if (method.isAnnotatedWith(RequestMapping.class)) {
                RequestMethod[] methods = method.getAnnotationOfType(RequestMapping.class).method();
                for (RequestMethod requestMethod : methods) {
                    assertThat(forbiddenVerbs)
                            .as(method.getFullName() + " must not use forbidden verb " + requestMethod)
                            .doesNotContain(requestMethod);
                }
            }
        }));
    }

    // ---------------------------------------------------------------
    // R2 (new): no PUT/PATCH/DELETE mapping anywhere in the main tree
    // ---------------------------------------------------------------

    @Test
    void r2_noMutationMappingExistsAnywhereInTheMainSourceTree() {
        assertThat(mutationVerbViolations(MAIN_CLASSES)).isEmpty();
    }

    @Test
    void r2_catchesAPutMappingViolationEvenOutsideApi() {
        JavaClasses fixture = new ClassFileImporter().importClasses(R2ViolatingFixture.class);

        assertThat(mutationVerbViolations(fixture))
                .as("R2 must flag a @PutMapping anywhere in the main tree, not just inside api/")
                .isNotEmpty();
    }

    private static List<String> mutationVerbViolations(JavaClasses classes) {
        List<String> violations = new ArrayList<>();
        classes.forEach(clazz -> clazz.getMethods().forEach(method -> {
            if (method.isAnnotatedWith(PutMapping.class)
                    || method.isAnnotatedWith(PatchMapping.class)
                    || method.isAnnotatedWith(DeleteMapping.class)
                    || usesForbiddenRequestMappingVerb(method)) {
                violations.add(method.getFullName());
            }
        }));
        return violations;
    }

    private static boolean usesForbiddenRequestMappingVerb(JavaMethod method) {
        if (!method.isAnnotatedWith(RequestMapping.class)) {
            return false;
        }
        for (RequestMethod requestMethod : method.getAnnotationOfType(RequestMapping.class).method()) {
            if (requestMethod == RequestMethod.PUT
                    || requestMethod == RequestMethod.PATCH
                    || requestMethod == RequestMethod.DELETE) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------
    // R3 (new): web GET handlers render only — no @PathVariable, must
    // return String/ModelAndView
    // ---------------------------------------------------------------

    @Test
    void r3_everyWebGetHandlerRendersOnlyWithNoPathVariableAndAStringOrModelAndViewReturnType() {
        assertThat(r3Violations(WEB_CLASSES)).isEmpty();
    }

    @Test
    void r3_catchesAPathVariableOnAWebGetHandler() {
        JavaClasses fixture = new ClassFileImporter().importClasses(R3PathVariableViolatingFixture.class);

        assertThat(r3Violations(fixture))
                .as("R3 must flag a @PathVariable on a GET handler (a lookup-by-identifier capability)")
                .isNotEmpty();
    }

    @Test
    void r3_catchesAGetHandlerReturningADomainTypeInsteadOfStringOrModelAndView() {
        JavaClasses fixture = new ClassFileImporter().importClasses(R3WrongReturnTypeViolatingFixture.class);

        assertThat(r3Violations(fixture))
                .as("R3 must flag a GET handler that returns something other than String/ModelAndView")
                .isNotEmpty();
    }

    private static List<String> r3Violations(JavaClasses classes) {
        List<String> violations = new ArrayList<>();
        classes.forEach(clazz -> clazz.getMethods().stream()
                .filter(method -> method.isAnnotatedWith(GetMapping.class))
                .forEach(method -> {
                    boolean hasPathVariable = method.getParameters().stream()
                            .anyMatch(parameter -> parameter.isAnnotatedWith(PathVariable.class));
                    if (hasPathVariable) {
                        violations.add(method.getFullName() + " declares @PathVariable on a GET handler");
                    }
                    JavaClass returnType = method.getRawReturnType();
                    boolean returnsRenderableType = returnType.isEquivalentTo(String.class)
                            || returnType.isEquivalentTo(ModelAndView.class);
                    if (!returnsRenderableType) {
                        violations.add(method.getFullName() + " must return String or ModelAndView, got "
                                + returnType.getFullName());
                    }
                }));
        return violations;
    }

    // ---------------------------------------------------------------
    // R4 (new): zero @RestController outside api
    // ---------------------------------------------------------------

    @Test
    void r4_noRestControllerExistsAnywhereOutsideApi() {
        assertThat(restControllersOutsideApi(MAIN_CLASSES)).isEmpty();
    }

    @Test
    void r4_catchesARestControllerDeclaredOutsideApi() {
        JavaClasses fixture = new ClassFileImporter().importClasses(R4RestControllerViolatingFixture.class);

        assertThat(restControllersOutsideApi(fixture))
                .as("R4 must flag a @RestController declared outside api/")
                .isNotEmpty();
    }

    private static List<String> restControllersOutsideApi(JavaClasses classes) {
        List<String> violations = new ArrayList<>();
        classes.forEach(clazz -> {
            if (clazz.isAnnotatedWith(RestController.class) && !clazz.getPackageName().startsWith(API_PACKAGE)) {
                violations.add(clazz.getFullName());
            }
        });
        return violations;
    }

    // ---------------------------------------------------------------
    // Route collision (new): web must never map "/reservations", the
    // api's sole create route
    // ---------------------------------------------------------------

    @Test
    void web_neverMapsTheApiCreateReservationsRoute() {
        assertThat(routeCollisions(WEB_CLASSES)).isEmpty();
    }

    @Test
    void web_catchesARouteCollisionWithSlashReservations() {
        JavaClasses fixture = new ClassFileImporter().importClasses(RouteCollisionViolatingFixture.class);

        assertThat(routeCollisions(fixture))
                .as("web must never declare a route at the api's own \"/reservations\" path")
                .isNotEmpty();
    }

    private static final String RESERVED_API_ROUTE = "/reservations";

    private static List<String> routeCollisions(JavaClasses classes) {
        List<String> collisions = new ArrayList<>();
        classes.forEach(clazz -> clazz.getMethods().forEach(method -> {
            if (mapsPath(method, GetMapping.class) || mapsPath(method, PostMapping.class)) {
                collisions.addAll(matchingPaths(method));
            }
        }));
        return collisions;
    }

    private static boolean mapsPath(JavaMethod method, Class<? extends java.lang.annotation.Annotation> annotation) {
        return method.isAnnotatedWith(annotation);
    }

    private static List<String> matchingPaths(JavaMethod method) {
        List<String> matches = new ArrayList<>();
        String[] paths = method.isAnnotatedWith(GetMapping.class)
                ? method.getAnnotationOfType(GetMapping.class).value()
                : method.isAnnotatedWith(PostMapping.class)
                ? method.getAnnotationOfType(PostMapping.class).value()
                : new String[0];
        for (String path : paths) {
            if (RESERVED_API_ROUTE.equals(path)) {
                matches.add(method.getFullName() + " maps reserved path " + path);
            }
        }
        return matches;
    }

    private static boolean isHttpMapped(JavaMethod method) {
        return method.isAnnotatedWith(PostMapping.class)
                || method.isAnnotatedWith(GetMapping.class)
                || method.isAnnotatedWith(PutMapping.class)
                || method.isAnnotatedWith(PatchMapping.class)
                || method.isAnnotatedWith(DeleteMapping.class)
                || method.isAnnotatedWith(RequestMapping.class);
    }

    // ---------------------------------------------------------------
    // Violation fixtures: never scanned by MAIN_CLASSES (test sources are
    // excluded there) or by API_CLASSES/WEB_CLASSES (different package);
    // each is imported explicitly, by class, only by its own test.
    // ---------------------------------------------------------------

    private static final class R2ViolatingFixture {
        @PutMapping("/reservations/{id}")
        public void edit(@PathVariable String id) {
        }
    }

    @Controller
    private static final class R3PathVariableViolatingFixture {
        @GetMapping("/ui/reservations/{id}")
        public String lookup(@PathVariable String id) {
            return "reservation-detail";
        }
    }

    @Controller
    private static final class R3WrongReturnTypeViolatingFixture {
        @GetMapping("/ui/reservations")
        public Object list() {
            return List.of();
        }
    }

    @RestController
    private static final class R4RestControllerViolatingFixture {
        @GetMapping("/ui/api-style")
        public String data() {
            return "not allowed outside api/";
        }
    }

    @Controller
    private static final class RouteCollisionViolatingFixture {
        @PostMapping("/reservations")
        public String collide() {
            return "collides with api's create route";
        }
    }
}
