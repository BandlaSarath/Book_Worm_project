package com.bookworm.controller;

import com.bookworm.entity.User;
import com.bookworm.service.UserService;
import org.springframework.security.core.Authentication;

/**
 * Base class providing a helper to resolve the authenticated {@link User}
 * from the Spring Security context.
 */
public abstract class BaseController {

    protected final UserService userService;

    protected BaseController(UserService userService) {
        this.userService = userService;
    }

    protected User currentUser(Authentication auth) {
        return userService.findByEmail(auth.getName());
    }
}
