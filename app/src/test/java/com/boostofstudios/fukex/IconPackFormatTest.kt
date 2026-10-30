package com.boostofstudios.fukex

import com.boostofstudios.fukex.data.IconPackException
import com.boostofstudios.fukex.data.IconPackFormat
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class IconPackFormatTest {
	private val packFile = File("../iconpacks/visual-icons.fxicons")
	private val indexFile = File("../iconpacks/index.json")

	private fun minimal(layer: String = """{"d":"M1 1 L5 5","style":"stroke"}""", extra: String = "") =
		"""{"format":1,"id":"test","name":"Test","version":1,"viewport":24,"icons":{"play":{"layers":[$layer]}}$extra}"""

	private fun assertRejected(json: String) {
		try {
			IconPackFormat.parse(json)
			fail("Expected the pack to be rejected")
		} catch (e: IconPackException) {
			// expected
		}
	}

	@Test
	fun shippedPackParses() {
		val pack = IconPackFormat.parse(packFile.readText())
		assertEquals("visual-icons", pack.id)
		assertTrue(pack.icons.size >= 40)
	}

	@Test
	fun catalogMatchesTheShippedPack() {
		val entry = IconPackFormat.parseCatalog(indexFile.readText()).single()
		assertEquals(packFile.name, entry.file)
		assertEquals("Run iconpacks/tools/build_pack.py after editing the pack", IconPackFormat.sha256Hex(packFile.readBytes()), entry.sha256)
		assertEquals(packFile.length(), entry.sizeBytes)
	}

	@Test
	fun packHasEveryIconTheAppAsksFor() {
		val source = File("src/main/java/com/boostofstudios/fukex/ui/FxIcons.kt").readText()
		val wanted = Regex("pick\\(\"([a-z_]+)\"").findAll(source).map { it.groupValues[1] }.toSet()
		val settings = File("src/main/java/com/boostofstudios/fukex/SettingsScreen.kt").readText()
		val sections = Regex("to \"(section_[a-z]+)\"").findAll(settings).map { it.groupValues[1] }.toSet()
		val have = IconPackFormat.parse(packFile.readText()).icons.keys
		assertTrue(wanted.size > 30)
		assertTrue("Missing: ${(wanted + sections) - have}", have.containsAll(wanted + sections))
	}

	@Test
	fun rejectsUnknownFormatsAndBadIds() {
		assertRejected(minimal().replace("\"format\":1", "\"format\":2"))
		assertRejected(minimal().replace("\"id\":\"test\"", "\"id\":\"../evil\""))
		assertRejected(minimal().replace("\"play\"", "\"Play Icon\""))
		assertRejected("not json at all")
	}

	@Test
	fun rejectsPathsThatAreNotPlainPathData() {
		assertRejected(minimal("""{"d":"M1 1 <script>","style":"stroke"}"""))
		assertRejected(minimal("""{"d":"M1 1 L5 5","style":"glow"}"""))
		assertRejected(minimal("""{"d":"M1 1 L5 5","alpha":3}"""))
	}

	@Test
	fun rejectsOversizedPacks() {
		assertRejected(" ".repeat(IconPackFormat.MAX_BYTES + 1))
	}

	@Test
	fun catalogSkipsEntriesThatCouldEscapeTheBaseUrl() {
		val sha = "a".repeat(64)
		fun entry(file: String, sha: String = this.let { "a".repeat(64) }) =
			"""{"id":"p","name":"P","version":1,"file":"$file","sha256":"$sha","sizeBytes":100}"""
		val catalog = IconPackFormat.parseCatalog(
			"""{"packs":[${entry("../../evil.fxicons")},${entry("https://evil.example/x.fxicons")},${entry("good.fxicons")},${entry("bad.fxicons", "xyz")}]}"""
		)
		assertEquals(listOf("good.fxicons"), catalog.map { it.file })
		assertEquals(sha, catalog.single().sha256)
	}
}
