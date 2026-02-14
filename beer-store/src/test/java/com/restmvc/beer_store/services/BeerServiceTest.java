package com.restmvc.beer_store.services;

import com.restmvc.beer_store.dtos.beer.BeerCreateDTO;
import com.restmvc.beer_store.entities.Beer;
import com.restmvc.beer_store.entities.Category;
import com.restmvc.beer_store.exceptions.ResourceAlreadyExistsExceptions;
import com.restmvc.beer_store.exceptions.ResourceNotFoundException;
import com.restmvc.beer_store.mappers.BeerMapper;
import com.restmvc.beer_store.repositories.BeerRepository;
import com.restmvc.beer_store.repositories.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BeerService}.
 *
 * Uses Mockito to isolate the service layer from dependencies.
 * Tests focus on business logic validation, data transformation,
 * and error handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BeerService Unit Tests")
class BeerServiceTest {

    @Mock
    private BeerRepository beerRepository;

    @Mock
    private BeerMapper beerMapper;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private BeerService beerService;

    private BeerCreateDTO validBeerDTO;
    private Beer validBeer;
    private Category category1;
    private Category category2;

    @BeforeEach
    void setUp() {
        validBeerDTO = new BeerCreateDTO(
                "Test Beer",
                "123456",
                100,
                new BigDecimal("10.99"),
                null
        );

        validBeer = Beer.builder()
                .id(UUID.randomUUID())
                .beerName("Test Beer")
                .upc("123456")
                .quantityOnHand(100)
                .price(new BigDecimal("10.99"))
                .categories(new HashSet<>())
                .build();

        category1 = Category.builder()
                .id(UUID.randomUUID())
                .description("IPA")
                .build();

        category2 = Category.builder()
                .id(UUID.randomUUID())
                .description("Craft")
                .build();
    }

    // ==================== Create Beer Tests ====================

    @Nested
    @DisplayName("Create Beer - Happy Path")
    class CreateBeerHappyPath {

        @Test
        @DisplayName("Should create beer successfully without categories")
        void shouldCreateBeerWithoutCategories() {
            // Given
            when(beerRepository.existsByBeerNameIgnoreCase(validBeerDTO.beerName())).thenReturn(false);
            when(beerMapper.dtoToBeer(validBeerDTO)).thenReturn(validBeer);
            when(beerRepository.save(validBeer)).thenReturn(validBeer);

            // When
            UUID result = beerService.createBeer(validBeerDTO);

            // Then
            assertThat(result).isEqualTo(validBeer.getId());
            verify(beerRepository).existsByBeerNameIgnoreCase("Test Beer");
            verify(beerMapper).dtoToBeer(validBeerDTO);
            verify(beerRepository).save(validBeer);
            verify(categoryRepository, never()).findAllById(anySet());
        }

        @Test
        @DisplayName("Should create beer with single category")
        void shouldCreateBeerWithSingleCategory() {
            // Given
            Set<UUID> categoryIds = Set.of(category1.getId());
            BeerCreateDTO dtoWithCategory = new BeerCreateDTO(
                    "Test Beer",
                    "123456",
                    100,
                    new BigDecimal("10.99"),
                    categoryIds
            );

            when(beerRepository.existsByBeerNameIgnoreCase(dtoWithCategory.beerName())).thenReturn(false);
            when(beerMapper.dtoToBeer(dtoWithCategory)).thenReturn(validBeer);
            when(categoryRepository.findAllById(categoryIds)).thenReturn(List.of(category1));
            when(beerRepository.save(validBeer)).thenReturn(validBeer);

            // When
            UUID result = beerService.createBeer(dtoWithCategory);

            // Then
            assertThat(result).isEqualTo(validBeer.getId());
            verify(categoryRepository).findAllById(categoryIds);
            verify(beerRepository).save(validBeer);
            assertThat(validBeer.getCategories()).hasSize(1);
        }

