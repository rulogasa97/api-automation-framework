package com.reservations.generator.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests proving Bean Validation rejects missing/blank required fields
 * on {@link CreateReservationsRequest} before any upstream call is possible.
 */
class CreateReservationsRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void validRequestHasNoViolations() {
        CreateReservationsRequest request = new CreateReservationsRequest(
                "ual-create-v1", "1", List.of(Map.of("name", "Ada Lovelace")));

        Set<ConstraintViolation<CreateReservationsRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void blankFlowIdIsRejected() {
        CreateReservationsRequest request = new CreateReservationsRequest(
                "   ", "1", List.of(Map.of("name", "Ada Lovelace")));

        Set<ConstraintViolation<CreateReservationsRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("flowId"));
    }

    @Test
    void blankSchemaVersionIsRejected() {
        CreateReservationsRequest request = new CreateReservationsRequest(
                "ual-create-v1", "", List.of(Map.of("name", "Ada Lovelace")));

        Set<ConstraintViolation<CreateReservationsRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("schemaVersion"));
    }

    @Test
    void emptyPassengerListIsRejected() {
        CreateReservationsRequest request = new CreateReservationsRequest(
                "ual-create-v1", "1", List.of());

        Set<ConstraintViolation<CreateReservationsRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("passengers"));
    }

    @Test
    void nullPassengerListIsRejected() {
        CreateReservationsRequest request = new CreateReservationsRequest(
                "ual-create-v1", "1", null);

        Set<ConstraintViolation<CreateReservationsRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("passengers"));
    }

    @Test
    void nullPassengerEntryIsRejected() {
        CreateReservationsRequest request = new CreateReservationsRequest(
                "ual-create-v1", "1", java.util.Arrays.asList((Map<String, Object>) null));

        Set<ConstraintViolation<CreateReservationsRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }
}
