package com.licenta.jobapplicationmicroservice.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotEmpty;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name="job_application_status")
public class JobApplicationStatus {

    @Id
    @GeneratedValue
    private Integer id;

    @NotEmpty
    @Column(unique=true)
    private String name;
}