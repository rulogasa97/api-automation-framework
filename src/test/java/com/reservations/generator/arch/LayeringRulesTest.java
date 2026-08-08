package com.reservations.generator.arch;

import com.tngtech.archunit.core.domain.AccessTarget.MethodCallTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the hexagonal layering rule from the design: {@code api} and
 * {@code domain} depend inward only; nothing in either package may import
 * {@code apireplay}, since it is a volatile outbound adapter.
 */
class LayeringRulesTest {

    private static final String BASE_PACKAGE = "com.reservations.generator";
    private static final String REST_ASSURED_CLASS = "io.restassured.RestAssured";
    private static final String REQUEST_SPECIFICATION_CLASS = "io.restassured.specification.RequestSpecification";
    private static final String RESPONSE_SPECIFICATION_CLASS = "io.restassured.specification.ResponseSpecification";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .importPackages(BASE_PACKAGE);

    // Main-source-only import: RestAssured is legitimately used statically in
    // test code (HTTP-level testing), so this scope deliberately excludes it.
    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    @Test
    void domainMustNotDependOnApireplay() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".domain..")
                .should().dependOnClassesThat().resideInAPackage(BASE_PACKAGE + ".apireplay..");

        rule.check(CLASSES);
    }

    @Test
    void apiMustNotDependOnApireplay() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".api..")
                .should().dependOnClassesThat().resideInAPackage(BASE_PACKAGE + ".apireplay..");

        rule.check(CLASSES);
    }

    @Test
    void mainSourcesMustNotStaticallyAccessRestAssured() {
        ArchRule rule = noClasses().should(unsafelyAccessRestAssured());

        rule.check(MAIN_CLASSES);
    }

    /**
     * Describes classes that statically access {@code io.restassured.RestAssured}
     * (field reads/writes such as {@code RestAssured.baseURI}, the no-arg
     * {@code given()}, etc.) other than through the public, stateless
     * two-argument {@code given(RequestSpecification, ResponseSpecification)}
     * overload, which takes both specs as parameters and returns a fresh,
     * independent spec per call rather than reading/writing shared static
     * state.
     *
     * <p>Note: this condition reports a class as <em>satisfying</em> it
     * (not violating it) when the class performs such an unsafe access.
     * That is intentional: {@code noClasses().should(condition)} internally
     * negates/inverts whatever this condition reports (see ArchUnit's
     * {@code NeverCondition}), so a "satisfied" event here is what surfaces
     * as an overall rule violation once wrapped by {@code noClasses()}.
     */
    private static ArchCondition<JavaClass> unsafelyAccessRestAssured() {
        return new ArchCondition<>("statically access RestAssured other than the stateless "
                + "given(RequestSpecification, ResponseSpecification) overload") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                item.getAccessesFromSelf().stream()
                        .filter(access -> access.getTargetOwner().getFullName().equals(REST_ASSURED_CLASS))
                        .filter(access -> !isStatelessGivenOverload(access))
                        .forEach(access -> events.add(SimpleConditionEvent.satisfied(access, access.getDescription())));
            }
        };
    }

    private static boolean isStatelessGivenOverload(JavaAccess<?> access) {
        if (!(access.getTarget() instanceof MethodCallTarget methodCallTarget)) {
            return false;
        }
        if (!methodCallTarget.getName().equals("given")) {
            return false;
        }
        List<JavaClass> parameterTypes = methodCallTarget.getRawParameterTypes();
        return parameterTypes.size() == 2
                && parameterTypes.get(0).getFullName().equals(REQUEST_SPECIFICATION_CLASS)
                && parameterTypes.get(1).getFullName().equals(RESPONSE_SPECIFICATION_CLASS);
    }
}
