package com.restmvc.beer_store.entities;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CategoryTest {

    private Validator validator;
    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void should_CreateCategory() {
        Category testCategory = Category.builder()
                .description("Test Category")
                .build();
        Set<ConstraintViolation<Category>> violations = validator.validate(testCategory);
        assertEquals(0, violations.size());
        assertNotNull(testCategory);
    }

    @Test
    void should_CreateCategoryWithDescription() {
        Category testCategory = Category.builder()
                .description("Test Category")
                .build();
        Set<ConstraintViolation<Category>> violations = validator.validate(testCategory);
        assertEquals(0, violations.size());
        assertNotNull(testCategory);
    }

}