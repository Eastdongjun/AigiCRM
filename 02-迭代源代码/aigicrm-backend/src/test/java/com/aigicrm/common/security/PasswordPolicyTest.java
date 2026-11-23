package com.aigicrm.common.security;

import com.aigicrm.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    @Test
    void acceptsLettersAndDigitsWithinRange() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("Abc12345"));
        assertDoesNotThrow(() -> PasswordPolicy.validate("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5"));
    }

    @Test
    void rejectsTooShortOrTooLong() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("Abc1234"));
        String tooLong = "A1" + "x".repeat(29);
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate(tooLong));
    }

    @Test
    void rejectsMissingLetterOrDigit() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("12345678"));
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("abcdefgh"));
    }
}
