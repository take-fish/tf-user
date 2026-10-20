package com.fishmatrix.user.model;

import java.util.List;

public record UserPage(List<UserView> items, int page, int size, long total, UserStats stats) {
}