        @Test
        @DisplayName("Should create beer with multiple categories")
        void shouldCreateBeerWithMultipleCategories() {
            // Given
            Set<UUID> categoryIds = Set.of(category1.getId(), category2.getId());
            BeerCreateDTO dtoWithCategories = new BeerCreateDTO(
                    "Test Beer",
                    "123456",
                    100,
                    new BigDecimal("10.99"),
                    categoryIds
            );

            when(beerRepository.existsByBeerNameIgnoreCase(dtoWithCategories.beerName())).thenReturn(false);
            when(beerMapper.dtoToBeer(dtoWithCategories)).thenReturn(validBeer);
            when(categoryRepository.findAllById(categoryIds)).thenReturn(List.of(category1, category2));
            when(beerRepository.save(validBeer)).thenReturn(validBeer);

            // When
            UUID result = beerService.createBeer(dtoWithCategories);

            // Then
            assertThat(result).isEqualTo(validBeer.getId());
            verify(categoryRepository).findAllById(categoryIds);
            assertThat(validBeer.getCategories()).hasSize(2);
        }

        @Test
        @DisplayName("Should handle empty category set")
        void shouldHandleEmptyCategorySet() {
            // Given
            BeerCreateDTO dtoWithEmptyCategories = new BeerCreateDTO(
                    "Test Beer",
                    "123456",
                    100,
                    new BigDecimal("10.99"),
                    Collections.emptySet()
            );

            when(beerRepository.existsByBeerNameIgnoreCase(dtoWithEmptyCategories.beerName())).thenReturn(false);
            when(beerMapper.dtoToBeer(dtoWithEmptyCategories)).thenReturn(validBeer);
            when(beerRepository.save(validBeer)).thenReturn(validBeer);

            // When
            UUID result = beerService.createBeer(dtoWithEmptyCategories);

            // Then
            assertThat(result).isEqualTo(validBeer.getId());
            verify(categoryRepository, never()).findAllById(anySet());
        }

        @Test
        @DisplayName("Should handle null categoryIds")
        void shouldHandleNullCategoryIds() {
            // Given - validBeerDTO already has null categoryIds
            when(beerRepository.existsByBeerNameIgnoreCase(validBeerDTO.beerName())).thenReturn(false);
            when(beerMapper.dtoToBeer(validBeerDTO)).thenReturn(validBeer);
            when(beerRepository.save(validBeer)).thenReturn(validBeer);

            // When
            UUID result = beerService.createBeer(validBeerDTO);

            // Then
            assertThat(result).isEqualTo(validBeer.getId());
            verify(categoryRepository, never()).findAllById(anySet());
        }
    }

    // ==================== Validation Tests ====================

    @Nested
    @DisplayName("Create Beer - Validation")
    class CreateBeerValidation {

        @Test
        @DisplayName("Should throw exception when beer name already exists (case insensitive)")
        void shouldThrowExceptionWhenBeerNameExists() {
            // Given
            when(beerRepository.existsByBeerNameIgnoreCase(validBeerDTO.beerName())).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> beerService.createBeer(validBeerDTO))
                    .isInstanceOf(ResourceAlreadyExistsExceptions.class)
                    .hasMessageContaining("Beer")
                    .hasMessageContaining("beerName")
                    .hasMessageContaining("Test Beer");

