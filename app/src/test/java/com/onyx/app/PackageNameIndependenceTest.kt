package com.onyx.app

import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le nom de paquet ne doit jamais être écrit en dur là où Android résout un **composant**.
 *
 * Android résout un composant (les alias du lanceur, un fournisseur) contre le paquet
 * **réellement installé** — `context.packageName` —, pas contre le paquet du code. Un alias
 * déclaré sous le paquet du code mais basculé sous l'identifiant installé n'existe pour personne :
 * l'utilisateur lit « Impossible de changer l'icône : Component class … does not exist », sans
 * qu'aucun journal ne le signale avant.
 *
 * Les **contrats** qui traversent les modules (permission des réglages Auto, autorité du
 * fournisseur, actions d'App Action) ne dépendent pas, eux, de l'identifiant d'installation : ils
 * portent le paquet du code, et ils doivent être **identiques** des deux côtés. Un contrat écrit
 * d'un côté avec l'identifiant d'installation et de l'autre avec le paquet du code ne casse pas au
 * démarrage : la montre n'entend jamais son action, l'Auto ne lit jamais ses réglages.
 *
 * Ce test tient les deux bouts, et il échoue dès qu'un paquet est remis en dur.
 */
class PackageNameIndependenceTest {

    private companion object {
        /**
         * L'ancien identifiant d'installation, assemblé plutôt qu'écrit : le balayage ci-dessous
         * doit pouvoir échouer sur n'importe quel fichier sans s'exempter lui-même.
         */
        val STALE_APPLICATION_ID = "com." + "red" + "de" + ".app"

        /** Le **seul** endroit où l'identité de l'application est écrite. */
        const val IDENTITY_FILE = "gradle.properties"

        /** Les répertoires qu'un balayage de dépôt ne visite pas. */
        val PRUNED = setOf("build", ".git", ".gradle", ".idea", "qa")
    }

    private val root = File("..")
    private val properties: Properties = Properties().apply {
        File(root, IDENTITY_FILE).inputStream().use { load(it) }
    }

    private val applicationId: String = properties.getProperty("onyx.applicationId")
    private val namespace: String = properties.getProperty("onyx.namespace")

    private fun text(moduleRelativePath: String): String = File(moduleRelativePath).readText()

    /** Tous les fichiers de code du dépôt, sans descendre dans les sorties de build. */
    private fun repositoryFiles(extensions: List<String>): List<File> {
        val found = mutableListOf<File>()
        val pending = ArrayDeque<File>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val directory = pending.removeLast()
            for (child in directory.listFiles().orEmpty()) {
                if (child.isDirectory) {
                    if (child.name !in PRUNED) pending.add(child)
                } else if (extensions.any { child.name.endsWith(it) }) {
                    found += child
                }
            }
        }
        return found
    }

    // --- 1. l'ancien identifiant ne doit plus apparaître nulle part ---------------------------

    @Test
    fun `aucun fichier ne nomme l ancien identifiant d application en dur`() {
        val offenders = repositoryFiles(listOf(".kt", ".kts", ".xml", ".pro", ".java", ".toml", ".py"))
            .filter { it.name != IDENTITY_FILE }
            .filter { it.readText().contains(STALE_APPLICATION_ID) }
            .map { it.path.removePrefix("../") }
            .sorted()

        assertFalse(
            "l'identité installée est celle du nom actuel : `$IDENTITY_FILE` ne déclare plus " +
                "l'ancien identifiant",
            applicationId == STALE_APPLICATION_ID,
        )
        assertTrue(
            "l'ancien identifiant ne vit que dans `$IDENTITY_FILE` ; ces fichiers le nomment " +
                "encore : ${offenders.joinToString(", ")}",
            offenders.isEmpty(),
        )
    }

    // --- 2. les alias du lanceur suivent le paquet installé ----------------------------------

    @Test
    fun `chaque alias du lanceur est nomme d apres le paquet installe`() {
        val manifest = text("src/main/AndroidManifest.xml")
        val names = Regex("<activity-alias\\b([^>]*)>", RegexOption.DOT_MATCHES_ALL)
            .findAll(manifest)
            .map { tag ->
                Regex("android:name=\"([^\"]*)\"").find(tag.groupValues[1])?.groupValues?.get(1).orEmpty()
            }
            .toList()

        assertEquals("un alias par icône", 14, names.size)
        names.forEach { name ->
            assertTrue(
                "un alias doit être préfixé par \${applicationId} (nommé « $name ») : Android le " +
                    "résout contre le paquet installé, sinon la bascule ne trouve aucun composant",
                name.startsWith("\${applicationId}."),
            )
        }
    }

    @Test
    fun `la bascule d icone compose le composant avec le paquet installe`() {
        val icon = text("src/main/java/com/onyx/app/ui/settings/AppIconSettings.kt")

        assertTrue(
            "le nom du composant vient de `context.packageName`",
            icon.contains("ComponentName(context.packageName, context.packageName + choice.aliasName)"),
        )
        assertFalse(
            "aucun paquet écrit en dur ne doit entrer dans la composition du composant",
            icon.contains("\"com."),
        )
    }

    @Test
    fun `les raccourcis ne nomment aucun paquet et se resolvent par leur action`() {
        val shortcuts = text("src/main/res/xml/shortcuts.xml")

        assertFalse(
            "un paquet écrit en dur dans `res/xml` (qui ne lit pas les placeholders) vise un paquet " +
                "qui n'existe pas sur le téléphone : l'appui rend « class not found ». Une " +
                "ressource `@string/…` ne rattrape rien — l'intention la garde telle quelle",
            Regex("android:targetPackage=\"").containsMatchIn(shortcuts),
        )
        assertTrue(
            "le raccourci porte son action : c'est elle que le manifeste accepte, donc la " +
                "résolution suit le paquet installé sans jamais le nommer",
            shortcuts.contains("android:action=\"$namespace.action."),
        )
        assertTrue(
            "et la classe visée reste celle du code, pour chaque intention",
            shortcuts.split("<intent").drop(1).all {
                it.substringBefore("/>").substringBefore('>')
                    .contains("android:targetClass=\"$namespace.MainActivity\"")
            },
        )
    }

    @Test
    fun `chaque alias du lanceur publie les raccourcis`() {
        val manifest = text("src/main/AndroidManifest.xml")
        val aliases = Regex("<activity-alias\\b([^>]*)>(.*?)</activity-alias>", RegexOption.DOT_MATCHES_ALL)
            .findAll(manifest)
            .toList()

        assertEquals("un alias par icône", 14, aliases.size)
        aliases.forEachIndexed { index, alias ->
            assertTrue(
                "l'alias #$index ne publie pas les raccourcis : le meta-data vit sur le composant " +
                    "LANCEUR, et un alias qui ne le porte pas laisse la liste vide sans erreur",
                alias.groupValues[2].contains("android:name=\"android.app.shortcuts\""),
            )
        }
    }

    // --- 3. aucun composant du manifeste nommé par un paquet littéral ------------------------

    @Test
    fun `aucun composant de nos manifestes n est nomme par un paquet litteral`() {
        val ours = listOf(STALE_APPLICATION_ID, namespace)
        val manifests = listOf(
            "src/main/AndroidManifest.xml",
            "src/debug/AndroidManifest.xml",
            "../auto/src/main/AndroidManifest.xml",
            "../wear/src/main/AndroidManifest.xml",
        )
        val tag = Regex("<(activity|activity-alias|service|receiver|provider)\\b([^>]*)>", RegexOption.DOT_MATCHES_ALL)

        manifests.forEach { path ->
            tag.findAll(text(path)).forEach { match ->
                val name = Regex("android:name=\"([^\"]*)\"").find(match.groupValues[2])
                    ?.groupValues?.get(1).orEmpty()
                assertFalse(
                    "$path : le composant « $name » est nommé par un paquet littéral — un composant " +
                        "se nomme relativement (`.X`) ou par `\${applicationId}`",
                    ours.any { name.startsWith("$it.") },
                )
            }
        }
    }

    // --- 4. ni composant ni paquet passé en littéral à la gestion des composants -------------

    @Test
    fun `le code ne passe jamais un paquet litteral a la gestion des composants`() {
        val calls = listOf(
            "ComponentName(", "setComponentEnabledSetting(", "getComponentEnabledSetting(",
            "getPackageInfo(", "createPackageContext(", "getLaunchIntentForPackage(",
            "getApplicationInfo(", "setPackage(",
        )
        val offenders = mutableListOf<String>()

        repositoryFiles(listOf(".kt"))
            .filter { it.path.contains("/src/") }
            .forEach { file ->
                val source = file.readText()
                calls.forEach { call ->
                    Regex(Regex.escape(call) + "([^)]{0,240})", RegexOption.DOT_MATCHES_ALL)
                        .findAll(source)
                        .forEach { hit ->
                            if (Regex("\"com\\.").containsMatchIn(hit.groupValues[1])) {
                                offenders += "${file.path.removePrefix("../")} → $call…"
                            }
                        }
                }
            }

        assertTrue(
            "un paquet écrit en dur ici ne suivra pas l'identité réellement installée : " +
                offenders.joinToString(", "),
            offenders.isEmpty(),
        )
    }

    // --- 5. les contrats partagés entre modules disent la même chose --------------------------

    /** Remplace les placeholders par leur valeur déclarée, comme la fusion de manifestes. */
    private fun resolved(raw: String): String = raw
        .replace("\${applicationId}", applicationId)
        .replace("\${onyxNamespace}", namespace)

    private fun attribute(path: String, tag: String, markers: List<String>, attribute: String): String {
        val block = Regex("<($tag)\\b([^>]*)>", RegexOption.DOT_MATCHES_ALL).findAll(text(path))
            .firstOrNull { hit -> markers.all { marker -> hit.groupValues[2].contains(marker) } }
            ?: error("« $tag » introuvable dans $path (repères : $markers)")
        return Regex("$attribute=\"([^\"]*)\"").find(block.groupValues[2])!!.groupValues[1]
    }

    private fun contract(field: String): String =
        Regex("$field = \"([^\"]*)\"")
            .find(text("../core/src/main/java/com/onyx/app/car/AutoLooks.kt"))!!.groupValues[1]

    @Test
    fun `la permission des reglages Auto est la meme des deux cotes`() {
        val declared = attribute(
            "src/main/AndroidManifest.xml", "permission", listOf("AUTO_SETTINGS"), "android:name",
        )
        val requested = attribute(
            "../auto/src/main/AndroidManifest.xml", "uses-permission", listOf("AUTO_SETTINGS"), "android:name",
        )

        assertEquals(
            "la permission déclarée suit le paquet du code",
            "$namespace.permission.AUTO_SETTINGS", resolved(declared),
        )
        assertEquals(
            "le compagnon Auto demande exactement la permission déclarée",
            resolved(declared), resolved(requested),
        )
        assertEquals("et le code partage la même valeur", resolved(declared), contract("PERMISSION"))
    }

    @Test
    fun `l autorite du fournisseur de reglages Auto est la meme des deux cotes`() {
        val provider = attribute(
            "src/main/AndroidManifest.xml", "provider", listOf("auto_settings"), "android:authorities",
        )

        assertEquals("$namespace.auto_settings", resolved(provider))
        assertEquals(resolved(provider), contract("AUTHORITY"))
    }

    @Test
    fun `l action de question est la meme dans les quatre fichiers`() {
        val action = "$namespace.action.ASK"

        assertEquals(
            "le manifeste de l'appli",
            action,
            resolved(
                attribute(
                    "src/main/AndroidManifest.xml", "action",
                    listOf("\${onyxNamespace}.action.ASK"), "android:name",
                ),
            ),
        )
        assertTrue(
            "l'activité écoute la même action",
            text("src/main/java/com/onyx/app/MainActivity.kt").contains("const val ACTION_ASK = \"$action\""),
        )
        assertTrue(
            "le raccourci porte la même action",
            text("src/main/res/xml/shortcuts.xml").contains("android:action=\"$action\""),
        )
        assertEquals(
            "la montre écoute la même action",
            action,
            resolved(
                attribute(
                    "../wear/src/main/AndroidManifest.xml", "action",
                    listOf(".action.ASK"), "android:name",
                ),
            ),
        )
    }

    // --- 6. l'identité est déclarée une fois, et lue partout ---------------------------------

    @Test
    fun `les trois modules lisent l identite au lieu de l ecrire`() {
        listOf("app", "auto", "wear").forEach { module ->
            val build = text("../$module/build.gradle.kts")
            assertTrue(
                "$module doit lire `onyx.applicationId` plutôt que d'écrire son identifiant",
                build.contains("providers.gradleProperty(\"onyx.applicationId\")"),
            )
            assertTrue(
                "$module doit injecter le paquet du code dans son manifeste",
                build.contains("manifestPlaceholders[\"onyxNamespace\"]"),
            )
        }
    }

    @Test
    fun `le renommage se propage d un coup aux trois manifestes`() {
        // Rien n'est figé ailleurs que dans `gradle.properties` : `-Ponyx.applicationId=<nouveau>`
        // suffit à renommer l'application, ses compagnons et tous leurs contrats d'un coup.
        val appBuild = text("../app/build.gradle.kts")
        assertFalse(
            "l'identifiant n'est plus écrit en dur dans le build de l'appli",
            appBuild.contains("\"$STALE_APPLICATION_ID\""),
        )
        assertFalse(
            "ni le paquet du code",
            appBuild.contains("namespace = \"$namespace\""),
        )
        assertEquals("le paquet du code", namespace, properties.getProperty("onyx.namespace"))
    }
}
