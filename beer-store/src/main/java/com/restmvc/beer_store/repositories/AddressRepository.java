package com.restmvc.beer_store.repositories;

import com.restmvc.beer_store.entities.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {
    List<Address> findByCustomerIdAndIsActiveTrue(UUID customerId);
}
