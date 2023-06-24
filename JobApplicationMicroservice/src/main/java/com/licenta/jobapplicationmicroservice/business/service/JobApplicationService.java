package com.licenta.jobapplicationmicroservice.business.service;

import com.licenta.jobapplicationmicroservice.business.interfaces.IDatabaseService;
import com.licenta.jobapplicationmicroservice.business.interfaces.IFileService;
import com.licenta.jobapplicationmicroservice.business.interfaces.IJobApplicationService;
import com.licenta.jobapplicationmicroservice.business.interfaces.INotificationService;
import com.licenta.jobapplicationmicroservice.business.model.Company;
import com.licenta.jobapplicationmicroservice.business.model.CreateJobApplicationRequest;
import com.licenta.jobapplicationmicroservice.business.model.FileData;
import com.licenta.jobapplicationmicroservice.business.model.Job;
import com.licenta.jobapplicationmicroservice.business.model.JobApplicationResponse;
import com.licenta.jobapplicationmicroservice.business.model.JobApplicationStatusResponse;
import com.licenta.jobapplicationmicroservice.business.model.Message;
import com.licenta.jobapplicationmicroservice.business.model.Notification;
import com.licenta.jobapplicationmicroservice.business.model.RecruiterStatistics;
import com.licenta.jobapplicationmicroservice.business.model.Review;
import com.licenta.jobapplicationmicroservice.business.model.UpdateJobApplicationRequest;
import com.licenta.jobapplicationmicroservice.business.util.constants.Constants;
import com.licenta.jobapplicationmicroservice.business.util.exception.ExceptionWithStatus;
import com.licenta.jobapplicationmicroservice.business.util.exception.NotFoundException;
import com.licenta.jobapplicationmicroservice.business.util.mapper.JobApplicationMapper;
import com.licenta.jobapplicationmicroservice.persistence.document.JobApplication;
import com.licenta.jobapplicationmicroservice.persistence.document.JobApplicationStatus;
import com.licenta.jobapplicationmicroservice.persistence.repository.JobApplicationRepository;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.licenta.jobapplicationmicroservice.business.util.constants.Constants.CLOSED_JOB_STATUS_ID;
import static com.licenta.jobapplicationmicroservice.business.util.constants.Constants.INITIAL_STATUS_ID;
import static com.licenta.jobapplicationmicroservice.business.util.constants.Constants.JOB_APPLICATION_NOT_FOUND_MESSAGE;

@Service
public class JobApplicationService implements IJobApplicationService {

    @Autowired
    private IDatabaseService databaseService;

    @Autowired
    private INotificationService notificationService;

    @Autowired
    private JobApplicationStatusService jobApplicationStatusService;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private IFileService fileService;

    private final JobApplicationMapper jobApplicationMapper = Mappers.getMapper(JobApplicationMapper.class);

    private static final String FILES_PATH = "src/main/resources/job-applications-files/";

    @Override
    public void create(Long userId, CreateJobApplicationRequest request) {
        Optional<JobApplication> jobApplicationOptional = jobApplicationRepository
                .findByUserIdAndJobId(userId, request.getJobId());

        if(jobApplicationOptional.isPresent()) {
            throw new ExceptionWithStatus("You already applied to this job", HttpStatus.CONFLICT);
        }

        Job job = databaseService.getJob(request.getJobId());
        JobApplicationStatus status = jobApplicationStatusService.getStatusOrElseThrowException(INITIAL_STATUS_ID);

        if(job.getJobStatusId().equals(CLOSED_JOB_STATUS_ID)) {
            throw new ExceptionWithStatus("This job is closed", HttpStatus.CONFLICT);
        }

        JobApplication jobApplication = JobApplication.builder()
                .userId(userId)
                .job(job)
                .status(status)
                .messageList(Collections.emptyList())
                .review(Review.builder()
                        .rating(5)
                        .description("dummy description")
                        .build())
                .updatedAt(LocalDateTime.now())
                .build();

        jobApplicationRepository.save(jobApplication);

        buildAndSendNotification(
                job.getRecruiterId(),
                jobApplication.getId(),
                String.format(Constants.SUBMITTED_JOB_APPLICATION_TEXT_FORMAT, job.getTitle()),
                null
        );
    }

