package dev.baliak.beerclient.controller;

import dev.baliak.beerclient.dto.BeerCreateRequestDTO;
import dev.baliak.beerclient.dto.BeerResponseDTO;
import dev.baliak.beerclient.dto.BeerUpdateRequestDTO;
import dev.baliak.beerclient.dto.PagedResponse;
import dev.baliak.beerclient.service.BeerRestClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@Slf4j
@RequiredArgsConstructor
public class BeerController {

    public static final String BASE_URL = "/client/beers";
    public static final String BASE_URL_ID = "/client/beers/{beerId}";
    private final BeerRestClientService beerService;

    @PostMapping(BASE_URL)
    public ResponseEntity<Void> createBeer(@Valid @RequestBody BeerCreateRequestDTO beerCreateRequestDTO) {
        log.info("Creating beer: {}", beerCreateRequestDTO);
        var savedBeer = beerService.createBeer(beerCreateRequestDTO);
        return ResponseEntity.created(URI.create(BASE_URL + "/" + savedBeer)).build();
    }

    @GetMapping(BASE_URL)
    public ResponseEntity<PagedResponse<BeerResponseDTO>> getAllBeers(
            @RequestParam(required = false) String beerName,
            @RequestParam(required = false) String upc,
            @RequestParam(required = false) Boolean showInventoryOnHand,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "25") Integer size) {

        return ResponseEntity.ok(
                beerService.getAllBeers(beerName, upc, showInventoryOnHand, page, size)
        );
    }

    @GetMapping(BASE_URL_ID)
    public ResponseEntity<BeerResponseDTO> getBeerById(@PathVariable UUID beerId) {
         return ResponseEntity.ok(beerService.getBeerById(beerId));
    }

    @PutMapping(BASE_URL_ID)
    public ResponseEntity<BeerResponseDTO> updateBeer(@PathVariable UUID beerId,
                                                      @Valid @RequestBody BeerUpdateRequestDTO beerUpdateRequestDTO) {
        return ResponseEntity.ok(beerService.updateBeerById(beerId, beerUpdateRequestDTO));
    }
}
