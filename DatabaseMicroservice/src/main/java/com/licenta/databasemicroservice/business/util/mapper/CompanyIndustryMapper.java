package com.licenta.databasemicroservice.business.util.mapper;

import com.licenta.databasemicroservice.business.model.CompanyIndustryDTO;
import com.licenta.databasemicroservice.persistence.entity.CompanyIndustry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

public interface CompanyIndustryMapper {

    @Mapping(target="companyId", source="company.id")
    @Mapping(target="industryId", source="industry.id")
    CompanyIndustryDTO toResponse(CompanyIndustry companyIndustry);
}
