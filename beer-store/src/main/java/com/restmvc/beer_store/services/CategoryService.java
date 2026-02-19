package com.restmvc.beer_store.services;

import com.restmvc.beer_store.dtos.category.CategoryCreateRequestDTO;
import com.restmvc.beer_store.dtos.category.CategoryResponseDTO;
import com.restmvc.beer_store.entities.Category;
import com.restmvc.beer_store.exceptions.ResourceAlreadyExistsExceptions;
import com.restmvc.beer_store.exceptions.ResourceNotFoundException;
import com.restmvc.beer_store.mappers.CategoryMapper;
import com.restmvc.beer_store.repositories.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Service layer responsible for business operations related to {@link Category} entities.
 *
 * <p>This service encapsulates domain logic and coordinates persistence
 * operations via {@link CategoryRepository}. It acts as the transactional
 * boundary between controllers and the data access layer.</p>
 *
 * <p>Responsibilities include:</p>
 * <ul>
 *     <li>Creating, reading, updating and deleting categories</li>
 *     <li>Enforcing unique description constraint (case-insensitive)</li>
 *     <li>Handling transactional consistency</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    /**
     * Creates a new {@link Category} entity based on the provided DTO.
     *
     * <p>The method is transactional to ensure atomic persistence.
     * If any exception occurs during the process, the transaction
     * will be rolled back automatically.</p>
     *
     * @param categoryCreateRequestDTO data required to create a new category
     * @return the unique identifier ({@link UUID}) of the newly created category
     * @throws ResourceAlreadyExistsExceptions if a category with the same description already exists
     */
    @Transactional
    public UUID createCategory(CategoryCreateRequestDTO categoryCreateRequestDTO) {
        log.info("Creating category with name: {}", categoryCreateRequestDTO);
        // 1) Validate uniqueness of the description
        validateUniqueCategoryName(categoryCreateRequestDTO.description(), null);
        // 2) Convert DTO to entity
        Category category = categoryMapper.dtoToCategory(categoryCreateRequestDTO);
        // 3) Persist entity
        Category saveCategory = categoryRepository.save(category);
        log.info("Successfully created category with ID: {}", category.getId());
        return saveCategory.getId();
    }

    /**
     * Retrieve a paginated list of categories, optionally filtered by description.
     *
     * <p>If {@code description} is provided, results are filtered using a case-insensitive
     * LIKE query. Otherwise all categories are returned.</p>
     *
     * @param description optional description filter (case-insensitive, partial match)
     * @param pageable    pagination and sorting parameters
     * @return a page of {@link CategoryResponseDTO}
     */
    @Transactional(readOnly = true)
    public Page<CategoryResponseDTO> getAllCategories(String description, Pageable pageable) {
        log.info("Retrieving all categories: description={}, pageable={}", description, pageable);

        // 1) Determine whether to apply the description filter
        Page<Category> categoryPage;
        boolean hasDescription = StringUtils.hasText(description);

        // 2) Filter by description if provided, otherwise return all
        if (hasDescription) {
            categoryPage = categoryRepository.findByDescriptionContainingIgnoreCase(description, pageable);
        } else {
            categoryPage = categoryRepository.findAll(pageable);
        }
        return categoryPage.map(categoryMapper::categoryToResponseDto);
    }

    /**
     * Retrieve a single category by its unique identifier.
     *
     * @param categoryId the UUID of the category to retrieve
     * @return {@link CategoryResponseDTO} representing the found category
     * @throws ResourceNotFoundException if no category with the given ID exists
     */
    @Transactional(readOnly = true)
    public CategoryResponseDTO getCategoryById(UUID categoryId) {
        log.info("Retrieving category with ID: {}", categoryId);
        return categoryMapper.categoryToResponseDto(getCategoryOrThrow(categoryId));
    }

    /**
     * Fully update an existing category with new data.
     *
     * <p>Name uniqueness is validated only when the description is actually being changed,
     * allowing the category to keep its current description without conflict.</p>
     *
     * @param categoryId               the UUID of the category to update
     * @param categoryUpdateRequestDTO the new category data
     * @return updated {@link CategoryResponseDTO}
     * @throws ResourceNotFoundException       if no category with the given ID exists
     * @throws ResourceAlreadyExistsExceptions if the new description is already taken by another category
     */
    @Transactional
    public CategoryResponseDTO updateCategoryById(UUID categoryId, CategoryCreateRequestDTO categoryUpdateRequestDTO) {
        log.info("Updating category with ID: {}", categoryId);

        // 1) Get category or throw 404 if not found
        Category category = getCategoryOrThrow(categoryId);

        // 2) Validate uniqueness only if the description is changing
        if (!category.getDescription().equalsIgnoreCase(categoryUpdateRequestDTO.description())) {
            validateUniqueCategoryName(categoryUpdateRequestDTO.description(), categoryId);
        }

        // 3) Apply the new values and persist
        categoryMapper.updateCategoryFromDto(categoryUpdateRequestDTO, category);
        return categoryMapper.categoryToResponseDto(categoryRepository.save(category));
    }

    /**
     * Delete a category by its unique identifier.
     *
     * @param categoryId the UUID of the category to delete
     * @throws ResourceNotFoundException if no category with the given ID exists
     */
    @Transactional
    public void deleteCategoryById(UUID categoryId) {
        log.info("Deleting category with ID: {}", categoryId);
        categoryRepository.deleteById(getCategoryOrThrow(categoryId).getId());
    }

    // ============================= Helper Methods =================================

    /**
     * Validates that the given category description is unique (case-insensitive).
     *
     * <p>During updates, {@code excludeId} ensures the current category is excluded
     * from the uniqueness check, allowing it to keep its existing description.
     * Case-insensitive comparison prevents duplicates like "IPA" and "ipa".</p>
     *
     * @param categoryName the description to validate
     * @param excludeId    the category ID to exclude from the check; {@code null} for create operations
     * @throws ResourceAlreadyExistsExceptions if another category with the same description exists
     */
    private void validateUniqueCategoryName(String categoryName, UUID excludeId) {
        boolean exists = excludeId == null
                ? categoryRepository.existsByDescriptionIgnoreCase(categoryName)
                : categoryRepository.existsByDescriptionIgnoreCaseAndIdNot(categoryName, excludeId);

        if (exists) {
            log.warn("Category with description '{}' already exists", categoryName);
            throw new ResourceAlreadyExistsExceptions("Category", "categoryDescription", categoryName);
        }
    }

    /**
     * Retrieves a {@link Category} by its ID or throws {@link ResourceNotFoundException}.
     *
     * @param categoryId the UUID of the category to retrieve
     * @return the found {@link Category} entity
     * @throws ResourceNotFoundException if no category with the given ID exists
     */
    private Category getCategoryOrThrow(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId.toString()));
    }
}
