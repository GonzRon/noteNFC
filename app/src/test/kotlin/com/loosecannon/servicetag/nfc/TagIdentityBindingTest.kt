package com.loosecannon.servicetag.nfc

import com.loosecannon.servicetag.BuildConfig
import com.loosecannon.servicetag.core.nfc.TagIdentity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * The C9 binding, JVM half (target §4.8 test 1). One Gradle value produces the manifest
 * placeholder and these three BuildConfig fields; this test is what fails if anyone puts a second
 * copy of the identity anywhere. The *installed* manifest is proved by `TagIdentityDispatchTest`
 * on the emulator, which a string comparison cannot do.
 *
 * Gradle runs JVM unit tests with the module directory as the working directory; an IDE run
 * configuration may use the repository root, so both are tried (the idiom `MigrationTestSupport`
 * already uses).
 */
class TagIdentityBindingTest {

    private val identity = TagIdentity(
        externalDomain = BuildConfig.NDEF_EXTERNAL_DOMAIN,
        typeName = BuildConfig.NDEF_TYPE_NAME,
        aarPackage = BuildConfig.NDEF_AAR_PACKAGE,
    )

    private fun moduleFile(relative: String): File =
        listOf(File(relative), File("app/$relative")).firstOrNull { it.isFile }
            ?: error("cannot find $relative from ${File(".").absolutePath}")

    private val manifest: String by lazy { moduleFile("src/main/AndroidManifest.xml").readText() }
    private val buildScript: String by lazy { moduleFile("build.gradle.kts").readText() }

    @Test fun theIdentityIsTheOneWeMeant() {
        assertEquals("com.loosecannon.servicetag:tag", identity.externalType)
        assertEquals(identity.externalType.lowercase(), identity.externalType)
    }

    /** Invariant 6: the AAR pins this app, not a string that merely looks like it. */
    @Test fun theAarPackageIsThisApplicationId() {
        assertEquals(BuildConfig.APPLICATION_ID, identity.aarPackage)
    }

    /** The manifest must carry no identity literal at all — only the placeholder. */
    @Test fun theManifestFilterPathIsThePlaceholder() {
        // Plain strings with escapes, not raw strings: a raw string that ends in a quote runs
        // straight into its own terminator and is a trap for the next reader.
        assertTrue(
            "the NDEF filter path must be \${ndefTagPath}, never a literal",
            manifest.contains("android:path=\"\${ndefTagPath}\""),
        )
        assertEquals(
            "no identity literal may survive in the manifest", 0,
            Regex(Regex.escape(identity.externalType)).findAll(manifest).count(),
        )
    }

    /** Exactly one NDEF_DISCOVERED filter, and exactly one place that defines the placeholder. */
    @Ignore("two filters until task 6 drops md5_short")
    @Test fun oneFilterAndOneDefinition() {
        assertEquals(
            "one NDEF_DISCOVERED filter", 1,
            Regex("android.nfc.action.NDEF_DISCOVERED").findAll(manifest).count(),
        )
        assertEquals(
            "one definition of ndefTagPath", 1,
            Regex("""manifestPlaceholders\["ndefTagPath"]""").findAll(buildScript).count(),
        )
        assertTrue(
            "the placeholder is built from the identity vals, not from a literal",
            buildScript.contains("manifestPlaceholders[\"ndefTagPath\"] = \"/\$tagExternalDomain:\$tagTypeName\""),
        )
    }
}
