package com.boostofstudios.fukex

import com.boostofstudios.fukex.data.ReleaseParser
import com.boostofstudios.fukex.data.SemanticVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
	private fun newer(current: String, remote: String) = SemanticVersion.isNewer(current, remote)

	@Test
	fun comparesNumbersNotText() {
		assertTrue(newer("0.1.0", "0.1.1"))
		assertTrue(newer("0.9.0", "0.10.0"))
		assertTrue(newer("1.9.9", "2.0.0"))
		assertTrue(newer("0.1.0", "v0.2.0"))
	}

	@Test
	fun sameOrOlderIsNotAnUpdate() {
		assertFalse(newer("0.1.0", "0.1.0"))
		assertFalse(newer("0.1.0", "v0.1.0"))
		assertFalse(newer("1.2.0", "1.1.9"))
		assertFalse(newer("0.10.0", "0.9.0"))
	}

	@Test
	fun preReleasesSortBeforeTheirRelease() {
		assertTrue(newer("1.0.0-rc.1", "1.0.0"))
		assertFalse(newer("1.0.0", "1.0.0-rc.1"))
		assertTrue(newer("1.0.0-alpha", "1.0.0-beta"))
		assertTrue(newer("1.0.0-rc.2", "1.0.0-rc.10"))
		assertTrue(newer("1.0.0-1", "1.0.0-alpha"))
		assertTrue(newer("1.0.0-alpha", "1.0.0-alpha.1"))
	}

	@Test
	fun buildMetadataIsIgnoredAndShortVersionsWork() {
		assertFalse(newer("1.0.0+5", "1.0.0+9"))
		assertTrue(newer("1.0", "1.0.1"))
		assertEquals(SemanticVersion.parse("2"), SemanticVersion.parse("2.0.0"))
	}

	@Test
	fun unreadableVersionsNeverCountAsUpdates() {
		assertNull(SemanticVersion.parse("latest"))
		assertNull(SemanticVersion.parse(""))
		assertNull(SemanticVersion.parse(null))
		assertFalse(newer("0.1.0", "nightly"))
		assertFalse(newer("dev", "1.0.0"))
	}

	private val sha = "a".repeat(64)

	private fun release(
		tag: String = "v0.2.0", draft: Boolean = false, pre: Boolean = false,
		assets: String = """{"name":"FukeX-0.2.0.apk","size":4200000,"digest":"sha256:$sha","browser_download_url":"https://github.com/amanKG777/FukeX/releases/download/v0.2.0/FukeX-0.2.0.apk"}"""
	) = """{"tag_name":"$tag","draft":$draft,"prerelease":$pre,"body":" Faster. ","html_url":"https://github.com/amanKG777/FukeX/releases/tag/$tag","assets":[$assets]}"""

	@Test
	fun readsAReleaseWithItsApk() {
		val r = ReleaseParser.parse(release())!!
		assertEquals("0.2.0", r.version)
		assertEquals("Faster.", r.notes)
		assertEquals(4200000L, r.apkSizeBytes)
		assertEquals(sha, r.sha256)
		assertTrue(r.apkUrl.endsWith("FukeX-0.2.0.apk"))
	}

	@Test
	fun skipsDraftsPreReleasesAndReleasesWithoutAnApk() {
		assertNull(ReleaseParser.parse(release(draft = true)))
		assertNull(ReleaseParser.parse(release(pre = true)))
		assertNull(ReleaseParser.parse(release(assets = "")))
		assertNull(ReleaseParser.parse(release(tag = "nightly")))
		assertNull(ReleaseParser.parse("not json"))
	}

	@Test
	fun onlyTrustsApksHostedOnTheFukeXRepository() {
		val elsewhere = """{"name":"FukeX.apk","size":1,"browser_download_url":"https://evil.example/FukeX.apk"}"""
		assertNull(ReleaseParser.parse(release(assets = elsewhere)))
	}

	@Test
	fun prefersTheReleaseApkOverDebugAndUnsignedOnes() {
		fun asset(name: String) =
			"""{"name":"$name","size":1,"browser_download_url":"https://github.com/amanKG777/FukeX/releases/download/v0.2.0/$name"}"""
		val r = ReleaseParser.parse(release(assets = listOf("app-debug.apk", "app-release-unsigned.apk", "app-release.apk").joinToString(",") { asset(it) }))!!
		assertEquals("app-release.apk", r.apkName)
	}

	@Test
	fun ignoresAMalformedChecksum() {
		val bad = """{"name":"a.apk","size":1,"digest":"sha256:zzz","browser_download_url":"https://github.com/amanKG777/FukeX/releases/download/v0.2.0/a.apk"}"""
		assertNull(ReleaseParser.parse(release(assets = bad))!!.sha256)
	}
}
