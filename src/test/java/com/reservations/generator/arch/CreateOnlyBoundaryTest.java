package com.reservations.generator.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces REQ-9: this service is create-only. There must be no
 * lookup/cancel/list-my-reservations (or any other) route anywhere in
 * {@code api} besides the single {@code POST /reservations} endpoint.
 */
class CreateOnlyBoundaryTest {

    private static final String API_PACKAGE = "com.reservations.generator.api";

    private static final JavaClasses API_CLASSES = new ClassFileImporter().importPackages(API_PACKAGE);

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

    private static boolean isHttpMapped(JavaMethod method) {
        return method.isAnnotatedWith(PostMapping.class)
                || method.isAnnotatedWith(GetMapping.class)
                || method.isAnnotatedWith(PutMapping.class)
                || method.isAnnotatedWith(PatchMapping.class)
                || method.isAnnotatedWith(DeleteMapping.class)
                || method.isAnnotatedWith(RequestMapping.class);
    }
}
