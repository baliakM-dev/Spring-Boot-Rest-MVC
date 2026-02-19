package com.restmvc.beer_store.integreation;

import com.restmvc.beer_store.controllers.CategoryController;
import com.restmvc.beer_store.entities.Category;
import com.restmvc.beer_store.repositories.CategoryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Testcontainers
public class CatgegoryIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CategoryRepository repository;
    @Autowired
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    // ==================== Test Fixtures ====================
    private Category createCategory(String description) {
        return Category.builder().description(description).build();
    }

    private String createCategoryJson(String description) {
        return """
                 {
                 "description": "%s"
                 }
                """.formatted(escapeJson(description));
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ==================== Edge Cases Tests ====================
    @Nested
    @DisplayName("Edge Cases - Field Limits")
    class EdgeCasesFieldLimits {

        @Test
        @DisplayName("Should accept category description at maximum length (255 chars)")
        void shouldAcceptCategoryDescriptionAtMaxLength() throws Exception {
            // Given
            String category = "a".repeat(255);
            String categoryJson = createCategoryJson(category);

            // When - create category
            MvcResult result = mockMvc.perform(post(CategoryController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(categoryJson))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andReturn();

            // Then - verify location header
            String location = result.getResponse().getHeader("Location");
            String id = location.substring(location.lastIndexOf("/") + 1);
            Category savedCategory = em.find(Category.class, UUID.fromString(id));

            assertThat(savedCategory).isNotNull();
            assertThat(savedCategory.getDescription()).isEqualTo(category);
            assertThat(savedCategory.getDescription()).hasSize(255);
        }

        @Test
        @DisplayName("Should reject category description at minimum length (0 chars)")
        void shouldRejectCategoryDescriptionAtMinimumLength() throws Exception {
            // Given
            String categoryJson = createCategoryJson("");
            String expectedErrorMessage = "Validation failed";

            // When
            MvcResult result = mockMvc.perform(post(CategoryController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(categoryJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", containsString(expectedErrorMessage)))
                    .andReturn();

            // Then - verify error message
            String errorMessage = result.getResponse().getContentAsString();
            assertThat(errorMessage).contains(expectedErrorMessage);
        }

        @Test
        @DisplayName("Should reject category description at maximum length (256 chars)")
        void shouldRejectCategoryDescriptionAtMaximumLength() throws Exception {
            // Given
            String category = "a".repeat(256);
            String categoryJson = createCategoryJson(category);
            String expectedErrorMessage = "Validation failed";

            // When
            MvcResult result = mockMvc.perform(post(CategoryController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(categoryJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", containsString(expectedErrorMessage)))
                    .andReturn();

            // Then - verify error message
            String errorMessage = result.getResponse().getContentAsString();
            assertThat(errorMessage).contains(expectedErrorMessage);
        }
    }

    @Nested
    @DisplayName("Get All Categories Tests")
    class EdgeCasesDuplicateCategories {

        @Test
        @DisplayName("Should return all categories")
        void shouldReturnAllCategories() throws Exception {
            // Given
            Set<Category> categories = Set.of(createCategory("IPA"), createCategory("Lager"));
            repository.saveAll(categories);

            // When
            mockMvc.perform(get(CategoryController.BASE_URL)
                            .param("page", "0")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[*].description").value(containsInAnyOrder("IPA", "Lager")))
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        @DisplayName("Should return empty list when no Categories exist")
        void shouldReturnEmptyListWhenNoCategoriesExist() throws Exception {
            // When
            mockMvc.perform(get(CategoryController.BASE_URL)
                            .param("page", "0")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(0));
        }

        @Test
        @DisplayName("Should return Category By name")
        void shouldReturnCategoryByName() throws Exception {
            // Given
            Set<Category> categories = Set.of(createCategory("IPA"), createCategory("Lager"));
            repository.saveAll(categories);

            // When
            mockMvc.perform(get(CategoryController.BASE_URL)
                            .param("description", "IPA")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].description").value(containsInAnyOrder("IPA")));
        }
    }

    @Nested
    @DisplayName("Duplicate Categories Tests")
    class DuplicateCategoriesTests {
        @Test
        @DisplayName("Should reject duplicate category description (case-insensitive)")
        void shouldRejectDuplicateCategoryDescription() throws Exception {
            // Given
            repository.save(createCategory("IPA"));

            String duplicateJson = createCategoryJson("ipa"); // different case

            // When / Then
            mockMvc.perform(post(CategoryController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(duplicateJson))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Resource already exists"))
                    .andExpect(jsonPath("$.detail").value(containsString("ipa")));
        }
    }

    @Nested
    @DisplayName("Get Category by id Test")
    class GetCategoryByIdTests {
        @Test
        @DisplayName("Should return category by id")
        void shouldReturnCategoryById() throws Exception {
            // Given
            Category saved = repository.save(createCategory("IPA"));

            // When / Then
            mockMvc.perform(get(CategoryController.BASE_URL + "/" + saved.getId())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                    .andExpect(jsonPath("$.description").value("IPA"));
        }

        @Test
        @DisplayName("Should return 404 when category does not exist")
        void shouldReturn404WhenCategoryDoesNotExist() throws Exception {
            // Given
            UUID missingId = UUID.randomUUID();

            // When / Then
            mockMvc.perform(get(CategoryController.BASE_URL + "/" + missingId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 when category id is invalid UUID")
        void shouldReturn400WhenCategoryIdIsInvalid() throws Exception {
            // Given
            String invalidId = "not-a-valid-uuid";

            // When / Then
            mockMvc.perform(get(CategoryController.BASE_URL + "/" + invalidId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Delete Category by id Test")
    class DeleteCategoryByIdTests {
        @Test
        @DisplayName("Should delete category by id")
        void shouldDeleteCategoryById() throws Exception {
            // Given
            Category saved = repository.save(createCategory("IPA"));
            UUID id = saved.getId();

            // When / Then
            mockMvc.perform(delete(CategoryController.BASE_URL + "/" + id))
                    // typicky 204
                    .andExpect(status().isNoContent());

            // Then - verify entity gone
            assertThat(repository.findById(id)).isEmpty();
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existing category")
        void shouldReturn404WhenDeletingNonExistingCategory() throws Exception {
            // Given
            UUID missingId = UUID.randomUUID();

            // When / Then
            mockMvc.perform(delete(CategoryController.BASE_URL + "/" + missingId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 when deleting with invalid UUID")
        void shouldReturn400WhenDeletingWithInvalidUuid() throws Exception {
            // Given
            String invalidId = "not-a-valid-uuid";

            // When / Then
            mockMvc.perform(delete(CategoryController.BASE_URL + "/" + invalidId))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Update Category by id Test")
    class UpdateCategoryByIdTests {

        @Test
        @DisplayName("Should update category description successfully")
        void shouldUpdateCategoryDescription() throws Exception {
            // Given
            Category saved = repository.save(createCategory("IPA"));
            UUID id = saved.getId();
            String updateJson = createCategoryJson("Craft");

            // When / Then
            mockMvc.perform(put(CategoryController.BASE_URL + "/" + id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            // Then - verify in DB (dôležité pri 204)
            em.flush();
            em.clear();

            Category updated = em.find(Category.class, id);
            assertThat(updated).isNotNull();
            assertThat(updated.getDescription()).isEqualTo("Craft");
        }

        @Test
        @DisplayName("Should return 404 when updating non-existing category")
        void shouldReturn404WhenUpdatingNonExistingCategory() throws Exception {
            // Given
            UUID missingId = UUID.randomUUID();
            String updateJson = createCategoryJson("Craft");

            // When / Then
            mockMvc.perform(put(CategoryController.BASE_URL + "/" + missingId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 when updating with invalid UUID")
        void shouldReturn400WhenUpdatingWithInvalidUuid() throws Exception {
            // Given
            String invalidId = "not-a-valid-uuid";
            String updateJson = createCategoryJson("Craft");

            // When / Then
            mockMvc.perform(put(CategoryController.BASE_URL + "/" + invalidId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should reject update when description is empty")
        void shouldRejectUpdateWhenDescriptionIsEmpty() throws Exception {
            // Given
            Category saved = repository.save(createCategory("IPA"));
            UUID id = saved.getId();

            String invalidJson = createCategoryJson("");

            // When / Then
            mockMvc.perform(put(CategoryController.BASE_URL + "/" + id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", containsString("Validation failed")));
        }

        @Test
        @DisplayName("Should reject update when description is too long (256 chars)")
        void shouldRejectUpdateWhenDescriptionTooLong() throws Exception {
            // Given
            Category saved = repository.save(createCategory("IPA"));
            UUID id = saved.getId();

            String tooLong = "a".repeat(256);
            String invalidJson = createCategoryJson(tooLong);

            // When / Then
            mockMvc.perform(put(CategoryController.BASE_URL + "/" + id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", containsString("Validation failed")));
        }
    }


}
