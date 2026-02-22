package com.restmvc.beer_store.dtos.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO used for both creating and fully updating a customer (POST and PUT).
 *
 * <p>All fields are validated by Bean Validation before the request reaches the service layer.
 * Validation errors are collected and returned as a 400 Bad Request with field-level details
 * by {@link com.restmvc.beer_store.exceptions.GlobalExceptionsHandler}.</p>
 *
 * <p>Reusing the same DTO for create and update (PUT) is intentional — PUT is a full
 * replacement, so all fields must always be provided. If partial updates (PATCH) are
 * introduced later, a separate {@code CustomerUpdateRequestDTO} with all-optional fields
 * should be created.</p>
 */
public record CustomerCreateRequestDTO(

        /**
         * Customer's full name.
         * Must be non-blank and between 2 and 100 characters.
         * Stored as a UNIQUE value in the DB — two customers cannot share the same name.
         *
         * Note: min = 2 allows short but valid names (e.g. "Al", "Bo").
         */
        @NotBlank
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        /**
         * Customer's email address.
         * Must be a well-formed email, non-blank, and max 100 characters.
         * Stored as a UNIQUE value in the DB — used as a login/contact identifier.
         */
        @Email
        @NotBlank
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email,

        /**
         * Customer's contact phone number.
         * Must be non-blank and between 7 and 20 characters.
         * Allows formats like "+421 900 123 456" or "00421900123456".
         *
         * Note: min = 7 is the international minimum for a valid phone number.
         */
        @NotBlank
        @Size(min = 7, max = 20, message = "Phone number must be between 7 and 20 characters")
        String phoneNumber

) {}
