package com.codex.suishouledger.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotMonitorServiceTest {
    @Test
    fun detectsCommonScreenshotNamesAndFolders() {
        assertTrue(looksLikePaymentScreenshot("Screenshot_20260610_120000.jpg", "", ""))
        assertTrue(looksLikePaymentScreenshot("IMG_20260610.png", "Screenshots", "Pictures/Screenshots/"))
        assertTrue(looksLikePaymentScreenshot("截屏_20260610.jpg", "", "DCIM/截屏/"))
        assertTrue(looksLikePaymentScreenshot("微信截图_20260610.png", "", "Pictures/"))
    }

    @Test
    fun ignoresOrdinaryCameraPhotos() {
        assertFalse(looksLikePaymentScreenshot("IMG_20260610_120000.jpg", "Camera", "DCIM/Camera/"))
        assertFalse(looksLikePaymentScreenshot("holiday.jpg", "Pictures", "Pictures/Vacation/"))
    }
}
