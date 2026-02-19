package com.restmvc.beer_store.controllers;

import com.restmvc.beer_store.dtos.category.CategoryCreateRequestDTO;
import com.restmvc.beer_store.dtos.category.CategoryResponseDTO;
import com.restmvc.beer_store.services.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * REST controller responsible for managing Category resources.
 *
 * <p>Provides CRUD operations for Category entities.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *     <li><b>POST</b>   /api/v1/categories       – Create a new category</li>
 *     <li><b>GET</b>    /api/v1/categories        – Retrieve a paginated list of categories</li>
 *     <li><b>GET</b>    /api/v1/categories/{id}   – Retrieve a category by ID</li>
 *     <li><b>PUT</b>    /api/v1/categories/{id}   – Fully update a category</li>
 *     <li><b>DELETE</b> /api/v1/categories/{id}   – Delete a category</li>
 * </ul>
 *
 * <p>All responses follow standard REST conventions and return appropriate HTTP status codes.</p>
 */
@Slf4j
@RequiredArgsConstructor
@RestController
public class CategoryController {

    public static final String BASE_URL = "/api/v1/categories";
    public static final String BASE_URL_ID = BASE_URL + "/{categoryId}";

    private final CategoryService categoryService;

    /**
     * Create a new category.
     *
     * @param categoryCreateRequestDTO the category data to create
     * @return HTTP 201 Created with the Location header pointing to the new resource
     */
    @PostMapping(BASE_URL)
    public ResponseEntity<Void> createCategory(@Valid @RequestBody CategoryCreateRequestDTO categoryCreateRequestDTO) {
        log.info("Creating category: {}", categoryCreateRequestDTO);
        var savedCategory = categoryService.createCategory(categoryCreateRequestDTO);
        return ResponseEntity.created(URI.create(BASE_URL + "/" + savedCategory)).build();
    }

    /**
     * Retrieve a paginated list of categories, optionally filtered by description.
     * Example:
     * <ul>
     *     <li><b>GET</b> /api/v1/categories?description=IPA</li>
     *     <li><b>GET</b> /api/v1/categories?page=0&amp;size=10&amp;sort=description,asc</li>
     * </ul>
     *
     * @param description optional description filter (case-insensitive, partial match)
     * @param pageable    pagination and sorting parameters
     * @return a page of {@link CategoryResponseDTO} matching the filter criteria
     */
    @GetMapping(BASE_URL)
    public ResponseEntity<Page<CategoryResponseDTO>> getAllCategories(
            @RequestParam(required = false) String description,
            @PageableDefault(sort = "description", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        log.info("Retrieving all categories");
        return ResponseEntity.ok(categoryService.getAllCategories(description, pageable));
    }

    /**
     * Retrieve a category by its ID.
     * Example:
     * <ul>
     *     <li><b>GET</b> /api/v1/categories/{categoryId}</li>
     * </ul>
     *
     * @param categoryId the UUID of the category to retrieve
     * @return {@link CategoryResponseDTO} with the category data
     */
    @GetMapping(BASE_URL_ID)
    public ResponseEntity<CategoryResponseDTO> getCategoryById(@PathVariable UUID categoryId) {
        log.info("Retrieving category with ID: {}", categoryId);
        return ResponseEntity.ok(categoryService.getCategoryById(categoryId));
    }

    /**
     * Fully update an existing category by its ID.
     * Example:
     * <ul>
     *     <li><b>PUT</b> /api/v1/categories/{categoryId}</li>
     * </ul>
     *
     * @param categoryId               the UUID of the category to update
     * @param categoryCreateRequestDTO the new category data
     * @return HTTP 204 No Content on success
     */
    @PutMapping(BASE_URL_ID)
    public ResponseEntity<CategoryResponseDTO> updateCategoryById(
            @PathVariable UUID categoryId,
            @Valid @RequestBody CategoryCreateRequestDTO categoryCreateRequestDTO
    ) {
        log.info("Updating category with ID: {}", categoryId);
        categoryService.updateCategoryById(categoryId, categoryCreateRequestDTO);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete a category by its ID.
     * Example:
     * <ul>
     *     <li><b>DELETE</b> /api/v1/categories/{categoryId}</li>
     * </ul>
     *
     * @param categoryId the UUID of the category to delete
     * @return HTTP 204 No Content on success
     */
    @DeleteMapping(BASE_URL_ID)
    public ResponseEntity<Void> deleteCategoryById(@PathVariable UUID categoryId) {
        log.info("Deleting category with ID: {}", categoryId);
        categoryService.deleteCategoryById(categoryId);
        return ResponseEntity.noContent().build();
    }
}
