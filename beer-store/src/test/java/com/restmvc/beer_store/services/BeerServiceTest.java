package com.restmvc.beer_store.services;

import com.restmvc.beer_store.dtos.beer.BeerCreateRequestDTO;
import com.restmvc.beer_store.dtos.beer.BeerPatchRequestDTO;
import com.restmvc.beer_store.dtos.beer.BeerResponseDTO;
import com.restmvc.beer_store.dtos.beer.BeerUpdateRequestDTO;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BeerService}.
 * <p>
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

    private BeerCreateRequestDTO validBeerDTO;
    private Beer validBeer;
    private Category category1;
    private Category category2;
    private BeerResponseDTO validBeerResponseDTO;
    private BeerUpdateRequestDTO validUpdateDTO;

    @BeforeEach
    void setUp() {
        validBeerDTO = new BeerCreateRequestDTO(
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

        validBeerResponseDTO = new BeerResponseDTO(
                validBeer.getId(),
                validBeer.getBeerName(),
                validBeer.getUpc(),
                validBeer.getQuantityOnHand(),
                validBeer.getPrice(),
                Set.of(),
                null,
                null
        );
        validUpdateDTO = new BeerUpdateRequestDTO(
                "Updated Beer",
                "123456",
                200,
                new BigDecimal("12.34")
        );
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
            BeerCreateRequestDTO dtoWithCategory = new BeerCreateRequestDTO(
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
            BeerCreateRequestDTO dtoWithCategories = new BeerCreateRequestDTO(
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
            BeerCreateRequestDTO dtoWithEmptyCategories = new BeerCreateRequestDTO(
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
            BeerCreateRequestDTO uppercaseDTO = new BeerCreateRequestDTO(
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
            BeerCreateRequestDTO dtoWithInvalidCategory = new BeerCreateRequestDTO(
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

            BeerCreateRequestDTO dtoWithMixedCategories = new BeerCreateRequestDTO(
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
            ArgumentCaptor<BeerCreateRequestDTO> dtoCaptor = ArgumentCaptor.forClass(BeerCreateRequestDTO.class);
            verify(beerMapper).dtoToBeer(dtoCaptor.capture());

            BeerCreateRequestDTO capturedDTO = dtoCaptor.getValue();
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
            BeerCreateRequestDTO dtoWithCategories = new BeerCreateRequestDTO(
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

            BeerCreateRequestDTO dto = new BeerCreateRequestDTO(
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

    // ==================== Get All Beers Tests ====================

    @Nested
    @DisplayName("Get All Beers Tests")
    class GetAllBeersTests {

        @Test
        @DisplayName("Should return all Beers")
        void shouldReturnAllBeers() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Beer> beerPage = new PageImpl<>(List.of(validBeer), pageable, 1);

            when(beerRepository.findAll(pageable)).thenReturn(beerPage);
            when(beerMapper.beerToResponseDto(validBeer)).thenReturn(validBeerResponseDTO);

            Page<BeerResponseDTO> result = beerService.getAllBeers(null, null, true, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0)).isEqualToComparingFieldByField(validBeerResponseDTO);

            verify(beerRepository).findAll(pageable);
            verify(beerMapper).beerToResponseDto(validBeer);
            verifyNoMoreInteractions(beerMapper);

        }


        @Test
        @DisplayName("Should return empty list when no Beers exist")
        void shouldReturnEmptyListWhenNoBeersExist() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Beer> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(beerRepository.findAll(pageable)).thenReturn(emptyPage);

            Page<BeerResponseDTO> result = beerService.getAllBeers(null, null, true, pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();

            verify(beerRepository).findAll(pageable);
            verifyNoInteractions(beerMapper);
        }

        @Test
        @DisplayName("Should return Beers sorted by name")
        void shouldReturnBeersSortedByName() {
            Pageable pageable = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("beerName").ascending());
            Page<Beer> beerPage = new PageImpl<>(List.of(validBeer), pageable, 1);

            when(beerRepository.findAll(pageable)).thenReturn(beerPage);
            when(beerMapper.beerToResponseDto(validBeer)).thenReturn(validBeerResponseDTO);

            beerService.getAllBeers(null, null, true, pageable);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(beerRepository).findAll(pageableCaptor.capture());

            Pageable captured = pageableCaptor.getValue();
            assertThat(captured.getSort().getOrderFor("beerName")).isNotNull();
            assertThat(captured.getSort().getOrderFor("beerName").isAscending()).isTrue();
        }

        @Test
        @DisplayName("Should return Beers sorted by upc")
        void shouldReturnBeersSortedByUpc() {
            Pageable pageable = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("upc").descending());
            Page<Beer> beerPage = new PageImpl<>(List.of(validBeer), pageable, 1);

            when(beerRepository.findAll(pageable)).thenReturn(beerPage);
            when(beerMapper.beerToResponseDto(validBeer)).thenReturn(validBeerResponseDTO);

            beerService.getAllBeers(null, null, true, pageable);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(beerRepository).findAll(pageableCaptor.capture());

            Pageable captured = pageableCaptor.getValue();
            assertThat(captured.getSort().getOrderFor("upc")).isNotNull();
            assertThat(captured.getSort().getOrderFor("upc").isDescending()).isTrue();
        }

        @Test
        @DisplayName("Should hide quantityOnHand when showInventoryOnHand is false")
        void shouldHideInventoryWhenFlagFalse() {
            Pageable pageable = PageRequest.of(0, 10);
            Beer beerWithQty = Beer.builder()
                    .id(UUID.randomUUID())
                    .beerName("Test Beer")
                    .upc("123456")
                    .quantityOnHand(100)
                    .price(new BigDecimal("10.99"))
                    .categories(new HashSet<>())
                    .build();

            Page<Beer> beerPage = new PageImpl<>(List.of(beerWithQty), pageable, 1);

            when(beerRepository.findAll(pageable)).thenReturn(beerPage);
            when(beerMapper.beerToResponseDto(any())).thenReturn(validBeerResponseDTO);

            beerService.getAllBeers(null, null, false, pageable);

            assertThat(beerWithQty.getQuantityOnHand()).isNull();
        }
    }

    @Nested
    @DisplayName("Get Beer By Id Tests")
    class GetBeerByIdTests {

        @Test
        @DisplayName("Should return Beer by ID")
        void shouldReturnBeerById() {
            // Given
            UUID beerId = validBeer.getId();

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerMapper.beerToResponseDto(validBeer)).thenReturn(validBeerResponseDTO);

            // When
            BeerResponseDTO result = beerService.getBeerById(beerId);

            // Then
            assertThat(result).isEqualTo(validBeerResponseDTO);
            assertThat(result).isNotNull();

            verify(beerRepository).findWithCategoriesById(beerId);
            verify(beerMapper).beerToResponseDto(validBeer);
        }

        @Test
        @DisplayName("Should throw exception when Beer does not exist")
        void shouldThrowExceptionWhenBeerDoesNotExist() throws Exception {
            UUID randomId = UUID.randomUUID();
            when(beerRepository.findWithCategoriesById(any())).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> beerService.getBeerById(randomId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Beer")
                    .hasMessageContaining(randomId.toString());

            verify(beerRepository).findWithCategoriesById(randomId);
            verifyNoInteractions(beerMapper);
        }
    }

    @Nested
    @DisplayName("Update Beer By Id Tests")
    class UpdateBeerByIdTests {

        @Test
        @DisplayName("Should update beer successfully")
        void shouldUpdateBeerById() {
            // Given
            UUID beerId = validBeer.getId();
            Beer updatedBeer = Beer.builder()
                    .id(beerId)
                    .beerName("Updated Beer")
                    .upc("123456")
                    .quantityOnHand(200)
                    .price(new BigDecimal("12.34"))
                    .categories(new HashSet<>())
                    .build();

            BeerResponseDTO updatedResponseDTO = new BeerResponseDTO(
                    beerId,
                    "Updated Beer",
                    "123456",
                    200,
                    new BigDecimal("12.34"),
                    Set.of(),
                    null,
                    null
            );

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.existsByBeerNameIgnoreCaseAndIdNot("Updated Beer", beerId)).thenReturn(false);
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(updatedBeer);
            when(beerMapper.beerToResponseDto(updatedBeer)).thenReturn(updatedResponseDTO);
            doNothing().when(beerMapper).updateBeerFromDto(validUpdateDTO, validBeer);

            // When
            BeerResponseDTO result = beerService.updateBeerById(beerId, validUpdateDTO);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.beerName()).isEqualTo("Updated Beer");
            assertThat(result.price()).isEqualTo(new BigDecimal("12.34"));
            assertThat(result.quantityOnHand()).isEqualTo(200);

            verify(beerRepository).findWithCategoriesById(beerId);
            verify(beerRepository).existsByBeerNameIgnoreCaseAndIdNot("Updated Beer", beerId);
            verify(beerMapper).updateBeerFromDto(validUpdateDTO, validBeer);
            verify(beerRepository).saveAndFlush(any(Beer.class));
            verify(beerMapper).beerToResponseDto(updatedBeer);
        }

        @Test
        @DisplayName("Should update beer without name validation when name stays the same")
        void shouldUpdateBeerWithoutNameValidation() {
            // Given
            UUID beerId = validBeer.getId();
            BeerUpdateRequestDTO sameNameDTO = new BeerUpdateRequestDTO(
                    "Test Beer",  // same name as validBeer
                    "654321",     // different UPC
                    150,
                    new BigDecimal("11.99")
            );

            Beer updatedBeer = Beer.builder()
                    .id(beerId)
                    .beerName("Test Beer")
                    .upc("654321")
                    .quantityOnHand(150)
                    .price(new BigDecimal("11.99"))
                    .categories(new HashSet<>())
                    .build();

            BeerResponseDTO updatedResponseDTO = new BeerResponseDTO(
                    beerId,
                    "Test Beer",
                    "654321",
                    150,
                    new BigDecimal("11.99"),
                    Set.of(),
                    null,
                    null
            );

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(updatedBeer);
            when(beerMapper.beerToResponseDto(updatedBeer)).thenReturn(updatedResponseDTO);
            doNothing().when(beerMapper).updateBeerFromDto(sameNameDTO, validBeer);

            // When
            BeerResponseDTO result = beerService.updateBeerById(beerId, sameNameDTO);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.beerName()).isEqualTo("Test Beer");
            assertThat(result.upc()).isEqualTo("654321");

            verify(beerRepository).findWithCategoriesById(beerId);
            verify(beerRepository, never()).existsByBeerNameIgnoreCaseAndIdNot(anyString(), any(UUID.class));
            verify(beerMapper).updateBeerFromDto(sameNameDTO, validBeer);
            verify(beerRepository).saveAndFlush(any(Beer.class));
        }

        @Test
        @DisplayName("Should handle case-insensitive name comparison")
        void shouldHandleCaseInsensitiveNameComparison() {
            // Given
            UUID beerId = validBeer.getId();
            BeerUpdateRequestDTO upperCaseNameDTO = new BeerUpdateRequestDTO(
                    "TEST BEER",  // same name but different case
                    "654321",
                    150,
                    new BigDecimal("11.99")
            );

            Beer updatedBeer = Beer.builder()
                    .id(beerId)
                    .beerName("TEST BEER")
                    .upc("654321")
                    .quantityOnHand(150)
                    .price(new BigDecimal("11.99"))
                    .categories(new HashSet<>())
                    .build();

            BeerResponseDTO updatedResponseDTO = new BeerResponseDTO(
                    beerId,
                    "TEST BEER",
                    "654321",
                    150,
                    new BigDecimal("11.99"),
                    Set.of(),
                    null,
                    null
            );

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(updatedBeer);
            when(beerMapper.beerToResponseDto(updatedBeer)).thenReturn(updatedResponseDTO);
            doNothing().when(beerMapper).updateBeerFromDto(upperCaseNameDTO, validBeer);

            // When
            BeerResponseDTO result = beerService.updateBeerById(beerId, upperCaseNameDTO);

            // Then
            assertThat(result).isNotNull();

            verify(beerRepository).findWithCategoriesById(beerId);
            // Should not check for duplicate since it's the same name (case-insensitive)
            verify(beerRepository, never()).existsByBeerNameIgnoreCaseAndIdNot(anyString(), any(UUID.class));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when beer does not exist")
        void shouldThrowExceptionWhenBeerDoesNotExist() {
            // Given
            UUID nonExistentId = UUID.randomUUID();

            when(beerRepository.findWithCategoriesById(nonExistentId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> beerService.updateBeerById(nonExistentId, validUpdateDTO))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Beer")
                    .hasMessageContaining("id")
                    .hasMessageContaining(nonExistentId.toString());

            verify(beerRepository).findWithCategoriesById(nonExistentId);
            verify(beerRepository, never()).existsByBeerNameIgnoreCaseAndIdNot(anyString(), any(UUID.class));
            verify(beerMapper, never()).updateBeerFromDto(any(), any());
            verify(beerRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Should throw ResourceAlreadyExistsException when beer name already exists")
        void shouldThrowExceptionWhenBeerNameAlreadyExists() {
            // Given
            UUID beerId = validBeer.getId();

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.existsByBeerNameIgnoreCaseAndIdNot("Updated Beer", beerId)).thenReturn(true);

            // When / Then
            assertThatThrownBy(() -> beerService.updateBeerById(beerId, validUpdateDTO))
                    .isInstanceOf(ResourceAlreadyExistsExceptions.class)
                    .hasMessageContaining("Beer")
                    .hasMessageContaining("beerName")
                    .hasMessageContaining("Updated Beer");

            verify(beerRepository).findWithCategoriesById(beerId);
            verify(beerRepository).existsByBeerNameIgnoreCaseAndIdNot("Updated Beer", beerId);
            verify(beerMapper, never()).updateBeerFromDto(any(), any());
            verify(beerRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Should update only allowed fields")
        void shouldUpdateOnlyAllowedFields() {
            // Given
            UUID beerId = validBeer.getId();

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.existsByBeerNameIgnoreCaseAndIdNot("Updated Beer", beerId)).thenReturn(false);
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(validBeer);
            when(beerMapper.beerToResponseDto(any())).thenReturn(validBeerResponseDTO);
            doNothing().when(beerMapper).updateBeerFromDto(validUpdateDTO, validBeer);

            // When
            beerService.updateBeerById(beerId, validUpdateDTO);

            // Then
            // Verify that mapper was called (mapper is responsible for ignoring id, version, timestamps)
            verify(beerMapper).updateBeerFromDto(validUpdateDTO, validBeer);
        }

        @Test
        @DisplayName("Should call saveAndFlush instead of save")
        void shouldCallSaveAndFlush() {
            // Given
            UUID beerId = validBeer.getId();

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.existsByBeerNameIgnoreCaseAndIdNot("Updated Beer", beerId)).thenReturn(false);
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(validBeer);
            when(beerMapper.beerToResponseDto(any())).thenReturn(validBeerResponseDTO);
            doNothing().when(beerMapper).updateBeerFromDto(validUpdateDTO, validBeer);

            // When
            beerService.updateBeerById(beerId, validUpdateDTO);

            // Then
            verify(beerRepository).saveAndFlush(validBeer);
            verify(beerRepository, never()).save(any(Beer.class));
        }

        @Test
        @DisplayName("Should return updated BeerResponseDTO")
        void shouldReturnUpdatedBeerResponseDTO() {
            // Given
            UUID beerId = validBeer.getId();
            Beer updatedBeer = Beer.builder()
                    .id(beerId)
                    .beerName("Updated Beer")
                    .upc("123456")
                    .quantityOnHand(200)
                    .price(new BigDecimal("12.34"))
                    .categories(new HashSet<>())
                    .build();

            BeerResponseDTO expectedResponse = new BeerResponseDTO(
                    beerId,
                    "Updated Beer",
                    "123456",
                    200,
                    new BigDecimal("12.34"),
                    Set.of(),
                    null,
                    null
            );

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.existsByBeerNameIgnoreCaseAndIdNot("Updated Beer", beerId)).thenReturn(false);
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(updatedBeer);
            when(beerMapper.beerToResponseDto(updatedBeer)).thenReturn(expectedResponse);
            doNothing().when(beerMapper).updateBeerFromDto(validUpdateDTO, validBeer);

            // When
            BeerResponseDTO result = beerService.updateBeerById(beerId, validUpdateDTO);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(result.id()).isEqualTo(beerId);
            assertThat(result.beerName()).isEqualTo("Updated Beer");

            verify(beerMapper).beerToResponseDto(updatedBeer);
        }
    }

    @Nested
    @DisplayName("Patch Beer By Id Tests")
    class PatchBeerByIdTests {

        private BeerPatchRequestDTO patchNameOnlyDTO;
        private BeerPatchRequestDTO patchUpcOnlyDTO;
        private BeerPatchRequestDTO patchQuantityOnlyDTO;
        private BeerPatchRequestDTO patchPriceOnlyDTO;
        private BeerPatchRequestDTO patchAllFieldsDTO;
        private BeerPatchRequestDTO patchMultipleFieldsDTO;

        @BeforeEach
        void setUpPatchDTOs() {
            patchNameOnlyDTO = new BeerPatchRequestDTO(
                    "Patched Name",
                    null,
                    null,
                    null
            );

            patchUpcOnlyDTO = new BeerPatchRequestDTO(
                    null,
                    "999999",
                    null,
                    null
            );

            patchQuantityOnlyDTO = new BeerPatchRequestDTO(
                    null,
                    null,
                    500,
                    null
            );

            patchPriceOnlyDTO = new BeerPatchRequestDTO(
                    null,
                    null,
                    null,
                    new BigDecimal("99.99")
            );

            patchAllFieldsDTO = new BeerPatchRequestDTO(
                    "Fully Patched Beer",
                    "888888",
                    300,
                    new BigDecimal("25.50")
            );

            patchMultipleFieldsDTO = new BeerPatchRequestDTO(
                    "Multi Patch Beer",
                    "777777",
                    null,
                    new BigDecimal("15.99")
            );
        }

        @Test
        @DisplayName("Should patch only beer name")
        void shouldPatchOnlyBeerName() {
            // Given
            UUID beerId = validBeer.getId();

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.existsByBeerNameIgnoreCaseAndIdNot("Patched Name", beerId)).thenReturn(false);
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(validBeer);
            when(beerMapper.beerToResponseDto(any())).thenReturn(validBeerResponseDTO);
            doNothing().when(beerMapper).patchBeerFromDto(patchNameOnlyDTO, validBeer);

            // When
            BeerResponseDTO result = beerService.patchBeerById(beerId, patchNameOnlyDTO);

            // Then
            assertThat(result).isNotNull();
            verify(beerRepository).findWithCategoriesById(beerId);
            verify(beerRepository).existsByBeerNameIgnoreCaseAndIdNot("Patched Name", beerId);
            verify(beerMapper).patchBeerFromDto(patchNameOnlyDTO, validBeer);
            verify(beerRepository).saveAndFlush(validBeer);
            verify(beerMapper).beerToResponseDto(any());
        }

        @Test
        @DisplayName("Should patch only UPC")
        void shouldPatchOnlyUpc() {
            // Given
            UUID beerId = validBeer.getId();

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(validBeer);
            when(beerMapper.beerToResponseDto(any())).thenReturn(validBeerResponseDTO);
            doNothing().when(beerMapper).patchBeerFromDto(patchUpcOnlyDTO, validBeer);

            // When
            BeerResponseDTO result = beerService.patchBeerById(beerId, patchUpcOnlyDTO);

            // Then
            assertThat(result).isNotNull();
            verify(beerRepository).findWithCategoriesById(beerId);
            // Name is null, so no name validation should occur
            verify(beerRepository, never()).existsByBeerNameIgnoreCaseAndIdNot(anyString(), any(UUID.class));
            verify(beerMapper).patchBeerFromDto(patchUpcOnlyDTO, validBeer);
            verify(beerRepository).saveAndFlush(validBeer);
        }

        @Test
        @DisplayName("Should patch only quantity on hand")
        void shouldPatchOnlyQuantityOnHand() {
            // Given
            UUID beerId = validBeer.getId();

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(validBeer);
            when(beerMapper.beerToResponseDto(any())).thenReturn(validBeerResponseDTO);
            doNothing().when(beerMapper).patchBeerFromDto(patchQuantityOnlyDTO, validBeer);

            // When
            BeerResponseDTO result = beerService.patchBeerById(beerId, patchQuantityOnlyDTO);

            // Then
            assertThat(result).isNotNull();
            verify(beerRepository).findWithCategoriesById(beerId);
            verify(beerRepository, never()).existsByBeerNameIgnoreCaseAndIdNot(anyString(), any(UUID.class));
            verify(beerMapper).patchBeerFromDto(patchQuantityOnlyDTO, validBeer);
            verify(beerRepository).saveAndFlush(validBeer);
        }

        @Test
        @DisplayName("Should patch only price")
        void shouldPatchOnlyPrice() {
            // Given
            UUID beerId = validBeer.getId();

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(validBeer);
            when(beerMapper.beerToResponseDto(any())).thenReturn(validBeerResponseDTO);
            doNothing().when(beerMapper).patchBeerFromDto(patchPriceOnlyDTO, validBeer);

            // When
            BeerResponseDTO result = beerService.patchBeerById(beerId, patchPriceOnlyDTO);

            // Then
            assertThat(result).isNotNull();
            verify(beerRepository).findWithCategoriesById(beerId);
            verify(beerRepository, never()).existsByBeerNameIgnoreCaseAndIdNot(anyString(), any(UUID.class));
            verify(beerMapper).patchBeerFromDto(patchPriceOnlyDTO, validBeer);
            verify(beerRepository).saveAndFlush(validBeer);
        }

        @Test
        @DisplayName("Should patch all fields")
        void shouldPatchAllFields() {
            // Given
            UUID beerId = validBeer.getId();

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.existsByBeerNameIgnoreCaseAndIdNot("Fully Patched Beer", beerId)).thenReturn(false);
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(validBeer);
            when(beerMapper.beerToResponseDto(any())).thenReturn(validBeerResponseDTO);
            doNothing().when(beerMapper).patchBeerFromDto(patchAllFieldsDTO, validBeer);

            // When
            BeerResponseDTO result = beerService.patchBeerById(beerId, patchAllFieldsDTO);

            // Then
            assertThat(result).isNotNull();
            verify(beerRepository).findWithCategoriesById(beerId);
            verify(beerRepository).existsByBeerNameIgnoreCaseAndIdNot("Fully Patched Beer", beerId);
            verify(beerMapper).patchBeerFromDto(patchAllFieldsDTO, validBeer);
            verify(beerRepository).saveAndFlush(validBeer);
        }

        @Test
        @DisplayName("Should patch multiple fields (name, upc, price)")
        void shouldPatchMultipleFields() {
            // Given
            UUID beerId = validBeer.getId();

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.existsByBeerNameIgnoreCaseAndIdNot("Multi Patch Beer", beerId)).thenReturn(false);
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(validBeer);
            when(beerMapper.beerToResponseDto(any())).thenReturn(validBeerResponseDTO);
            doNothing().when(beerMapper).patchBeerFromDto(patchMultipleFieldsDTO, validBeer);

            // When
            BeerResponseDTO result = beerService.patchBeerById(beerId, patchMultipleFieldsDTO);

            // Then
            assertThat(result).isNotNull();
            verify(beerRepository).findWithCategoriesById(beerId);
            verify(beerRepository).existsByBeerNameIgnoreCaseAndIdNot("Multi Patch Beer", beerId);
            verify(beerMapper).patchBeerFromDto(patchMultipleFieldsDTO, validBeer);
            verify(beerRepository).saveAndFlush(validBeer);
        }

        @Test
        @DisplayName("Should skip name validation when name is null in patch")
        void shouldSkipNameValidationWhenNameIsNull() {
            // Given
            UUID beerId = validBeer.getId();
            BeerPatchRequestDTO patchWithoutName = new BeerPatchRequestDTO(
                    null,
                    "999999",
                    100,
                    new BigDecimal("10.99")
            );

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(validBeer);
            when(beerMapper.beerToResponseDto(any())).thenReturn(validBeerResponseDTO);
            doNothing().when(beerMapper).patchBeerFromDto(patchWithoutName, validBeer);

            // When
            BeerResponseDTO result = beerService.patchBeerById(beerId, patchWithoutName);

            // Then
            assertThat(result).isNotNull();
            verify(beerRepository).findWithCategoriesById(beerId);
            // Name is null, so no validation should occur
            verify(beerRepository, never()).existsByBeerNameIgnoreCaseAndIdNot(anyString(), any(UUID.class));
        }

        @Test
        @DisplayName("Should skip name validation when patching to same name")
        void shouldSkipNameValidationWhenSameName() {
            // Given
            UUID beerId = validBeer.getId();
            BeerPatchRequestDTO patchSameName = new BeerPatchRequestDTO(
                    "Test Beer", // same as validBeer.getBeerName()
                    "999999",
                    null,
                    null
            );

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(validBeer);
            when(beerMapper.beerToResponseDto(any())).thenReturn(validBeerResponseDTO);
            doNothing().when(beerMapper).patchBeerFromDto(patchSameName, validBeer);

            // When
            BeerResponseDTO result = beerService.patchBeerById(beerId, patchSameName);

            // Then
            assertThat(result).isNotNull();
            verify(beerRepository).findWithCategoriesById(beerId);
            // Name is the same (case-insensitive), so no validation
            verify(beerRepository, never()).existsByBeerNameIgnoreCaseAndIdNot(anyString(), any(UUID.class));
        }

        @Test
        @DisplayName("Should handle case-insensitive name comparison in patch")
        void shouldHandleCaseInsensitiveNameComparison() {
            // Given
            UUID beerId = validBeer.getId();
            BeerPatchRequestDTO patchUpperCase = new BeerPatchRequestDTO(
                    "TEST BEER", // same name but different case
                    null,
                    null,
                    null
            );

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(validBeer);
            when(beerMapper.beerToResponseDto(any())).thenReturn(validBeerResponseDTO);
            doNothing().when(beerMapper).patchBeerFromDto(patchUpperCase, validBeer);

            // When
            BeerResponseDTO result = beerService.patchBeerById(beerId, patchUpperCase);

            // Then
            assertThat(result).isNotNull();
            verify(beerRepository).findWithCategoriesById(beerId);
            // Should not check for duplicates since it's the same name (case-insensitive)
            verify(beerRepository, never()).existsByBeerNameIgnoreCaseAndIdNot(anyString(), any(UUID.class));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when beer does not exist")
        void shouldThrowExceptionWhenBeerDoesNotExist() {
            // Given
            UUID nonExistentId = UUID.randomUUID();

            when(beerRepository.findWithCategoriesById(nonExistentId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> beerService.patchBeerById(nonExistentId, patchNameOnlyDTO))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Beer")
                    .hasMessageContaining("id")
                    .hasMessageContaining(nonExistentId.toString());

            verify(beerRepository).findWithCategoriesById(nonExistentId);
            verify(beerRepository, never()).existsByBeerNameIgnoreCaseAndIdNot(anyString(), any(UUID.class));
            verify(beerMapper, never()).patchBeerFromDto(any(), any());
            verify(beerRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Should throw ResourceAlreadyExistsException when patching to existing beer name")
        void shouldThrowExceptionWhenPatchingToExistingName() {
            // Given
            UUID beerId = validBeer.getId();

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.existsByBeerNameIgnoreCaseAndIdNot("Patched Name", beerId)).thenReturn(true);

            // When / Then
            assertThatThrownBy(() -> beerService.patchBeerById(beerId, patchNameOnlyDTO))
                    .isInstanceOf(ResourceAlreadyExistsExceptions.class)
                    .hasMessageContaining("Beer")
                    .hasMessageContaining("beerName")
                    .hasMessageContaining("Patched Name");

            verify(beerRepository).findWithCategoriesById(beerId);
            verify(beerRepository).existsByBeerNameIgnoreCaseAndIdNot("Patched Name", beerId);
            verify(beerMapper, never()).patchBeerFromDto(any(), any());
            verify(beerRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Should use saveAndFlush instead of save")
        void shouldCallSaveAndFlush() {
            // Given
            UUID beerId = validBeer.getId();

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(validBeer);
            when(beerMapper.beerToResponseDto(any())).thenReturn(validBeerResponseDTO);
            doNothing().when(beerMapper).patchBeerFromDto(patchUpcOnlyDTO, validBeer);

            // When
            beerService.patchBeerById(beerId, patchUpcOnlyDTO);

            // Then
            verify(beerRepository).saveAndFlush(validBeer);
            verify(beerRepository, never()).save(any(Beer.class));
        }

        @Test
        @DisplayName("Should return BeerResponseDTO after patch")
        void shouldReturnBeerResponseDTO() {
            // Given
            UUID beerId = validBeer.getId();
            BeerResponseDTO expectedResponse = new BeerResponseDTO(
                    beerId,
                    "Patched Beer",
                    "999999",
                    500,
                    new BigDecimal("99.99"),
                    Set.of(),
                    null,
                    null
            );

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(validBeer);
            when(beerMapper.beerToResponseDto(validBeer)).thenReturn(expectedResponse);
            doNothing().when(beerMapper).patchBeerFromDto(patchAllFieldsDTO, validBeer);

            // When
            BeerResponseDTO result = beerService.patchBeerById(beerId, patchAllFieldsDTO);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(expectedResponse);
            verify(beerMapper).beerToResponseDto(validBeer);
        }

        @Test
        @DisplayName("Should handle empty patch DTO (all fields null)")
        void shouldHandleEmptyPatchDTO() {
            // Given
            UUID beerId = validBeer.getId();
            BeerPatchRequestDTO emptyPatch = new BeerPatchRequestDTO(null, null, null, null);

            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            when(beerRepository.saveAndFlush(any(Beer.class))).thenReturn(validBeer);
            when(beerMapper.beerToResponseDto(any())).thenReturn(validBeerResponseDTO);
            doNothing().when(beerMapper).patchBeerFromDto(emptyPatch, validBeer);

            // When
            BeerResponseDTO result = beerService.patchBeerById(beerId, emptyPatch);

            // Then
            assertThat(result).isNotNull();
            verify(beerRepository).findWithCategoriesById(beerId);
            verify(beerRepository, never()).existsByBeerNameIgnoreCaseAndIdNot(anyString(), any(UUID.class));
            verify(beerMapper).patchBeerFromDto(emptyPatch, validBeer);
            verify(beerRepository).saveAndFlush(validBeer);
        }
    }

    @Nested
    @DisplayName("Delete Beer By Id Tests")
    class DeleteBeerByIdTests {

        @Test
        @DisplayName("Should delete beer successfully when it exists")
        void shouldDeleteBeerWhenExists() {
            // Given
            UUID beerId = validBeer.getId();
            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.of(validBeer));
            doNothing().when(beerRepository).deleteById(beerId);

            // When
            beerService.deleteBeerById(beerId);

            // Then
            verify(beerRepository).findWithCategoriesById(beerId);
            verify(beerRepository).deleteById(beerId);
            verifyNoInteractions(beerMapper);
            verifyNoMoreInteractions(beerRepository);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when beer does not exist")
        void shouldThrowWhenBeerDoesNotExist() {
            // Given
            UUID beerId = UUID.randomUUID();
            when(beerRepository.findWithCategoriesById(beerId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> beerService.deleteBeerById(beerId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Beer")
                    .hasMessageContaining(beerId.toString());

            verify(beerRepository).findWithCategoriesById(beerId);
            verify(beerRepository, never()).deleteById(any());
            verifyNoInteractions(beerMapper);
        }
    }
}