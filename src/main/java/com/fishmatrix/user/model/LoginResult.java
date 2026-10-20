package com.fishmatrix.user.model;

public record LoginResult(String token, UserView user) {
}
