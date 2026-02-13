# Spring-Boot-Rest-MVC
Beer order project.

POST   /api/v1/categories                         # Create category (NO beers!)
GET    /api/v1/categories                         # List all (NO beers!)
GET    /api/v1/categories/{id}                    # Get one (NO beers!)
PUT    /api/v1/categories/{id}                    # Update category
DELETE /api/v1/categories/{id}                    # Delete category


# ==================== SPECIAL QUERIES ====================
GET    /api/v1/beers/by-category/{categoryId}    # Beers in category (PAGED!)