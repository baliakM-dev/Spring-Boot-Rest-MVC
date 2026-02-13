package com.restmvc.beer_store.entities;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Entity test for {@link Beer}.
 * <p>
 *     Tests the basic functionality of the Beer entity.
 * </p>
 */
class BeerTest {
    private Beer beer() {
        return Beer.builder()
                .beerName("Test Beer")
                .upc("1235568")
                .price(BigDecimal.valueOf(10.99))
                .quantityOnHand(100)
                .build();
    }

    private Category category() {
        return Category.builder().description("Test Category").build();
    }

    @Test
    void should_CreateBeer() {
        Beer testBeer = beer();
        assertNotNull(testBeer);
        assertEquals("Test Beer", testBeer.getBeerName());
        assertEquals("1235568", testBeer.getUpc());
        assertEquals(BigDecimal.valueOf(10.99), testBeer.getPrice());
        assertEquals(100, testBeer.getQuantityOnHand());
    }

    @Test
    void should_CreateBeerWithCategory() {
        Beer testBeer = beer();
        testBeer.addCategory(category());
        assertNotNull(testBeer.getCategories());
        assertEquals(1, testBeer.getCategories().size());
    }

    @Test
    void should_ClearCategories() {
        Beer testBeer = beer();
        testBeer.addCategory(category());
        testBeer.clearCategories();
        assertNotNull(testBeer.getCategories());
        assertEquals(0, testBeer.getCategories().size());
    }

    @Test
    void should_addCategoryTwiceNoDuplicates() {
        Beer testBeer = beer();
        Category testCategory = category();
        testBeer.addCategory(testCategory);
        testBeer.addCategory(testCategory);

        assertNotNull(testBeer.getCategories());
        assertEquals(1, testBeer.getCategories().size());
    }
}