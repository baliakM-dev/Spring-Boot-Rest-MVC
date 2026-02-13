package com.restmvc.beer_store.repositories;

import com.restmvc.beer_store.entities.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
}
