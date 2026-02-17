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

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;


    // ============================= Helper Methods =================================

    @Transactional
    public UUID createCategory(CategoryCreateRequestDTO categoryCreateRequestDTO) {
        log.info("Creating category with name: {}", categoryCreateRequestDTO);
        // 1) validation
        validateUniqueCategoryName(categoryCreateRequestDTO.description(), null);
        // 2) convert dto to entity
        Category category = categoryMapper.dtoToCategory(categoryCreateRequestDTO);
        // 3) save entity
        Category saveCategory = categoryRepository.save(category);
        log.info("Successfully created category with ID: {}", category.getId());
        return saveCategory.getId();
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponseDTO> getAllCategories(String description, Pageable pageable) {
        log.info("Retrieving all categories: description={}, pageable={}", description, pageable);

        // 1) Initial categoryPage and StringUtils for filtering
        Page<Category> categoryPage;
        boolean hasDescription = StringUtils.hasText(description);

        // 2) Filter categories by description if provided
        if (hasDescription) {
            categoryPage = categoryRepository.findByDescriptionContainingIgnoreCase(description, pageable);
        } else {
            categoryPage = categoryRepository.findAll(pageable);
        }
        return categoryPage.map(categoryMapper::categoryToResponseDto);
    }

    @Transactional(readOnly = true)
    public CategoryResponseDTO getCategoryById(UUID categoryId) {
        log.info("Retrieving category with ID: {}", categoryId);
        return categoryMapper.categoryToResponseDto(getCategoryOrThrow(categoryId));
    }


    /**
     * Validates that a category name is unique.
     * For updates, excludeId allows the current category to keep its name.
     * Case-insensitive check prevents "Pilsner" and "pilsner" duplicates.
     * @param categoryName
     * @param excludeId
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
     * Retrieves a category by its ID, throwing a ResourceNotFoundException if not found.
     * @param categoryId The ID of the category to retrieve.
     * @return The found category.
     * @throws ResourceNotFoundException If the category with the given ID is not found.
     */
    private Category getCategoryOrThrow(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId.toString()));
    }
}
