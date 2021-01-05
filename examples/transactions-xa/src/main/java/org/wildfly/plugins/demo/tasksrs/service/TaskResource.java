/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.plugins.demo.tasksrs.service;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import javax.annotation.Resource;
import javax.enterprise.context.RequestScoped;

import javax.inject.Inject;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;
import javax.transaction.xa.XAResource;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.wildfly.plugins.demo.mock.XAResourceMock;
import org.wildfly.plugins.demo.tasksrs.model.Task;
import org.wildfly.plugins.demo.tasksrs.model.TaskDaoImpl;
import org.wildfly.plugins.demo.tasksrs.model.TaskUser;
import org.wildfly.plugins.demo.tasksrs.model.TaskUserDaoImpl;

/**
 * A JAX-RS resource for exposing REST endpoints for Task manipulation
 */
@RequestScoped
@Path("/")
public class TaskResource {

    @Inject
    private TaskUserDaoImpl userDao;

    @Inject
    private TaskDaoImpl taskDao;

    @Inject
    private UserTransaction tx;

    @Inject
    private TransactionManager transactionManager;

    @POST
    @Path("tasks/title/{title}")
    public Response createTask(@Context UriInfo info, @Context SecurityContext context,
            @PathParam("title") @DefaultValue("task") String taskTitle) throws Exception {
        return createTask(info, context, taskTitle, new XAResourceMock());
    }

    @POST
    @Path("tasks/title/{title}/fail")
    public Response createTaskFail(@Context UriInfo info, @Context SecurityContext context,
                               @PathParam("title") @DefaultValue("task") String taskTitle) throws Exception {
        return createTask(info, context, taskTitle, new XAResourceMock(XAResourceMock.TestAction.COMMIT_THROW_XAER_RMERR));
    }

    @GET
    @Produces({"application/json"})
    @Path("tasks/id/{id}")
    public Task getTask(@Context SecurityContext context, @PathParam("id") Long id) {
        return taskDao.get(id);
    }

    @GET
    @Produces({"application/json"})
    @Path("tasks")
    public List<Task> getTasks(@Context SecurityContext context) {
        try {
            tx.begin();
            List<Task> tasks = getTasks(getUser(context));
            tx.commit();
            return tasks;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Response createTask(UriInfo info, SecurityContext context, String taskTitle, XAResource xaResource) {
        Task task = null;
        try {
            tx.begin();

            // saving data to database
            TaskUser user = getUser(context);
            task = new Task(taskTitle);
            taskDao.createTask(user, task);

            // adding XAResource to start 2PC
            transactionManager.getTransaction().enlistResource(xaResource);

            tx.commit();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        String rawPath = info.getAbsolutePath().getRawPath().replace("title/" + taskTitle, "id/" + task.getId().toString());
        UriBuilder uriBuilder = info.getAbsolutePathBuilder().replacePath(rawPath);
        URI uri = uriBuilder.build();

        return Response.created(uri).build();
    }

    private List<Task> getTasks(TaskUser user) {
        return taskDao.getAll(user);
    }

    private TaskUser getUser(SecurityContext context) {
        Principal principal = null;
        String name = "Anonymous";
        if (context != null) {
            principal = context.getUserPrincipal();
        }

        if (principal != null) {
            name = principal.getName();
        }

        return getUser(name);
    }

    private TaskUser getUser(String username) {

        try {
            TaskUser user = userDao.getForUsername(username);

            if (user == null) {
                user = new TaskUser(username);

                userDao.createUser(user);
            }

            return user;
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }
}
