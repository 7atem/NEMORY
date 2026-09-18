package com.vaultbrain.feature.capture.share

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShareContentValidatorTest {

    @Test
    fun `supports plain text`() {
        assertThat(ShareContentValidator.isSupportedMime("text/plain")).isTrue()
    }

    @Test
    fun `supports HTML`() {
        assertThat(ShareContentValidator.isSupportedMime("text/html")).isTrue()
    }

    @Test
    fun `supports PDF`() {
        assertThat(ShareContentValidator.isSupportedMime("application/pdf")).isTrue()
    }

    @Test
    fun `supports any image MIME`() {
        assertThat(ShareContentValidator.isSupportedMime("image/jpeg")).isTrue()
        assertThat(ShareContentValidator.isSupportedMime("image/png")).isTrue()
        assertThat(ShareContentValidator.isSupportedMime("image/webp")).isTrue()
    }

    @Test
    fun `rejects unsupported MIME`() {
        assertThat(ShareContentValidator.isSupportedMime("video/mp4")).isFalse()
        assertThat(ShareContentValidator.isSupportedMime("audio/mpeg")).isFalse()
        assertThat(ShareContentValidator.isSupportedMime("application/zip")).isFalse()
    }

    @Test
    fun `rejects blank MIME`() {
        assertThat(ShareContentValidator.isSupportedMime("")).isFalse()
        assertThat(ShareContentValidator.isSupportedMime(null)).isFalse()
    }

    @Test
    fun `extracts URL from text`() {
        val text = "Check this out https://example.com/path?q=1 it's great"
        assertThat(ShareContentValidator.extractUrl(text)).isEqualTo("https://example.com/path?q=1")
    }

    @Test
    fun `returns null when no URL in text`() {
        assertThat(ShareContentValidator.extractUrl("Just plain text")).isNull()
    }

    @Test
    fun `extracts plain text from HTML`() {
        val html = "<p>Hello <b>world</b></p>"
        assertThat(ShareContentValidator.extractTextFromHtml(html)).isEqualTo("Hello world")
    }

    @Test
    fun `returns null for empty HTML`() {
        assertThat(ShareContentValidator.extractTextFromHtml("<br/>")).isNull()
    }

    @Test
    fun `sanitizes malicious filename`() {
        assertThat(
            ShareContentValidator.sanitizeFilename("../../../etc/passwd")
        ).doesNotContain("..")
        assertThat(
            ShareContentValidator.sanitizeFilename("/data/local/tmp/evil")
        ).doesNotContain("/")
    }

    @Test
    fun `allows content scheme only`() {
        assertThat(ShareContentValidator.isAllowedUriScheme("content")).isTrue()
        assertThat(ShareContentValidator.isAllowedUriScheme("file")).isFalse()
        assertThat(ShareContentValidator.isAllowedUriScheme("http")).isFalse()
    }
}
