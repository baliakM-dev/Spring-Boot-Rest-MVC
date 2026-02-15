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
import static org.hamcrest.Matchers.containsInAnyOrder;
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

// ==================== JSON Builders ====================

    /**
     * Creates JSON for beer creation/update.
     * ✅ OPRAVENÉ: Správne handling null quantityOnHand
     */
    private String createBeerJson(String beerName, String upc, Integer quantityOnHand, BigDecimal price) {
        String priceJson = (price == null) ? "0.00" : price.toString();
        String qtyJson = (quantityOnHand == null) ? "null" : quantityOnHand.toString();

        return """
                 {
                 "beerName": "%s",
                 "upc": "%s",
                 "quantityOnHand": %s,
                 "price": %s
                 }
                """.formatted(escapeJson(beerName), escapeJson(upc), qtyJson, priceJson);
    }

    /**
     * Creates JSON for beer update.
     * ✅ OPRAVENÉ: Správne handling null quantityOnHand
     */
    private String updateBeerJson(String beerName, String upc, Integer quantityOnHand, BigDecimal price) {
        String priceJson = (price == null) ? "0.00" : price.toString();
        String qtyJson = (quantityOnHand == null) ? "null" : quantityOnHand.toString();

        return """
                {
                "beerName": "%s",
                "upc": "%s",
                "quantityOnHand": %s,
                "price": %s
                }
                """.formatted(escapeJson(beerName), escapeJson(upc), qtyJson, priceJson);
    }

    /**
     * Creates JSON for beer with categories.
     * ✅ OPRAVENÉ: Správne handling null quantityOnHand
     */
    private String createBeerWithCategories(String beerName, String upc, Integer quantityOnHand,
                                            BigDecimal price, Set<UUID> categoryIds) {
        String priceJson = (price == null) ? "0.00" : price.toString();
        String qtyJson = (quantityOnHand == null) ? "null" : quantityOnHand.toString();

        String categoryIdsJson = (categoryIds == null || categoryIds.isEmpty()) ? "" :
                categoryIds.stream()
                        .map(UUID::toString)
                        .map(id -> "\"" + id + "\"")
                        .collect(Collectors.joining(", "));

        return """
                {
                "beerName": "%s",
                "upc": "%s",
                "quantityOnHand": %s,
                "price": %s,
                "categoryIds": [%s]
                }
                """.formatted(escapeJson(beerName),
                escapeJson(upc),
                qtyJson,
                priceJson,
                categoryIdsJson);
    }

    private String createCategoryJson(String description) {
        return """
                 {
                 "description": "%s"
                 }
                """.formatted(description);
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
    @DisplayName("Edge Cases - Multiple Categories and retrieve beer by id")
    class EdgeCasesMultipleCategories {

        @Test
        @DisplayName("Should create beer with multiple categories (2 categories) and retrieve by id")
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

            // Retrieve
            mockMvc.perform(get(location)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.beerName").value("Test Beer"))
                    .andExpect(jsonPath("$.upc").value("123456"))
                    .andExpect(jsonPath("$.price").value(10.99))
                    .andExpect(jsonPath("$.quantityOnHand").value(100))
                    .andExpect(jsonPath("$.categories").isArray())
                    .andExpect(jsonPath("$.categories.length()").value(2))
                    .andExpect(jsonPath("$.categories[*].description", containsInAnyOrder("IPA", "Craft")));
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

            // Retrieve
            mockMvc.perform(get(location)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.beerName").value("Test Beer"))
                    .andExpect(jsonPath("$.upc").value("123456"))
                    .andExpect(jsonPath("$.quantityOnHand").value(100))
                    .andExpect(jsonPath("$.categories.length()").value(3))
                    .andExpect(jsonPath("$.categories[*].description", containsInAnyOrder("IPA", "Craft", "Premium")));
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

            // Retrieve
            mockMvc.perform(get(location)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.beerName").value("Test Beer"))
                    .andExpect(jsonPath("$.upc").value("123456"))
                    .andExpect(jsonPath("$.quantityOnHand").value(100))
                    .andExpect(jsonPath("$.categories").isArray())
                    .andExpect(jsonPath("$.categories.length()").value(0));
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

    @Nested
    @DisplayName("Get all beers Tests")
    class GetAllBeersTests {

        @Test
        @DisplayName("Should return all Beers")
        void shouldReturnAllBeers() throws Exception {
            // Given
            repository.save(createBeer("Beer A", "111", 10, new BigDecimal("2.00")));
            repository.save(createBeer("Beer B", "222", 20, new BigDecimal("3.00")));
            repository.save(createBeer("Beer C", "333", 30, new BigDecimal("4.00")));

            // When/Then
            mockMvc.perform(get(BeerController.BASE_URL)
                            .param("page", "0")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(3))
                    .andExpect(jsonPath("$.page.totalElements").value(3))
                    .andExpect(jsonPath("$.page.number").value(0))
                    .andExpect(jsonPath("$.page.size").value(10));
        }

        @Test
        @DisplayName("Should return empty list when no Beers exist")
        void shouldReturnEmptyListWhenNoBeersExist() throws Exception {
            // Given - setUp() already deletes

            // When/Then
            mockMvc.perform(get(BeerController.BASE_URL)
                            .param("page", "0")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.page.totalElements").value(0))
                    .andExpect(jsonPath("$.page.totalPages").value(0));
        }

        @Test
        @DisplayName("Should return Beers sorted by name")
        void shouldReturnBeersFilteredByName() throws Exception {
            // Given
            repository.save(createBeer("Beer A", "111", 10, new BigDecimal("2.00")));
            repository.save(createBeer("Beer B", "222", 20, new BigDecimal("3.00")));
            repository.save(createBeer("Beer C", "333", 30, new BigDecimal("4.00")));

            // When/Then
            mockMvc.perform(get(BeerController.BASE_URL)
                            .param("beerName", "beer A")
                            .param("page", "0")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.page.totalElements").value(1));
        }

        @Test
        @DisplayName("Should return Beers sorted by upc")
        void shouldReturnBeersSortedByUpc() throws Exception {
            // Given
            repository.save(createBeer("Beer A", "111", 10, new BigDecimal("2.00")));
            repository.save(createBeer("Beer B", "222", 20, new BigDecimal("3.00")));
            repository.save(createBeer("Beer C", "333", 30, new BigDecimal("4.00")));

            // When/Then
            mockMvc.perform(get(BeerController.BASE_URL)
                            .param("upc", "111")
                            .param("page", "0")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.page.totalElements").value(1));
        }

        @Test
        @DisplayName("Should return Beers sorted by price")
        void shouldReturnBeersSortedByNameAndUpc() throws Exception {
            // Given
            repository.save(createBeer("Beer A", "111", 10, new BigDecimal("2.00")));
            repository.save(createBeer("Beer B", "222", 20, new BigDecimal("3.00")));
            repository.save(createBeer("Beer C", "333", 30, new BigDecimal("4.00")));

            // When/Then
            mockMvc.perform(get(BeerController.BASE_URL)
                            .param("beerName", "beer A")
                            .param("upc", "111")
                            .param("page", "0")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.page.totalElements").value(1));
        }
    }

    @Nested
    @DisplayName("Get Beer By Id Tests")
    class GetBeerByIdTests {

        @Test
        @DisplayName("Should throw exception when Beer does not exist")
        void shouldThrowExceptionWhenBeerDoesNotExist() throws Exception {
            // Given
            UUID invalidId1 = UUID.randomUUID();

            // Then
            mockMvc.perform(get(BeerController.BASE_URL + "/" + invalidId1)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Resource not found"));
        }
    }


    @Nested
    @DisplayName("Update beer by ID tests")
    class UpdateBeerByIdTests {

        @Test
        @DisplayName("Should successfully update beer with all fields")
        void shouldUpdateBeerById() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Original Beer", "111", 10, new BigDecimal("2.00")));
            String updateBeerJson = updateBeerJson("Updated Beer", "222", 50, new BigDecimal("5.99"));

            // When - Update Beer
            mockMvc.perform(put(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateBeerJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(savedBeer.getId().toString()))
                    .andExpect(jsonPath("$.beerName").value("Updated Beer"))
                    .andExpect(jsonPath("$.upc").value("222"))
                    .andExpect(jsonPath("$.quantityOnHand").value(50))
                    .andExpect(jsonPath("$.price").value(5.99));

            // Then - Verify in database
            Beer updatedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(updatedBeer).isNotNull();
            assertThat(updatedBeer.getBeerName()).isEqualTo("Updated Beer");
            assertThat(updatedBeer.getUpc()).isEqualTo("222");
            assertThat(updatedBeer.getQuantityOnHand()).isEqualTo(50);
            assertThat(updatedBeer.getPrice()).isEqualByComparingTo(new BigDecimal("5.99"));
        }

        @Test
        @DisplayName("Should allow updating beer to same name with different case")
        void shouldAllowUpdatingToSameNameDifferentCase() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));
            String updateJson = updateBeerJson("TEST BEER", "222", 20, new BigDecimal("3.00"));

            // When/Then
            mockMvc.perform(put(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.beerName").value("TEST BEER"))
                    .andExpect(jsonPath("$.upc").value("222"))
                    .andExpect(jsonPath("$.quantityOnHand").value(20))
                    .andExpect(jsonPath("$.price").value(3.0));

            // Verify in database
            Beer updatedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(updatedBeer.getBeerName()).isEqualTo("TEST BEER");
        }

        @Test
        @DisplayName("Should allow updating beer keeping same name")
        void shouldAllowUpdatingWithSameName() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));
            String updateJson = updateBeerJson("Test Beer", "222", 20, new BigDecimal("3.00"));

            // When/Then - Should succeed, name stays the same
            mockMvc.perform(put(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.beerName").value("Test Beer"))
                    .andExpect(jsonPath("$.upc").value("222"));
        }

        @Test
        @DisplayName("Should return 404 when updating non-existent beer")
        void shouldReturnNotFoundWhenBeerDoesNotExist() throws Exception {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            String updateJson = updateBeerJson("New Name", "999", 50, new BigDecimal("15.99"));

            // When/Then
            mockMvc.perform(put(BeerController.BASE_URL + "/" + nonExistentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Resource not found"))
                    .andExpect(jsonPath("$.detail").value(containsString("Beer")))
                    .andExpect(jsonPath("$.detail").value(containsString(nonExistentId.toString())));
        }

        @Test
        @DisplayName("Should return 400 when updating with invalid JSON")
        void shouldReturnBadRequestWhenInvalidJson() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Beer A", "111", 10, new BigDecimal("2.00")));
            String invalidJson = "{ invalid json }";

            // When/Then
            mockMvc.perform(put(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid JSON input"));
        }

        @Test
        @DisplayName("Should return 409 when updating to existing beer name")
        void shouldReturnConflictWhenBeerNameAlreadyExists() throws Exception {
            // Given - Two different beers
            Beer existingBeer = repository.save(createBeer("Existing Beer", "111", 10, new BigDecimal("2.00")));
            Beer beerToUpdate = repository.save(createBeer("Beer To Update", "222", 20, new BigDecimal("3.00")));

            // Try to update second beer to have the same name as first
            String updateJson = updateBeerJson("Existing Beer", "333", 30, new BigDecimal("4.00"));

            // When/Then
            mockMvc.perform(put(BeerController.BASE_URL + "/" + beerToUpdate.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Resource already exists"))
                    .andExpect(jsonPath("$.detail").value(containsString("Beer")))
                    .andExpect(jsonPath("$.detail").value(containsString("Existing Beer")));

            // Verify original beer was not changed
            Beer originalBeer = em.find(Beer.class, beerToUpdate.getId());
            assertThat(originalBeer.getBeerName()).isEqualTo("Beer To Update");
        }

        @Test
        @DisplayName("Should return 409 when updating to existing name with different case")
        void shouldReturnConflictWhenUpdatingToExistingNameDifferentCase() throws Exception {
            // Given
            Beer existingBeer = repository.save(createBeer("Existing Beer", "111", 10, new BigDecimal("2.00")));
            Beer beerToUpdate = repository.save(createBeer("Beer To Update", "222", 20, new BigDecimal("3.00")));

            String updateJson = updateBeerJson("EXISTING BEER", "333", 30, new BigDecimal("4.00"));

            // When/Then - Should fail because name exists (case-insensitive)
            mockMvc.perform(put(BeerController.BASE_URL + "/" + beerToUpdate.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Resource already exists"));
        }

        @Test
        @DisplayName("Should return 400 when updating with negative price")
        void shouldReturnBadRequestWhenNegativePrice() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));
            String updateJson = updateBeerJson("Test Beer", "111", 10, new BigDecimal("-5.99"));

            // When/Then
            mockMvc.perform(put(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.price").exists())
                    .andExpect(jsonPath("$.errors.price").value(containsString("positive")));
        }

        @Test
        @DisplayName("Should return 400 when updating with zero price")
        void shouldReturnBadRequestWhenZeroPrice() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));
            String updateJson = updateBeerJson("Test Beer", "111", 10, BigDecimal.ZERO);

            // When/Then
            mockMvc.perform(put(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.price").exists())
                    .andExpect(jsonPath("$.errors.price").value(containsString("positive")));
        }

        @Test
        @DisplayName("Should return 400 when updating with empty beer name")
        void shouldReturnBadRequestWhenEmptyBeerName() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));
            String updateJson = updateBeerJson("", "111", 10, new BigDecimal("2.00"));

            // When/Then
            mockMvc.perform(put(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.beerName").exists());
        }

        @Test
        @DisplayName("Should return 400 when updating with beer name exceeding max length")
        void shouldReturnBadRequestWhenBeerNameTooLong() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));
            String tooLongName = "A".repeat(51); // Max is 50
            String updateJson = updateBeerJson(tooLongName, "111", 10, new BigDecimal("2.00"));

            // When/Then
            mockMvc.perform(put(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.beerName").exists())
                    .andExpect(jsonPath("$.errors.beerName").value(containsString("50")));
        }

        @Test
        @DisplayName("Should return 400 when updating with empty UPC")
        void shouldReturnBadRequestWhenEmptyUpc() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));
            String updateJson = updateBeerJson("Test Beer", "", 10, new BigDecimal("2.00"));

            // When/Then
            mockMvc.perform(put(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.upc").exists());
        }

        @Test
        @DisplayName("Should return 400 when updating with UPC exceeding max length")
        void shouldReturnBadRequestWhenUpcTooLong() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));
            String tooLongUpc = "1".repeat(51); // Max is 50
            String updateJson = updateBeerJson("Test Beer", tooLongUpc, 10, new BigDecimal("2.00"));

            // When/Then
            mockMvc.perform(put(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.upc").exists())
                    .andExpect(jsonPath("$.errors.upc").value(containsString("50")));
        }

        @Test
        @DisplayName("Should accept very small positive price when updating")
        void shouldAcceptVerySmallPositivePriceOnUpdate() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));
            String updateJson = updateBeerJson("Test Beer", "111", 10, new BigDecimal("0.01"));

            // When/Then
            mockMvc.perform(put(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.price").value(0.01));

            // Verify in database
            Beer updatedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(updatedBeer.getPrice()).isEqualByComparingTo(new BigDecimal("0.01"));
        }

        @Test
        @DisplayName("Should accept very large price when updating")
        void shouldAcceptVeryLargePriceOnUpdate() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));
            String updateJson = updateBeerJson("Test Beer", "111", 10, new BigDecimal("999999.99"));

            // When/Then
            mockMvc.perform(put(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.price").value(999999.99));

            // Verify in database
            Beer updatedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(updatedBeer.getPrice()).isEqualByComparingTo(new BigDecimal("999999.99"));
        }

        @Test
        @DisplayName("Should update quantityOnHand to zero")
        void shouldUpdateQuantityOnHandToZero() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 100, new BigDecimal("2.00")));
            String updateJson = updateBeerJson("Test Beer", "111", 0, new BigDecimal("2.00"));

            // When/Then
            mockMvc.perform(put(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantityOnHand").value(0));

            // Verify in database
            Beer updatedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(updatedBeer.getQuantityOnHand()).isZero();
        }

        @Test
        @DisplayName("Should update beer at maximum field lengths")
        void shouldUpdateBeerAtMaxFieldLengths() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));
            String maxLengthName = "A".repeat(50);
            String maxLengthUpc = "1".repeat(50);
            String updateJson = updateBeerJson(maxLengthName, maxLengthUpc, 100, new BigDecimal("10.99"));

            // When/Then
            mockMvc.perform(put(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.beerName").value(maxLengthName))
                    .andExpect(jsonPath("$.upc").value(maxLengthUpc));

            // Verify in database
            Beer updatedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(updatedBeer.getBeerName()).hasSize(50);
            assertThat(updatedBeer.getUpc()).hasSize(50);
        }

        @Test
        @DisplayName("Should return 400 when updating with malformed UUID in path")
        void shouldReturnBadRequestWhenMalformedUuidInPath() throws Exception {
            // Given
            String updateJson = updateBeerJson("Test Beer", "111", 10, new BigDecimal("2.00"));
            String malformedUuid = "not-a-valid-uuid";

            // When/Then
            mockMvc.perform(put(BeerController.BASE_URL + "/" + malformedUuid)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should preserve createdAt timestamp after update")
        void shouldPreserveCreatedAtTimestampAfterUpdate() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Original Beer", "111", 10, new BigDecimal("2.00")));
            em.flush();
            em.clear();

            Beer beforeUpdate = em.find(Beer.class, savedBeer.getId());
            var originalCreatedAt = beforeUpdate.getCreatedAt();

            String updateJson = updateBeerJson("Updated Beer", "222", 20, new BigDecimal("3.00"));

            // When
            mockMvc.perform(put(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isOk());

            // Then - createdAt should remain the same
            em.flush();
            em.clear();
            Beer afterUpdate = em.find(Beer.class, savedBeer.getId());

            assertThat(afterUpdate.getCreatedAt()).isEqualTo(originalCreatedAt);
            assertThat(afterUpdate.getUpdatedAt()).isNotNull();
            assertThat(afterUpdate.getUpdatedAt()).isAfterOrEqualTo(originalCreatedAt);
        }
    }

    // ==================== PATCH BEER INTEGRATION TESTY ====================

    @Nested
    @DisplayName("Patch beer by ID tests")
    class PatchBeerByIdTests {

        /**
         * Helper method to create JSON for partial beer patch.
         * Null values are explicitly included in JSON.
         */
        private String patchBeerJson(String beerName, String upc, Integer quantityOnHand, BigDecimal price) {
            StringBuilder json = new StringBuilder("{");
            boolean needsComma = false;

            if (beerName != null) {
                json.append("\"beerName\": \"").append(escapeJson(beerName)).append("\"");
                needsComma = true;
            }

            if (upc != null) {
                if (needsComma) json.append(", ");
                json.append("\"upc\": \"").append(escapeJson(upc)).append("\"");
                needsComma = true;
            }

            if (quantityOnHand != null) {
                if (needsComma) json.append(", ");
                json.append("\"quantityOnHand\": ").append(quantityOnHand);
                needsComma = true;
            }

            if (price != null) {
                if (needsComma) json.append(", ");
                json.append("\"price\": ").append(price);
            }

            json.append("}");
            return json.toString();
        }

        @Test
        @DisplayName("Should patch only beer name")
        void shouldPatchOnlyBeerName() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Original Beer", "111", 10, new BigDecimal("2.00")));
            String patchJson = patchBeerJson("Patched Name", null, null, null);

            // When
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(savedBeer.getId().toString()))
                    .andExpect(jsonPath("$.beerName").value("Patched Name"))
                    .andExpect(jsonPath("$.upc").value("111"))
                    .andExpect(jsonPath("$.quantityOnHand").value(10))
                    .andExpect(jsonPath("$.price").value(2.0));

            // Then - Verify in database
            Beer patchedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(patchedBeer.getBeerName()).isEqualTo("Patched Name");
            assertThat(patchedBeer.getUpc()).isEqualTo("111"); // unchanged
            assertThat(patchedBeer.getQuantityOnHand()).isEqualTo(10); // unchanged
            assertThat(patchedBeer.getPrice()).isEqualByComparingTo(new BigDecimal("2.00")); // unchanged
        }

        @Test
        @DisplayName("Should patch only UPC")
        void shouldPatchOnlyUpc() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Original Beer", "111", 10, new BigDecimal("2.00")));
            String patchJson = patchBeerJson(null, "999", null, null);

            // When
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.beerName").value("Original Beer"))
                    .andExpect(jsonPath("$.upc").value("999"))
                    .andExpect(jsonPath("$.quantityOnHand").value(10))
                    .andExpect(jsonPath("$.price").value(2.0));

            // Then
            Beer patchedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(patchedBeer.getUpc()).isEqualTo("999");
            assertThat(patchedBeer.getBeerName()).isEqualTo("Original Beer"); // unchanged
        }

        @Test
        @DisplayName("Should patch only quantity on hand")
        void shouldPatchOnlyQuantityOnHand() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Original Beer", "111", 10, new BigDecimal("2.00")));
            String patchJson = patchBeerJson(null, null, 500, null);

            // When
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.beerName").value("Original Beer"))
                    .andExpect(jsonPath("$.upc").value("111"))
                    .andExpect(jsonPath("$.quantityOnHand").value(500))
                    .andExpect(jsonPath("$.price").value(2.0));

            // Then
            Beer patchedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(patchedBeer.getQuantityOnHand()).isEqualTo(500);
        }

        @Test
        @DisplayName("Should patch only price")
        void shouldPatchOnlyPrice() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Original Beer", "111", 10, new BigDecimal("2.00")));
            String patchJson = patchBeerJson(null, null, null, new BigDecimal("99.99"));

            // When
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.beerName").value("Original Beer"))
                    .andExpect(jsonPath("$.upc").value("111"))
                    .andExpect(jsonPath("$.quantityOnHand").value(10))
                    .andExpect(jsonPath("$.price").value(99.99));

            // Then
            Beer patchedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(patchedBeer.getPrice()).isEqualByComparingTo(new BigDecimal("99.99"));
        }

        @Test
        @DisplayName("Should patch multiple fields (name and price)")
        void shouldPatchMultipleFields() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Original Beer", "111", 10, new BigDecimal("2.00")));
            String patchJson = patchBeerJson("Multi Patch", null, null, new BigDecimal("25.50"));

            // When
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.beerName").value("Multi Patch"))
                    .andExpect(jsonPath("$.upc").value("111")) // unchanged
                    .andExpect(jsonPath("$.quantityOnHand").value(10)) // unchanged
                    .andExpect(jsonPath("$.price").value(25.50));

            // Then
            Beer patchedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(patchedBeer.getBeerName()).isEqualTo("Multi Patch");
            assertThat(patchedBeer.getPrice()).isEqualByComparingTo(new BigDecimal("25.50"));
            assertThat(patchedBeer.getUpc()).isEqualTo("111");
            assertThat(patchedBeer.getQuantityOnHand()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should patch all fields")
        void shouldPatchAllFields() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Original Beer", "111", 10, new BigDecimal("2.00")));
            String patchJson = patchBeerJson("Fully Patched", "999", 300, new BigDecimal("50.00"));

            // When
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(savedBeer.getId().toString()))
                    .andExpect(jsonPath("$.beerName").value("Fully Patched"))
                    .andExpect(jsonPath("$.upc").value("999"))
                    .andExpect(jsonPath("$.quantityOnHand").value(300))
                    .andExpect(jsonPath("$.price").value(50.0));

            // Then
            Beer patchedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(patchedBeer.getBeerName()).isEqualTo("Fully Patched");
            assertThat(patchedBeer.getUpc()).isEqualTo("999");
            assertThat(patchedBeer.getQuantityOnHand()).isEqualTo(300);
            assertThat(patchedBeer.getPrice()).isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("Should allow patching to same name with different case")
        void shouldAllowPatchingToSameNameDifferentCase() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));
            String patchJson = patchBeerJson("TEST BEER", null, null, null);

            // When/Then
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.beerName").value("TEST BEER"));

            // Verify in database
            Beer patchedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(patchedBeer.getBeerName()).isEqualTo("TEST BEER");
        }

        @Test
        @DisplayName("Should handle empty patch (all fields null)")
        void shouldHandleEmptyPatch() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Original Beer", "111", 10, new BigDecimal("2.00")));
            String emptyPatchJson = "{}";

            // When - Even with empty patch, beer should be returned unchanged
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(emptyPatchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.beerName").value("Original Beer"))
                    .andExpect(jsonPath("$.upc").value("111"))
                    .andExpect(jsonPath("$.quantityOnHand").value(10))
                    .andExpect(jsonPath("$.price").value(2.0));

            // Then - Verify nothing changed
            Beer patchedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(patchedBeer.getBeerName()).isEqualTo("Original Beer");
            assertThat(patchedBeer.getUpc()).isEqualTo("111");
            assertThat(patchedBeer.getQuantityOnHand()).isEqualTo(10);
            assertThat(patchedBeer.getPrice()).isEqualByComparingTo(new BigDecimal("2.00"));
        }

        @Test
        @DisplayName("Should return 404 when patching non-existent beer")
        void shouldReturnNotFoundWhenPatchingNonExistentBeer() throws Exception {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            String patchJson = patchBeerJson("New Name", null, null, null);

            // When/Then
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + nonExistentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Resource not found"))
                    .andExpect(jsonPath("$.detail").value(containsString("Beer")))
                    .andExpect(jsonPath("$.detail").value(containsString(nonExistentId.toString())));
        }

        @Test
        @DisplayName("Should return 409 when patching to existing beer name")
        void shouldReturnConflictWhenPatchingToExistingName() throws Exception {
            // Given - Two different beers
            Beer existingBeer = repository.save(createBeer("Existing Beer", "111", 10, new BigDecimal("2.00")));
            Beer beerToPatch = repository.save(createBeer("Beer To Patch", "222", 20, new BigDecimal("3.00")));

            String patchJson = patchBeerJson("Existing Beer", null, null, null);

            // When/Then
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + beerToPatch.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Resource already exists"))
                    .andExpect(jsonPath("$.detail").value(containsString("Beer")))
                    .andExpect(jsonPath("$.detail").value(containsString("Existing Beer")));

            // Verify original beer was not changed
            Beer originalBeer = em.find(Beer.class, beerToPatch.getId());
            assertThat(originalBeer.getBeerName()).isEqualTo("Beer To Patch");
        }

        @Test
        @DisplayName("Should return 409 when patching to existing name with different case")
        void shouldReturnConflictWhenPatchingToExistingNameDifferentCase() throws Exception {
            // Given
            Beer existingBeer = repository.save(createBeer("Existing Beer", "111", 10, new BigDecimal("2.00")));
            Beer beerToPatch = repository.save(createBeer("Beer To Patch", "222", 20, new BigDecimal("3.00")));

            String patchJson = patchBeerJson("EXISTING BEER", null, null, null);

            // When/Then - Should fail because name exists (case-insensitive)
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + beerToPatch.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Resource already exists"));
        }

        @Test
        @DisplayName("Should return 400 when patching with invalid JSON")
        void shouldReturnBadRequestWhenInvalidJson() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));
            String invalidJson = "{ invalid json }";

            // When/Then
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid JSON input"));
        }

        @Test
        @DisplayName("Should patch quantity to zero")
        void shouldPatchQuantityToZero() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 100, new BigDecimal("2.00")));
            String patchJson = patchBeerJson(null, null, 0, null);

            // When
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantityOnHand").value(0));

            // Then
            Beer patchedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(patchedBeer.getQuantityOnHand()).isZero();
        }

        @Test
        @DisplayName("Should patch beer name to maximum length")
        void shouldPatchBeerNameToMaxLength() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Short Name", "111", 10, new BigDecimal("2.00")));
            String maxLengthName = "A".repeat(50);
            String patchJson = patchBeerJson(maxLengthName, null, null, null);

            // When
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.beerName").value(maxLengthName));

            // Then
            Beer patchedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(patchedBeer.getBeerName()).hasSize(50);
        }

        @Test
        @DisplayName("Should patch UPC to maximum length")
        void shouldPatchUpcToMaxLength() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));
            String maxLengthUpc = "9".repeat(50);
            String patchJson = patchBeerJson(null, maxLengthUpc, null, null);

            // When
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upc").value(maxLengthUpc));

            // Then
            Beer patchedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(patchedBeer.getUpc()).hasSize(50);
        }

        @Test
        @DisplayName("Should patch price to very small positive value")
        void shouldPatchPriceToVerySmallValue() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));
            String patchJson = patchBeerJson(null, null, null, new BigDecimal("0.01"));

            // When
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.price").value(0.01));

            // Then
            Beer patchedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(patchedBeer.getPrice()).isEqualByComparingTo(new BigDecimal("0.01"));
        }

        @Test
        @DisplayName("Should patch price to very large value")
        void shouldPatchPriceToVeryLargeValue() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Test Beer", "111", 10, new BigDecimal("2.00")));
            String patchJson = patchBeerJson(null, null, null, new BigDecimal("999999.99"));

            // When
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.price").value(999999.99));

            // Then
            Beer patchedBeer = em.find(Beer.class, savedBeer.getId());
            assertThat(patchedBeer.getPrice()).isEqualByComparingTo(new BigDecimal("999999.99"));
        }

        @Test
        @DisplayName("Should return 400 when patching with malformed UUID in path")
        void shouldReturnBadRequestWhenMalformedUuidInPath() throws Exception {
            // Given
            String patchJson = patchBeerJson("Test Beer", null, null, null);
            String malformedUuid = "not-a-valid-uuid";

            // When/Then
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + malformedUuid)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should preserve createdAt timestamp after patch")
        void shouldPreserveCreatedAtTimestampAfterPatch() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Original Beer", "111", 10, new BigDecimal("2.00")));
            em.flush();
            em.clear();

            Beer beforePatch = em.find(Beer.class, savedBeer.getId());
            var originalCreatedAt = beforePatch.getCreatedAt();

            String patchJson = patchBeerJson("Patched Beer", null, null, null);

            // When
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + savedBeer.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk());

            // Then - createdAt should remain the same
            em.flush();
            em.clear();
            Beer afterPatch = em.find(Beer.class, savedBeer.getId());

            assertThat(afterPatch.getCreatedAt()).isEqualTo(originalCreatedAt);
            assertThat(afterPatch.getUpdatedAt()).isNotNull();
            assertThat(afterPatch.getUpdatedAt()).isAfterOrEqualTo(originalCreatedAt);
        }

        @Test
        @DisplayName("Should preserve ID after patch")
        void shouldPreserveIdAfterPatch() throws Exception {
            // Given
            Beer savedBeer = repository.save(createBeer("Original Beer", "111", 10, new BigDecimal("2.00")));
            UUID originalId = savedBeer.getId();

            String patchJson = patchBeerJson("Patched Beer", "999", 100, new BigDecimal("50.00"));

            // When
            mockMvc.perform(patch(BeerController.BASE_URL + "/" + originalId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(originalId.toString()));

            // Then
            Beer patchedBeer = em.find(Beer.class, originalId);
            assertThat(patchedBeer).isNotNull();
            assertThat(patchedBeer.getId()).isEqualTo(originalId);
        }
    }
}
