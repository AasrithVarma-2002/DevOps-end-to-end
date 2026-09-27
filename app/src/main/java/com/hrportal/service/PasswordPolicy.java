package com.hrportal.service;

import java.security.SecureRandom;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    // No look-alike characters (0/O, 1/l/I) so temporary passwords are easy to read out
    private static final String LETTERS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordPolicy() {
    }

    /** Returns an error message, or null when the password is acceptable. */
    public static String validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters";
        }
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            return "Password must contain both letters and numbers";
        }
        return null;
    }

    public static String temporaryPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        }
        // guarantee at least two digits in random positions
        for (int i = 0; i < 2; i++) {
            sb.insert(RANDOM.nextInt(sb.length() + 1), DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        }
        return sb.toString();
    }
}
