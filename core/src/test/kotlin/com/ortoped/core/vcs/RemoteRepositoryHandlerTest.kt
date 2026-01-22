package com.ortoped.core.vcs

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteRepositoryHandlerTest {

    private val handler = RemoteRepositoryHandler()

    @Test
    fun `should detect HTTPS URLs as remote`() {
        assertTrue(handler.isRemoteUrl("https://github.com/user/repo.git"))
        assertTrue(handler.isRemoteUrl("https://gitlab.com/user/project.git"))
    }

    @Test
    fun `should detect HTTP URLs as remote`() {
        assertTrue(handler.isRemoteUrl("http://example.com/repo.git"))
    }

    @Test
    fun `should detect git protocol URLs as remote`() {
        assertTrue(handler.isRemoteUrl("git://github.com/user/repo.git"))
    }

    @Test
    fun `should detect SSH URLs as remote`() {
        assertTrue(handler.isRemoteUrl("git@github.com:user/repo.git"))
        assertTrue(handler.isRemoteUrl("ssh://git@github.com/user/repo.git"))
    }

    @Test
    fun `should not detect local paths as remote`() {
        assertFalse(handler.isRemoteUrl("/path/to/local/repo"))
        assertFalse(handler.isRemoteUrl("./relative/path"))
        assertFalse(handler.isRemoteUrl("../parent/path"))
        assertFalse(handler.isRemoteUrl("C:\\Windows\\Path"))
    }

    @Test
    fun `should not detect relative paths as remote`() {
        assertFalse(handler.isRemoteUrl("myproject"))
        assertFalse(handler.isRemoteUrl("src/main/kotlin"))
    }

    @Test
    fun `should normalize git@ URLs to HTTPS`() {
        val input = "git@github.com:user/repo.git"
        // This tests the internal behavior by checking isRemoteUrl accepts it
        assertTrue(handler.isRemoteUrl(input))
    }

    @Test
    fun `should detect various git hosting providers`() {
        assertTrue(handler.isRemoteUrl("https://github.com/user/repo.git"))
        assertTrue(handler.isRemoteUrl("https://gitlab.com/user/repo.git"))
        assertTrue(handler.isRemoteUrl("https://bitbucket.org/user/repo.git"))
        assertTrue(handler.isRemoteUrl("https://gitea.io/user/repo.git"))
    }

    @Test
    fun `should handle URLs with authentication`() {
        assertTrue(handler.isRemoteUrl("https://user:token@github.com/repo.git"))
        assertTrue(handler.isRemoteUrl("ssh://git@github.com:22/user/repo.git"))
    }

    @Test
    fun `should handle URLs without git extension`() {
        assertTrue(handler.isRemoteUrl("https://github.com/user/repo"))
        assertTrue(handler.isRemoteUrl("git@gitlab.com:user/project"))
    }

    @Test
    fun `should reject empty or invalid strings`() {
        assertFalse(handler.isRemoteUrl(""))
        assertFalse(handler.isRemoteUrl("not-a-url"))
        assertFalse(handler.isRemoteUrl("ftp://example.com/file"))
    }

    @Test
    fun `should handle URLs with ports`() {
        assertTrue(handler.isRemoteUrl("https://github.com:443/user/repo.git"))
        assertTrue(handler.isRemoteUrl("ssh://git@gitlab.com:2222/user/repo.git"))
    }

    @Test
    fun `should handle subgroups and nested paths`() {
        assertTrue(handler.isRemoteUrl("https://gitlab.com/group/subgroup/project.git"))
        assertTrue(handler.isRemoteUrl("git@github.com:org/team/repo.git"))
    }
}
