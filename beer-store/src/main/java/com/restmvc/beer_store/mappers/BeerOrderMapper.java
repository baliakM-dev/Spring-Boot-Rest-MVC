package com.restmvc.beer_store.mappers;

import com.restmvc.beer_store.dtos.beerOrder.BeerOrderLineResponseDTO;
import com.restmvc.beer_store.dtos.beerOrder.BeerOrderResponseDTO;
import com.restmvc.beer_store.entities.BeerOrder;
import com.restmvc.beer_store.entities.BeerOrderLine;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface BeerOrderMapper {

    /**
     * Maps a {@link BeerOrder} entity to {@link BeerOrderResponseDTO}.
     *
     * <p>Field mappings:
     * <ul>
     *   <li>{@code customerId} ← {@code order.customer.id}</li>
     *   <li>{@code lines}      ← each {@link BeerOrderLine} mapped via {@link #toLineDTO}</li>
     * </ul>
     * All other fields (id, status, currency, amounts, promoCode, createdAt)
     * are mapped automatically — names match exactly between entity and DTO.
     * </p>
     */
    @Mapping(target = "customerId", source = "customer.id")
    BeerOrderResponseDTO toResponseDTO(BeerOrder order);

    /**
     * Maps a {@link BeerOrderLine} entity to {@link BeerOrderLineResponseDTO}.
     *
     * <p>Field mappings:
     * <ul>
     *   <li>{@code beerId}   ← {@code line.beer.id}</li>
     *   <li>{@code beerName} ← {@code line.beer.beerName} (snapshot — value at order time)</li>
     *   <li>{@code quantity} ← {@code line.orderQuantity}</li>
     * </ul>
     * unitPrice and lineSubtotal map automatically — names match.
     * </p>
     */
    @Mapping(target = "beerId",   source = "beer.id")
    @Mapping(target = "beerName", source = "beer.beerName")
    @Mapping(target = "quantity", source = "orderQuantity")
    BeerOrderLineResponseDTO toLineDTO(BeerOrderLine line);
}
