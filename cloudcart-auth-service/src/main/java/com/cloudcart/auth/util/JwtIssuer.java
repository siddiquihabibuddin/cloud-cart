package com.cloudcart.auth.util;

public interface JwtIssuer {

    String issue(String userId, String email) throws Exception;
}
