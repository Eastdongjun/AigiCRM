package com.aigicrm.module.customer.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerNameNormalizerTest {

    @Test
    void normalize_caseWidthSpacesAndChineseBrackets() {
        assertEquals("艾志(南京)环保 科技",
                CustomerNameNormalizer.normalize("  艾志（南京）环保   科技  "));
        assertEquals("abc 123", CustomerNameNormalizer.normalize("ＡＢＣ　　１２３"));
        assertEquals("test(公司)", CustomerNameNormalizer.normalize("ＴＥＳＴ【公司】"));
    }

    @Test
    void normalize_collapsesInternalWhitespace() {
        assertEquals("a b c", CustomerNameNormalizer.normalize("a\t\n b　c"));
    }

    @Test
    void normalize_removesZeroWidth() {
        assertEquals("客户名", CustomerNameNormalizer.normalize("客​户﻿名"));
    }

    @Test
    void normalizeOrThrow_rejectsEmptyAndOver100() {
        assertThrows(Exception.class, () -> CustomerNameNormalizer.normalizeOrThrow("   "));
        String tooLong = "客".repeat(101);
        assertThrows(Exception.class, () -> CustomerNameNormalizer.normalizeOrThrow(tooLong));
        assertEquals("客".repeat(100), CustomerNameNormalizer.normalizeOrThrow("客".repeat(100)));
    }

    @Test
    void displayName_onlyTrims() {
        assertEquals("艾志（南京）", CustomerNameNormalizer.displayName(" 艾志（南京） "));
    }
}
