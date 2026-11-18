package com.aigicrm.module.incentive.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P0-5/B14：槽位 JSON 线性解析分支覆盖（替代原正则，防 S5998 回溯）。 */
class SlotJsonParserTest {

    private static boolean valid(String s) throws Exception {
        Method m = IncentiveService.class.getDeclaredMethod("isValidSlotJson", String.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, s);
    }

    @Test
    void validForms() throws Exception {
        assertTrue(valid("[1]"));
        assertTrue(valid("[1,2]"));
        assertTrue(valid("[01]"));
        assertTrue(valid("[123456789012345678]"));
    }

    @Test
    void invalidForms() throws Exception {
        assertFalse(valid("[]"));
        assertFalse(valid("[1,]"));
        assertFalse(valid("[1,,2]"));
        assertFalse(valid("[ 1]"));
        assertFalse(valid("[1, 2]"));
        assertFalse(valid("[1234567890123456789]"));
        assertFalse(valid("[a]"));
        assertFalse(valid("[1,a]"));
        assertFalse(valid("1"));
        assertFalse(valid("[1]x"));
    }
}
