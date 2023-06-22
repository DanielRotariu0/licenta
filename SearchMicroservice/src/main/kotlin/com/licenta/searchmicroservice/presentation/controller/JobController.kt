package com.licenta.searchmicroservice.presentation.controller

import com.licenta.searchmicroservice.business.interfaces.IFilterService
import com.licenta.searchmicroservice.business.model.JobQuery
import com.licenta.searchmicroservice.business.model.JobQueryResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*


@Validated
@RestController
@CrossOrigin
@RequestMapping(value = ["/api/jobs"])
class JobController {

    @Autowired
    private lateinit var jobFilterService: IFilterService

    @GetMapping
    fun getFilteredJobs(
        @RequestParam(defaultValue = "true") cached: Boolean,
        @RequestParam(defaultValue = "") title: List<String>,
        @RequestParam(defaultValue = "") cityId: List<Int>,
        @RequestParam(defaultValue = "") countryId: List<Int>,
        @RequestParam(defaultValue = "") companyId: List<Long>,
        @RequestParam(defaultValue = "") industryId: List<Int>,
        @RequestParam(defaultValue = "") companyIndustryId: List<Long>,
        @RequestParam(defaultValue = "") workTypeId: List<Int>,
        @RequestParam(defaultValue = "") jobTypeId: List<Int>,
        @RequestParam(defaultValue = "") experienceLevelId: List<Int>,
        @RequestParam(defaultValue = "") descriptionKeyword: List<String>,
        @RequestParam(defaultValue = "2000-01-01 00:00:00") postedSince: String,
        @RequestParam(defaultValue = "") jobStatusId: List<Int>,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): JobQueryResponse {

        val jobQuery = JobQuery(
            title,
            cityId,
            countryId,
            companyId,
            industryId,
            companyIndustryId,
            workTypeId,
            jobTypeId,
            experienceLevelId,
            descriptionKeyword,
            postedSince,
            jobStatusId
        )

        return jobFilterService.getFilteredJobs(jobQuery, cached, page, size)
    }
}
