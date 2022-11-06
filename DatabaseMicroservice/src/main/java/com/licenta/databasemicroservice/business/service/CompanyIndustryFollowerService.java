package com.licenta.databasemicroservice.business.service;

import com.licenta.databasemicroservice.business.interfaces.ICompanyIndustryFollowerService;
import com.licenta.databasemicroservice.business.model.companyindustry.CompanyIndustryResponse;
import com.licenta.databasemicroservice.business.model.companyindustryfollower.CompanyIndustryFollowerRequest;
import com.licenta.databasemicroservice.business.model.user.UserResponse;
import com.licenta.databasemicroservice.business.util.exception.AlreadyExistsException;
import com.licenta.databasemicroservice.business.util.exception.NotFoundException;
import com.licenta.databasemicroservice.business.util.mapper.CompanyIndustryMapper;
import com.licenta.databasemicroservice.persistence.entity.CompanyIndustry;
import com.licenta.databasemicroservice.persistence.entity.CompanyIndustryFollower;
import com.licenta.databasemicroservice.persistence.entity.User;
import com.licenta.databasemicroservice.persistence.repository.CompanyIndustryFollowerRepository;
import com.licenta.databasemicroservice.persistence.repository.CompanyIndustryRepository;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.stream.Collectors;


@Service
public class CompanyIndustryFollowerService implements ICompanyIndustryFollowerService {

    @Autowired
    private UserService userService;
    @Autowired
    private CompanyIndustryService companyIndustryService;
    @Autowired
    private CompanyIndustryRepository  companyIndustryRepository;
    @Autowired
    private CompanyIndustryFollowerRepository companyIndustryFollowerRepository;

    private final CompanyIndustryMapper companyIndustryMapper = Mappers.getMapper(CompanyIndustryMapper.class);

    private static final String COMPANY_FOLLOWER_ALREADY_EXISTS_MESSAGE = "User with id <%s> already follows company industry with id <%s>.";
    private static final String COMPANY_FOLLOWER_NOT_FOUND_MESSAGE = "User with id <%s> does not follow company industry with id <%s>.";

    @Override
    public void addCompanyIndustryFollower(CompanyIndustryFollowerRequest request) {
        String userId = request.getUserId();
        Integer companyIndustryId = request.getCompanyIndustryId();

        User user = userService.getUserOrElseThrowException(userId);
        CompanyIndustry companyIndustry = companyIndustryService.getCompanyIndustryOrElseThrowException(companyIndustryId);

        Optional<CompanyIndustryFollower> follower =
                companyIndustryFollowerRepository.findCompanyIndustryFollowerByCompanyIndustryIdAndUserId(companyIndustryId, userId);

        if (follower.isPresent()) {
            throw new AlreadyExistsException(String.format(COMPANY_FOLLOWER_ALREADY_EXISTS_MESSAGE, userId, companyIndustryId));
        }

        CompanyIndustryFollower newCompanyIndustryFollower = CompanyIndustryFollower.builder()
                .user(user)
                .companyIndustry(companyIndustry)
                .build();

        companyIndustryFollowerRepository.save(newCompanyIndustryFollower);
    }

    @Override
    public Iterable<UserResponse> getCompanyIndustryFollowers(Integer companyIndustryId) {

        companyIndustryService.getCompanyIndustryOrElseThrowException(companyIndustryId);

        return companyIndustryFollowerRepository.findCompanyIndustryFollowersByCompanyIndustryId(companyIndustryId).stream()
                .map(follower -> userService.getUser(follower.getUser().getId()))
                .collect(Collectors.toList());
    }

    @Override
    public Iterable<CompanyIndustryResponse> getFollowedCompanyIndustries(String userId) {

        userService.getUserOrElseThrowException(userId);

        return companyIndustryFollowerRepository.findCompanyIndustryFollowersByUserId(userId).stream()
                .map(follower -> companyIndustryRepository.findById(follower.getCompanyIndustry().getId()))
                .map(companyIndustry -> companyIndustryMapper.toResponse(companyIndustry.get()))
                .collect(Collectors.toList());
    }

    @Override
    public void removeCompanyIndustryFollower(CompanyIndustryFollowerRequest request) {
        String userId = request.getUserId();
        Integer companyIndustryId = request.getCompanyIndustryId();

        userService.getUserOrElseThrowException(userId);
        companyIndustryService.getCompanyIndustryOrElseThrowException(companyIndustryId);

        Optional<CompanyIndustryFollower> follower =
                companyIndustryFollowerRepository.findCompanyIndustryFollowerByCompanyIndustryIdAndUserId(companyIndustryId, userId);

        companyIndustryFollowerRepository.delete(follower.orElseThrow(
                () -> new NotFoundException(String.format(COMPANY_FOLLOWER_NOT_FOUND_MESSAGE, userId, companyIndustryId)))
        );
    }
}