            verify(beerRepository).existsByBeerNameIgnoreCase("Test Beer");
            verify(beerMapper, never()).dtoToBeer(any());
            verify(beerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception for duplicate name with different case")
        void shouldThrowExceptionForDuplicateNameDifferentCase() {
            // Given - trying to create "TEST BEER" when "Test Beer" exists
            BeerCreateDTO uppercaseDTO = new BeerCreateDTO(
                    "TEST BEER",
                    "999999",
                    50,
                    new BigDecimal("12.99"),
                    null
            );
            when(beerRepository.existsByBeerNameIgnoreCase("TEST BEER")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> beerService.createBeer(uppercaseDTO))
                    .isInstanceOf(ResourceAlreadyExistsExceptions.class)
                    .hasMessageContaining("TEST BEER");

            verify(beerRepository).existsByBeerNameIgnoreCase("TEST BEER");
        }

        @Test
        @DisplayName("Should throw exception when category does not exist")
        void shouldThrowExceptionWhenCategoryDoesNotExist() {
            // Given
            UUID nonExistentCategoryId = UUID.randomUUID();
            Set<UUID> categoryIds = Set.of(nonExistentCategoryId);
            BeerCreateDTO dtoWithInvalidCategory = new BeerCreateDTO(
                    "Test Beer",
                    "123456",
                    100,
                    new BigDecimal("10.99"),
                    categoryIds
            );

            when(beerRepository.existsByBeerNameIgnoreCase(dtoWithInvalidCategory.beerName())).thenReturn(false);
            when(beerMapper.dtoToBeer(dtoWithInvalidCategory)).thenReturn(validBeer);
            when(categoryRepository.findAllById(categoryIds)).thenReturn(Collections.emptyList());

            // When/Then
            assertThatThrownBy(() -> beerService.createBeer(dtoWithInvalidCategory))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Category")
                    .hasMessageContaining("not found");

            verify(categoryRepository).findAllById(categoryIds);
            verify(beerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when some categories exist and some don't")
        void shouldThrowExceptionWhenSomeCategoriesDoNotExist() {
            // Given
            UUID existingId = category1.getId();
            UUID nonExistentId = UUID.randomUUID();
            Set<UUID> categoryIds = Set.of(existingId, nonExistentId);

            BeerCreateDTO dtoWithMixedCategories = new BeerCreateDTO(
                    "Test Beer",
                    "123456",
                    100,
                    new BigDecimal("10.99"),
                    categoryIds
            );

            when(beerRepository.existsByBeerNameIgnoreCase(dtoWithMixedCategories.beerName())).thenReturn(false);
            when(beerMapper.dtoToBeer(dtoWithMixedCategories)).thenReturn(validBeer);
            when(categoryRepository.findAllById(categoryIds)).thenReturn(List.of(category1)); // Only one found

            // When/Then
            assertThatThrownBy(() -> beerService.createBeer(dtoWithMixedCategories))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Category")
                    .hasMessageContaining(nonExistentId.toString());

            verify(categoryRepository).findAllById(categoryIds);
            verify(beerRepository, never()).save(any());
        }
    }

    // ==================== Integration with Mapper Tests ====================

    @Nested
    @DisplayName("Mapper Integration")
    class MapperIntegration {

        @Test
        @DisplayName("Should correctly map DTO to entity")
        void shouldCorrectlyMapDtoToEntity() {
            // Given
            when(beerRepository.existsByBeerNameIgnoreCase(validBeerDTO.beerName())).thenReturn(false);
            when(beerMapper.dtoToBeer(validBeerDTO)).thenReturn(validBeer);
            when(beerRepository.save(validBeer)).thenReturn(validBeer);

            // When
            beerService.createBeer(validBeerDTO);

            // Then
            ArgumentCaptor<BeerCreateDTO> dtoCaptor = ArgumentCaptor.forClass(BeerCreateDTO.class);
            verify(beerMapper).dtoToBeer(dtoCaptor.capture());

            BeerCreateDTO capturedDTO = dtoCaptor.getValue();
            assertThat(capturedDTO.beerName()).isEqualTo("Test Beer");
            assertThat(capturedDTO.upc()).isEqualTo("123456");
            assertThat(capturedDTO.quantityOnHand()).isEqualTo(100);
            assertThat(capturedDTO.price()).isEqualTo(new BigDecimal("10.99"));
        }

        @Test
        @DisplayName("Should save entity returned by mapper")
        void shouldSaveEntityReturnedByMapper() {
            // Given
            when(beerRepository.existsByBeerNameIgnoreCase(validBeerDTO.beerName())).thenReturn(false);
            when(beerMapper.dtoToBeer(validBeerDTO)).thenReturn(validBeer);
            when(beerRepository.save(validBeer)).thenReturn(validBeer);

            // When
            beerService.createBeer(validBeerDTO);

            // Then
            ArgumentCaptor<Beer> beerCaptor = ArgumentCaptor.forClass(Beer.class);
            verify(beerRepository).save(beerCaptor.capture());

            Beer capturedBeer = beerCaptor.getValue();
            assertThat(capturedBeer).isEqualTo(validBeer);
        }
    }

    // ==================== Category Association Tests ====================

    @Nested
    @DisplayName("Category Association Logic")
    class CategoryAssociation {

        @Test
        @DisplayName("Should add categories to beer using addCategory method")
        void shouldAddCategoriesToBeer() {
            // Given
            Set<UUID> categoryIds = Set.of(category1.getId(), category2.getId());
            BeerCreateDTO dtoWithCategories = new BeerCreateDTO(
                    "Test Beer",
                    "123456",
                    100,
                    new BigDecimal("10.99"),
                    categoryIds
            );

            Beer freshBeer = Beer.builder()
                    .id(UUID.randomUUID())
                    .beerName("Test Beer")
                    .upc("123456")
                    .quantityOnHand(100)
                    .price(new BigDecimal("10.99"))
                    .categories(new HashSet<>())
                    .build();

            when(beerRepository.existsByBeerNameIgnoreCase(dtoWithCategories.beerName())).thenReturn(false);
            when(beerMapper.dtoToBeer(dtoWithCategories)).thenReturn(freshBeer);
            when(categoryRepository.findAllById(categoryIds)).thenReturn(List.of(category1, category2));
            when(beerRepository.save(freshBeer)).thenReturn(freshBeer);

            // When
            beerService.createBeer(dtoWithCategories);

            // Then
            assertThat(freshBeer.getCategories())
                    .hasSize(2)
                    .contains(category1, category2);
        }

        @Test
        @DisplayName("Should validate all categories exist before associating")
        void shouldValidateAllCategoriesExist() {
            // Given
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            UUID id3 = UUID.randomUUID();
            Set<UUID> categoryIds = Set.of(id1, id2, id3);

            BeerCreateDTO dto = new BeerCreateDTO(
                    "Test Beer",
                    "123456",
                    100,
                    new BigDecimal("10.99"),
                    categoryIds
            );

            Category cat1 = Category.builder().id(id1).description("Cat1").build();
            Category cat2 = Category.builder().id(id2).description("Cat2").build();

            when(beerRepository.existsByBeerNameIgnoreCase(dto.beerName())).thenReturn(false);
            when(beerMapper.dtoToBeer(dto)).thenReturn(validBeer);
            when(categoryRepository.findAllById(categoryIds)).thenReturn(List.of(cat1, cat2)); // Missing id3

            // When/Then
            assertThatThrownBy(() -> beerService.createBeer(dto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(id3.toString());
        }
    }

    // ==================== Return Value Tests ====================

    @Nested
    @DisplayName("Return Value Verification")
    class ReturnValueTests {

        @Test
        @DisplayName("Should return ID of saved beer")
        void shouldReturnIdOfSavedBeer() {
            // Given
            UUID expectedId = UUID.randomUUID();
            Beer savedBeer = Beer.builder()
                    .id(expectedId)
                    .beerName("Test Beer")
                    .upc("123456")
                    .quantityOnHand(100)
                    .price(new BigDecimal("10.99"))
                    .categories(new HashSet<>())
                    .build();

            when(beerRepository.existsByBeerNameIgnoreCase(validBeerDTO.beerName())).thenReturn(false);
            when(beerMapper.dtoToBeer(validBeerDTO)).thenReturn(validBeer);
            when(beerRepository.save(validBeer)).thenReturn(savedBeer);

            // When
            UUID result = beerService.createBeer(validBeerDTO);

            // Then
            assertThat(result).isEqualTo(expectedId);
        }

        @Test
        @DisplayName("Should not return null")
        void shouldNotReturnNull() {
            // Given
            when(beerRepository.existsByBeerNameIgnoreCase(validBeerDTO.beerName())).thenReturn(false);
            when(beerMapper.dtoToBeer(validBeerDTO)).thenReturn(validBeer);
            when(beerRepository.save(validBeer)).thenReturn(validBeer);

            // When
            UUID result = beerService.createBeer(validBeerDTO);

            // Then
            assertThat(result).isNotNull();
        }
    }
}