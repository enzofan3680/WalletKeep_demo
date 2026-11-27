package com.example.walletkeep_demo;

import com.example.walletkeep_demo.util.StringUtils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StringUtilsTest {

    @Test
    public void firstCodePointOr_keepsSurrogatePairIntact() {
        assertEquals("😀", StringUtils.firstCodePointOr("😀奶茶", "类"));
        assertEquals("a", StringUtils.firstCodePointOr("abc", "类"));
        assertEquals("类", StringUtils.firstCodePointOr("", "类"));
        assertEquals("类", StringUtils.firstCodePointOr("   ", "类"));
        assertEquals("类", StringUtils.firstCodePointOr(null, "类"));
    }
}
