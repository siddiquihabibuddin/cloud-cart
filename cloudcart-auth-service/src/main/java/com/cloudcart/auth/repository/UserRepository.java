package com.cloudcart.auth.repository;

import com.cloudcart.auth.model.User;

public interface UserRepository {

    /**
     * Throws software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
     * if the email is already registered.
     */
    void createUser(User user);

    User getByEmail(String email);
}
