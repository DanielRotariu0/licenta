package com.licenta.databasemicroservice.presentation.controller;

import com.licenta.databasemicroservice.business.interfaces.ICountryService;
import com.licenta.databasemicroservice.business.model.country.CountryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Min;

@Validated
@RestController
@CrossOrigin(origins = "http://localhost:4200", maxAge = 3600)
@RequestMapping(value="/countries")
public class CountryController {

    @Autowired
    private ICountryService countryService;

    @RequestMapping(method= RequestMethod.GET)
    public Iterable<CountryResponse> getAllCountries() {

        return countryService.getCountries();
    }

    @RequestMapping(value="/{countryId}", method=RequestMethod.GET)
    public CountryResponse getCountry(@Min(1) @PathVariable Integer countryId) {

        return countryService.getCountry(countryId);
    }
}