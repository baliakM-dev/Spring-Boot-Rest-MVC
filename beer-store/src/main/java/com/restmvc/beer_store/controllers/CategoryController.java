package com.restmvc.beer_store.controllers;

import com.restmvc.beer_store.dtos.category.CategoryCreateRequestDTO;
import com.restmvc.beer_store.dtos.category.CategoryResponseDTO;
import com.restmvc.beer_store.services.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * Rest controller responsible for mapping between Category resources.
 *
 * <p>
 *     Provides CRUD operations for Category entities.
 * </p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *    <li><b>POST</b></></li>
 *    <li><b>GET</b></></li>
 *    <li><b>GET</b></></li>
 *    <li><b>UPDATE</b></></li>
 *    <li><b>DELETE</b></></li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@RestController
public class CategoryController {


    public static final String BASE_URL = "/api/v1/categories";
    public static final String BASE_URL_ID = BASE_URL + "/{categoryId}";

    private final CategoryService categoryService;

    @PostMapping(BASE_URL)
    public ResponseEntity<Void> createCategory(@Validated @RequestBody CategoryCreateRequestDTO categoryCreateRequestDTO) {
        log.info("Creating category: {}", categoryCreateRequestDTO);
        var savedCategory = categoryService.createCategory(categoryCreateRequestDTO);
        return ResponseEntity.created(URI.create(BASE_URL + "/" + savedCategory)).build();
    }

    @GetMapping(BASE_URL)
    public ResponseEntity<Page<CategoryResponseDTO>> getAllCategories(
            @RequestParam(required = false) String description,
            @PageableDefault(sort = "description", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        log.info("Retrieving all categories");
        return ResponseEntity.ok(categoryService.getAllCategories(description, pageable));
    }

    @GetMapping(BASE_URL_ID)
    public ResponseEntity<CategoryResponseDTO> getCategoryById(@PathVariable UUID categoryId) {
        log.info("Retrieving category with ID: {}", categoryId);
        return ResponseEntity.ok(categoryService.getCategoryById(categoryId));
    }
}

