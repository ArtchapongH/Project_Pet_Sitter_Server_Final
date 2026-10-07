package com.techup.pet_sitter.controller;

import com.techup.pet_sitter.entity.Location;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    @GetMapping
    public List<Location> getLocations() {

        return List.of(

            new Location(
                1L,
                "Bangkok Location",
                13.7563,
                100.5018
            ),

            new Location(
                2L,
                "Location B",
                13.7465,
                100.5348
            ),

            new Location(
                3L,
                "Location C",
                13.7650,
                100.5150
            )

        );
    }

    @PostMapping
    public Location createLocation(
            @RequestBody Location location
    ) {

        System.out.println(
            "Received location: "
            + location.getLatitude()
            + ", "
            + location.getLongitude()
        );

        return location;
    }
}
