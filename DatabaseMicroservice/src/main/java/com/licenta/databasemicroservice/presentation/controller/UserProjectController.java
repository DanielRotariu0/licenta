package com.licenta.databasemicroservice.presentation.controller;

import com.licenta.databasemicroservice.business.interfaces.IUserProjectService;
import com.licenta.databasemicroservice.business.model.UserProjectDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Min;

@Validated
@RestController
@CrossOrigin(origins = "http://localhost:4200", maxAge = 3600)
public class UserProjectController {

    @Autowired
    private IUserProjectService userProjectService;

    @ResponseStatus(HttpStatus.CREATED)
    @RequestMapping(value="/projects", method=RequestMethod.POST)
    public void add(@Valid @RequestBody UserProjectDTO userProjectDTO) {

        userProjectService.add(userProjectDTO);
    }

    @RequestMapping(value="/users/{userId}/projects", method=RequestMethod.GET)
    public Iterable<UserProjectDTO> getByUserId(@Min(1) @PathVariable Long userId) {

        return userProjectService.getByUserId(userId);
    }

    @RequestMapping(value="/projects/{projectId}", method=RequestMethod.DELETE)
    public void delete(@Min(1) @PathVariable Long projectId) {

        userProjectService.delete(projectId);
    }
}