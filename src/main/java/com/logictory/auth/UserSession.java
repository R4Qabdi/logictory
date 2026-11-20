package com.logictory.auth;

public class UserSession {
    private final Integer userId;
    private final String username;
    private final boolean guest;

    private UserSession(Integer userId, String username, boolean guest) {
        this.userId = userId;
        this.username = username;
        this.guest = guest;
    }

    public static UserSession guest() {
        return new UserSession(null, "Guest", true);
    }

    public static UserSession authenticated(int userId, String username) {
        return new UserSession(userId, username, false);
    }

    public Integer getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public boolean isGuest() {
        return guest;
    }
}
