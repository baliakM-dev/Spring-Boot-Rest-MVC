package com.restmvc.beer_store.integreation;

import com.restmvc.beer_store.controllers.BeerController;
import com.restmvc.beer_store.entities.Beer;
import com.restmvc.beer_store.entities.Category;
import com.restmvc.beer_store.repositories.BeerRepository;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for beer API endpoints.
 * Tests the full stack: Controller -> Service -> Repository.
 * Uses Testcontainers for an isolated MySQL database per test run.
 *
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Testcontainers
public class BeerIntegrationTest {

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
    private BeerRepository repository;
    @Autowired
    private EntityManager em;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        categoryRepository.deleteAll();
    }

    // ==================== Test Fixtures ====================
    private Beer createBeer(String name, String upc, Integer quantityOnHand, BigDecimal price) {
        return Beer.builder()
                .beerName(name)
                .upc(upc)
                .quantityOnHand(quantityOnHand)
                .price(price)
                .build();
    }

    private Category createCategory(String description) {
        return Category.builder().description(description).build();
    }

    private String createBeerJson(String beerName, String upc, Integer quantityOnHand, BigDecimal price) {
        String priceJson = (price == null) ? "0.00" : price.toString();

        return """
                 {
                 "beerName": "%s",
                 "upc": "%s",
                 "quantityOnHand": %d,
                 "price": %s
                 }
                """.formatted(escapeJson(beerName)
                , escapeJson(upc)
                , quantityOnHand
                , priceJson
        );
    }

    private String createCategoryJson(String description) {
        return """
                 {
                 "description": "%s"
                 }
                """.formatted(description);
    }

    private String createBeerWithCategories(String beerName, String upc, Integer quantityOnHand,
                                            BigDecimal price, Set<UUID> categoryIds) {
        String priceJson = (price == null) ? "0.00" : price.toString();

        String categoryIdsJson = (categoryIds == null || categoryIds.isEmpty()) ? "" :
                categoryIds.stream()
                        .map(UUID::toString)
                        .map(id -> "\"" + id + "\"")
                        .collect(Collectors.joining(", "));

        return """
            {
            "beerName": "%s",
            "upc": "%s",
            "quantityOnHand": %d,
            "price": %s,
            "categoryIds": [%s]
            }
            """.formatted(escapeJson(beerName),
                escapeJson(upc),
                quantityOnHand,
                priceJson,
                categoryIdsJson);  // Changed from "categories" to "categoryIds"
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
        @DisplayName("Should accept beer name at maximum length (50 chars)")
        void shouldAcceptBeerNameAtMaxLength() throws Exception {
            // Given - exactly 50 characters
            String maxLengthName = "A".repeat(50);
            String beerJson = createBeerJson(maxLengthName, "123456", 100, BigDecimal.valueOf(10.99));

            // When - Create Beer
            MvcResult result = mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(beerJson))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andReturn();

            // Then
            String location = result.getResponse().getHeader("Location");
            String beerId = location.substring(location.lastIndexOf("/") + 1);
            Beer savedBeer = em.find(Beer.class, UUID.fromString(beerId));

            assertThat(savedBeer).isNotNull();
            assertThat(savedBeer.getBeerName()).hasSize(50);
            assertThat(savedBeer.getBeerName()).isEqualTo(maxLengthName);
        }

        @Test
        @DisplayName("Should reject beer name exceeding maximum length (51 chars)")
        void shouldRejectBeerNameExceedingMaxLength() throws Exception {
            // Given - 51 characters (exceeds max)
            String tooLongName = "A".repeat(51);
            String beerJson = createBeerJson(tooLongName, "123456", 100, BigDecimal.valueOf(10.99));

            // When/Then
            mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(beerJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.beerName").exists())
                    .andExpect(jsonPath("$.errors.beerName").value(containsString("50")));
        }

        @Test
        @DisplayName("Should accept UPC at maximum length (50 chars)")
        void shouldAcceptUpcAtMaxLength() throws Exception {
            // Given - exactly 50 characters
            String maxLengthUpc = "1".repeat(50);
            String beerJson = createBeerJson("Valid Beer", maxLengthUpc, 100, BigDecimal.valueOf(10.99));

            // When - Create Beer
            MvcResult result = mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(beerJson))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andReturn();

            // Then
            String location = result.getResponse().getHeader("Location");
            String beerId = location.substring(location.lastIndexOf("/") + 1);
            Beer savedBeer = em.find(Beer.class, UUID.fromString(beerId));

            assertThat(savedBeer).isNotNull();
            assertThat(savedBeer.getUpc()).hasSize(50);
            assertThat(savedBeer.getUpc()).isEqualTo(maxLengthUpc);
        }

        @Test
        @DisplayName("Should reject UPC exceeding maximum length (51 chars)")
        void shouldRejectUpcExceedingMaxLength() throws Exception {
            // Given - 51 characters (exceeds max)
            String tooLongUpc = "1".repeat(51);
            String beerJson = createBeerJson("Valid Beer", tooLongUpc, 100, BigDecimal.valueOf(10.99));

            // When/Then
            mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(beerJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.upc").exists())
                    .andExpect(jsonPath("$.errors.upc").value(containsString("50")));
        }
    }

    @Nested
    @DisplayName("Edge Cases - Optional Fields")
    class EdgeCasesOptionalFields {

        @Test
        @DisplayName("Should accept null quantityOnHand (optional field)")
        void shouldAcceptNullQuantityOnHand() throws Exception {
            // Given
            String beerJson = createBeerJson("Test Beer", "123456", null, BigDecimal.valueOf(10.99));

            // When - Create Beer
            MvcResult result = mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(beerJson))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andReturn();

            // Then
            String location = result.getResponse().getHeader("Location");
            String beerId = location.substring(location.lastIndexOf("/") + 1);
            Beer savedBeer = em.find(Beer.class, UUID.fromString(beerId));

            assertThat(savedBeer).isNotNull();
            assertThat(savedBeer.getQuantityOnHand()).isNull();
        }

        @Test
        @DisplayName("Should accept zero quantityOnHand")
        void shouldAcceptZeroQuantityOnHand() throws Exception {
            // Given
            String beerJson = createBeerJson("Test Beer", "123456", 0, BigDecimal.valueOf(10.99));

            // When - Create Beer
            MvcResult result = mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(beerJson))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andReturn();

            // Then
            String location = result.getResponse().getHeader("Location");
            String beerId = location.substring(location.lastIndexOf("/") + 1);
            Beer savedBeer = em.find(Beer.class, UUID.fromString(beerId));

            assertThat(savedBeer).isNotNull();
            assertThat(savedBeer.getQuantityOnHand()).isZero();
        }
    }

    @Nested
    @DisplayName("Edge Cases - Price Validation")
    class EdgeCasesPriceValidation {

        @Test
        @DisplayName("Should reject negative price")
        void shouldRejectNegativePrice() throws Exception {
            // Given
            String beerJson = createBeerJson("Test Beer", "123456", 100, BigDecimal.valueOf(-5.99));

            // When/Then
            mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(beerJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.price").exists())
                    .andExpect(jsonPath("$.errors.price").value(containsString("positive")));
        }

        @Test
        @DisplayName("Should reject zero price")
        void shouldRejectZeroPrice() throws Exception {
            // Given
            String beerJson = createBeerJson("Test Beer", "123456", 100, BigDecimal.ZERO);

            // When/Then
            mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(beerJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.price").exists())
                    .andExpect(jsonPath("$.errors.price").value(containsString("positive")));
        }

        @Test
        @DisplayName("Should accept very small positive price")
        void shouldAcceptVerySmallPositivePrice() throws Exception {
            // Given
            String beerJson = createBeerJson("Test Beer", "123456", 100, new BigDecimal("0.01"));

            // When - Create Beer
            MvcResult result = mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(beerJson))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andReturn();

            // Then
            String location = result.getResponse().getHeader("Location");
            String beerId = location.substring(location.lastIndexOf("/") + 1);
            Beer savedBeer = em.find(Beer.class, UUID.fromString(beerId));

            assertThat(savedBeer).isNotNull();
            assertThat(savedBeer.getPrice()).isEqualTo(new BigDecimal("0.01"));
        }

        @Test
        @DisplayName("Should accept very large price")
        void shouldAcceptVeryLargePrice() throws Exception {
            // Given
            String beerJson = createBeerJson("Test Beer", "123456", 100, new BigDecimal("999999.99"));

            // When - Create Beer
            MvcResult result = mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(beerJson))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andReturn();

            // Then
            String location = result.getResponse().getHeader("Location");
            String beerId = location.substring(location.lastIndexOf("/") + 1);
            Beer savedBeer = em.find(Beer.class, UUID.fromString(beerId));

            assertThat(savedBeer).isNotNull();
            assertThat(savedBeer.getPrice()).isEqualTo(new BigDecimal("999999.99"));
        }
    }

    @Nested
    @DisplayName("Edge Cases - Case Insensitive Duplicate Names")
    class EdgeCasesCaseInsensitiveDuplicates {

        @Test
        @DisplayName("Should reject duplicate name with different case (uppercase)")
        void shouldRejectDuplicateNameUppercase() throws Exception {
            // Given - existing beer
            repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));

            String duplicateJson = createBeerJson("TEST BEER", "222", 50, new BigDecimal("3.00"));

            // When/Then
            mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(duplicateJson))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Resource already exists"))
                    .andExpect(jsonPath("$.detail").value(containsString("TEST BEER")));
        }

        @Test
        @DisplayName("Should reject duplicate name with different case (lowercase)")
        void shouldRejectDuplicateNameLowercase() throws Exception {
            // Given - existing beer
            repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));

            String duplicateJson = createBeerJson("test beer", "222", 50, new BigDecimal("3.00"));

            // When/Then
            mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(duplicateJson))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Resource already exists"))
                    .andExpect(jsonPath("$.detail").value(containsString("test beer")));
        }

        @Test
        @DisplayName("Should reject duplicate name with mixed case")
        void shouldRejectDuplicateNameMixedCase() throws Exception {
            // Given - existing beer
            repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));

            String duplicateJson = createBeerJson("TeSt BeEr", "222", 50, new BigDecimal("3.00"));

            // When/Then
            mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(duplicateJson))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Resource already exists"))
                    .andExpect(jsonPath("$.detail").value(containsString("TeSt BeEr")));
        }

        @Test
        @DisplayName("Should allow same UPC with different beer names")
        void shouldAllowSameUpcDifferentNames() throws Exception {
            // Given - existing beer
            repository.save(createBeer("Beer One", "123456", 10, new BigDecimal("2.00")));

            // Note: UPC validation might be added later, this tests current behavior
            String newBeerJson = createBeerJson("Beer Two", "123456", 50, new BigDecimal("3.00"));

            // When - Create Beer (currently allows duplicate UPC)
            mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(newBeerJson))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("Edge Cases - Multiple Categories")
    class EdgeCasesMultipleCategories {

        @Test
        @DisplayName("Should create beer with multiple categories (2 categories)")
        void shouldCreateBeerWithTwoCategories() throws Exception {
            // Given
            Category cat1 = categoryRepository.save(createCategory("IPA"));
            Category cat2 = categoryRepository.save(createCategory("Craft"));

            String beerJson = createBeerWithCategories(
                    "Test Beer",
                    "123456",
                    100,
                    BigDecimal.valueOf(10.99),
                    Set.of(cat1.getId(), cat2.getId())
            );

            // When - Create Beer
            MvcResult result = mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(beerJson))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andReturn();

            // Then
            String location = result.getResponse().getHeader("Location");
            String beerId = location.substring(location.lastIndexOf("/") + 1);
            Beer savedBeer = em.find(Beer.class, UUID.fromString(beerId));

            assertThat(savedBeer).isNotNull();
            assertThat(savedBeer.getCategories()).hasSize(2);
            assertThat(savedBeer.getCategories()).extracting(Category::getId)
                    .containsExactlyInAnyOrder(cat1.getId(), cat2.getId());
        }

        @Test
        @DisplayName("Should create beer with multiple categories (3 categories)")
        void shouldCreateBeerWithThreeCategories() throws Exception {
            // Given
            Category cat1 = categoryRepository.save(createCategory("IPA"));
            Category cat2 = categoryRepository.save(createCategory("Craft"));
            Category cat3 = categoryRepository.save(createCategory("Premium"));

            String beerJson = createBeerWithCategories(
                    "Test Beer",
                    "123456",
                    100,
                    BigDecimal.valueOf(10.99),
                    Set.of(cat1.getId(), cat2.getId(), cat3.getId())
            );

            // When - Create Beer
            MvcResult result = mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(beerJson))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andReturn();

            // Then
            String location = result.getResponse().getHeader("Location");
            String beerId = location.substring(location.lastIndexOf("/") + 1);
            Beer savedBeer = em.find(Beer.class, UUID.fromString(beerId));

            assertThat(savedBeer).isNotNull();
            assertThat(savedBeer.getCategories()).hasSize(3);
        }

        @Test
        @DisplayName("Should handle empty category list")
        void shouldHandleEmptyCategoryList() throws Exception {
            // Given
            String beerJson = createBeerWithCategories(
                    "Test Beer",
                    "123456",
                    100,
                    BigDecimal.valueOf(10.99),
                    Set.of()
            );

            // When - Create Beer
            MvcResult result = mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(beerJson))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andReturn();

            // Then
            String location = result.getResponse().getHeader("Location");
            String beerId = location.substring(location.lastIndexOf("/") + 1);
            Beer savedBeer = em.find(Beer.class, UUID.fromString(beerId));

            assertThat(savedBeer).isNotNull();
            assertThat(savedBeer.getCategories()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Edge Cases - Non-existent Categories")
    class EdgeCasesNonExistentCategories {

        @Test
        @DisplayName("Should reject beer with non-existent category ID")
        void shouldRejectNonExistentCategory() throws Exception {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            String beerJson = createBeerWithCategories(
                    "Test Beer",
                    "123456",
                    100,
                    BigDecimal.valueOf(10.99),
                    Set.of(nonExistentId)
            );

            // When/Then
            mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(beerJson))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Resource not found"))
                    .andExpect(jsonPath("$.detail").value(containsString("Category")))
                    .andExpect(jsonPath("$.detail").value(containsString(nonExistentId.toString())));
        }

        @Test
        @DisplayName("Should reject beer with mix of valid and invalid category IDs")
        void shouldRejectMixOfValidAndInvalidCategories() throws Exception {
            // Given
            Category validCategory = categoryRepository.save(createCategory("IPA"));
            UUID invalidId = UUID.randomUUID();

            String beerJson = createBeerWithCategories(
                    "Test Beer",
                    "123456",
                    100,
                    BigDecimal.valueOf(10.99),
                    Set.of(validCategory.getId(), invalidId)
            );

            // When/Then
            mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(beerJson))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Resource not found"))
                    .andExpect(jsonPath("$.detail").value(containsString("Category")))
                    .andExpect(jsonPath("$.detail").value(containsString(invalidId.toString())));
        }

        @Test
        @DisplayName("Should reject beer with multiple non-existent category IDs")
        void shouldRejectMultipleNonExistentCategories() throws Exception {
            // Given
            UUID invalidId1 = UUID.randomUUID();
            UUID invalidId2 = UUID.randomUUID();

            String beerJson = createBeerWithCategories(
                    "Test Beer",
                    "123456",
                    100,
                    BigDecimal.valueOf(10.99),
                    Set.of(invalidId1, invalidId2)
            );

            // When/Then
            mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(beerJson))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Resource not found"))
                    .andExpect(jsonPath("$.detail").value(containsString("Category")));

            // Should contain at least one of the invalid IDs
        }

        @Test
        @DisplayName("Should reject beer with malformed category UUID")
        void shouldRejectMalformedCategoryUuid() throws Exception {
            // Given - malformed UUID in JSON
            String malformedJson = """
                    {
                    "beerName": "Test Beer",
                    "upc": "123456",
                    "quantityOnHand": 100,
                    "price": 10.99,
                    "categoryIds": ["not-a-valid-uuid"]
                    }
                    """;

            // When/Then
            mockMvc.perform(post(BeerController.BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid JSON input"));
        }
    }
}