    @Override
    public JobApplicationResponse getById(String jobApplicationId) {
        JobApplication jobApplication = getJobApplicationOrElseThrowException(jobApplicationId);

        return jobApplicationMapper.toResponse(jobApplication);
    }

    @Override
    public Iterable<JobApplicationResponse> getByUserId(Long userId) {
        return StreamSupport.stream(jobApplicationRepository.findAllByUserId(userId).spliterator(), false)
                .map(jobApplicationMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Iterable<JobApplicationResponse> getByJobId(Long jobId) {
        verifyJobExists(jobId);

        return StreamSupport.stream(jobApplicationRepository.findAllByJobId(jobId).spliterator(), false)
                .map(jobApplicationMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void update(String jobApplicationId, UpdateJobApplicationRequest request) {
        JobApplication jobApplication = getJobApplicationOrElseThrowException(jobApplicationId);
        JobApplicationStatus status = jobApplicationStatusService.getStatusOrElseThrowException(request.getStatusId());

        if (status != jobApplication.getStatus()) {
            jobApplication.setStatus(status);
            jobApplicationRepository.save(jobApplication);

            Job job = databaseService.getJob(jobApplication.getJob().getId());
            Company company = databaseService.getCompany(job.getCompanyId());
            if (status.getName().equals(Constants.WITHDRAWN_STATUS)) {
                buildAndSendNotification(
                        job.getRecruiterId(),
                        jobApplication.getId(),
                        String.format(Constants.WITHDRAWN_JOB_APPLICATION_TEXT_FORMAT, job.getTitle()),
                        null
                );
            } else {
                buildAndSendNotification(
                        jobApplication.getUserId(),
                        jobApplication.getId(),
                        String.format(Constants.UPDATED_JOB_APPLICATION_TEXT_FORMAT, job.getTitle(), company.getName()),
                        null
                );
            }
        }
    }

    @Override
    public void delete(String jobApplicationId) {
        getJobApplicationOrElseThrowException(jobApplicationId);

        jobApplicationRepository.deleteById(jobApplicationId);
    }

    @Override
    public Iterable<JobApplicationStatusResponse> getStatus() {
        return jobApplicationStatusService.getStatus();
    }

    @Override
    public JobApplicationStatusResponse getStatusById(Integer statusId) {
        return jobApplicationStatusService.getStatusById(statusId);
    }

    @Override
    public Message addMessage(String jobApplicationId, Message message) {
        JobApplication jobApplication = getJobApplicationOrElseThrowException(jobApplicationId);

        message.setTimestamp(LocalDateTime.now());
        jobApplication.getMessageList().add(message);

        jobApplicationRepository.save(jobApplication);

        Job job = databaseService.getJob(jobApplication.getJob().getId());
        Company company = databaseService.getCompany(job.getCompanyId());

        if(jobApplication.getUserId().equals(message.getUserId())) {
            buildAndSendNotification(
                    job.getRecruiterId(),
                    jobApplication.getId(),
                    String.format(Constants.RECRUITER_NEW_MESSAGE_TEXT_FORMAT, job.getTitle()),
                    message
            );
        } else {
            buildAndSendNotification(
                    jobApplication.getUserId(),
                    jobApplication.getId(),
                    String.format(Constants.USER_NEW_MESSAGE_TEXT_FORMAT, job.getTitle(), company.getName()),
                    message
            );
        }
        return message;
    }

    @Override
    public Review updateReview(Long userId, String jobApplicationId, Review review) {
        JobApplication jobApplication = getJobApplicationOrElseThrowException(jobApplicationId);

        jobApplication.setReview(review);
        jobApplicationRepository.save(jobApplication);

        return jobApplication.getReview();
    }

    @Override
    public RecruiterStatistics getRecruiterStatistics(Long recruiterId) {
        List<JobApplication> jobApplications = jobApplicationRepository.findAllByRecruiterId(recruiterId);
        List<Review> reviews = jobApplications.stream()
                .map(JobApplication::getReview)
                .collect(Collectors.toList());
        Integer ratingCount = Math.max(1, Math.toIntExact(reviews.stream()
                .map(Review::getRating)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
                .size()));
        Integer ratingSum = reviews.stream()
                .map(Review::getRating)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);

        Integer currentSubmittedJobApplications = getCountWithStatus(jobApplications, 1);
        Integer currentReviewedJobApplications = getCountWithStatus(jobApplications, 2);
        Integer currentInProgressJobApplications = getCountWithStatus(jobApplications, 3);
        Integer currentOfferExtendedJobApplications = getCountWithStatus(jobApplications, 4);
        Integer currentHiredJobApplications = getCountWithStatus(jobApplications, 5);
        Integer currentOnHoldJobApplications = getCountWithStatus(jobApplications, 6);
        Integer currentDeclinedJobApplications = getCountWithStatus(jobApplications, 7);
        Integer currentWithdrawnJobApplications = getCountWithStatus(jobApplications, 8);

        DecimalFormat decimalFormat = new DecimalFormat("0.00");

        return RecruiterStatistics.builder()
                .recruiterId(recruiterId)
                .reviews(reviews)
                .averageRating(Double.valueOf(decimalFormat.format(ratingSum * 1.0 / ratingCount)))
                .totalJobApplications(jobApplications.size())
                .currentSubmittedJobApplications(currentSubmittedJobApplications)
                .currentReviewedJobApplications(currentReviewedJobApplications)
                .currentInProgressJobApplications(currentInProgressJobApplications)
                .currentOfferExtendedJobApplications(currentOfferExtendedJobApplications)
                .currentHiredJobApplications(currentHiredJobApplications)
                .currentOnHoldJobApplications(currentOnHoldJobApplications)
                .currentDeclinedJobApplications(currentDeclinedJobApplications)
                .currentWithdrawnJobApplications(currentWithdrawnJobApplications)
                .build();
    }

    @Override
    public List<FileData> getFileList(String jobApplicationId) {
        return fileService.list(FILES_PATH + jobApplicationId);
    }

    @Override
    public Resource download(String jobApplicationId, String filename) {
        return fileService.download(FILES_PATH + jobApplicationId, filename);
    }

    @Override
    public void saveFile(String jobApplicationId, MultipartFile file) throws IOException {
        getJobApplicationOrElseThrowException(jobApplicationId);


        byte[] fileBytes = file.getBytes();
        String path = FILES_PATH + "/" + jobApplicationId;

        File jobApplicationDirectory = new File(path);
        if (!jobApplicationDirectory.exists()){
            jobApplicationDirectory.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(path + '/' + file.getOriginalFilename())) {
            fos.write(fileBytes);
            fos.flush();
        }
    }

    private Integer getCountWithStatus(List<JobApplication> jobApplications, Integer jobApplicationStatusId) {
        return Math.toIntExact(jobApplications.stream()
                .map(jobApplication -> jobApplication.getStatus().getId())
                .filter(id -> Objects.equals(id, jobApplicationStatusId))
                .count());
    }

    private JobApplication getJobApplicationOrElseThrowException(String jobApplicationId) {

        return jobApplicationRepository.findById(jobApplicationId).orElseThrow(
                () -> new NotFoundException(String.format(JOB_APPLICATION_NOT_FOUND_MESSAGE, jobApplicationId))
        );
    }

    private void verifyJobExists(Long jobId) {
        databaseService.getJob(jobId);
    }

    private void buildAndSendNotification(Long userId, String jobApplicationId, String text, Message message) {
        Notification notification = Notification.builder()
                .userId(userId)
                .jobApplicationId(jobApplicationId)
                .text(text)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();

        notificationService.sendNotification(notification);
    }
}
