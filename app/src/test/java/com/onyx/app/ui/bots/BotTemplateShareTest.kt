package com.onyx.app.ui.bots

import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.BotFace
import com.onyx.app.ui.design.FaceEyes
import com.onyx.app.ui.design.FaceMark
import com.onyx.app.ui.design.FaceMouth
import com.onyx.app.ui.design.FaceShape
import java.util.Base64
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le partage d'un modèle de bot : un codec **pur** (aucun Android, aucun serveur), un aperçu qui
 * dit ce qui manque, et des refus qui ne se taisent pas.
 *
 * Ces tests sont le contrat du lot :
 *
 *  1. un aller-retour rend **exactement** le même modèle — prompt, visage, outils, compétences,
 *     modèle par défaut ;
 *  2. un lien qui porte une clé, un jeton ou une adresse est **refusé**, jamais nettoyé ;
 *  3. un format d'une version inconnue est **refusé**, jamais interprété au mieux ;
 *  4. l'aperçu **nomme** ce que le lien ne contient pas.
 */
class BotTemplateShareTest {
    private val fr = Locale.FRANCE

    /** Une charge JSON encodée comme le fait le codec, pour forger les liens que les tests refusent. */
    private fun forged(plain: String, linkVersion: String = "1"): String {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(plain.toByteArray(Charsets.UTF_8))
        return "onyx://bot?v=$linkVersion&t=$payload"
    }

    private fun rejected(link: String): BotTemplateShareException =
        assertThrows(BotTemplateShareException::class.java) { BotTemplateShareCodec.decode(link) }

    // MARK: - L'aller-retour

    @Test
    fun `un aller retour rend exactement le meme modele`() {
        val share = BotTemplateShare(
            spec = BotTemplateSpec.all.first { it.id == "researcher" },
            tools = listOf("web", "files"),
            skills = listOf("arxiv", "grounded-citations"),
            model = "hermes-4-405b",
        )

        val decoded = BotTemplateShareCodec.decode(share.link)

        assertEquals(share, decoded)
        assertEquals("Researcher", decoded.spec.title)
        assertEquals(decoded.spec, share.spec)
        assertEquals(listOf("web", "files"), decoded.tools)
        assertEquals(listOf("arxiv", "grounded-citations"), decoded.skills)
        assertEquals("hermes-4-405b", decoded.model)
    }

    @Test
    fun `chaque modele du catalogue fait l aller retour avec son propre visage`() {
        val named = BotTemplateSpec.all.filter { it.id != BotTemplateSpec.blank.id }
        assertEquals("cinq modèles nommés", 5, named.size)
        named.forEach { template ->
            val decoded = BotTemplateShareCodec.decode(BotTemplateShare.of(template).link)
            assertEquals(template, decoded.spec)
            assertNotNull("${template.id} garde son visage", decoded.spec.face)
        }
    }

    @Test
    fun `un visage ancien garde ses identifiants locaux`() {
        // `circle` et `line` sont les formes d'avant le portage : les identifiants serveur les
        // normaliseraient (`round`, `sleepy`). Un aller-retour doit rendre le même visage.
        val face = BotFace(
            shape = FaceShape.CIRCLE,
            eyes = FaceEyes.LINE,
            mouth = FaceMouth.LINE,
            mark = FaceMark.NONE,
            colour = 0,
            glance = 3,
            tilt = -7,
        )
        val spec = BotTemplateSpec("vecu", "Vecu", "Un bot d'avant.", "# Vecu\n\nBonjour.", face)

        val decoded = BotTemplateShareCodec.decode(BotTemplateShare.of(spec).link)

        assertEquals(face, decoded.spec.face)
        assertEquals(FaceShape.CIRCLE, decoded.spec.face?.shape)
        assertEquals(FaceEyes.LINE, decoded.spec.face?.eyes)
    }

    @Test
    fun `un modele sans visage ni outil reste valide`() {
        val spec = BotTemplateSpec("scout", "Scout", "Regarde devant.", "# Scout\n\nTu regardes devant.")
        val share = BotTemplateShare.of(spec)

        val decoded = BotTemplateShareCodec.decode(share.link)

        assertEquals(share, decoded)
        assertNull(decoded.spec.face)
        assertTrue(decoded.tools.isEmpty())
        assertNull(decoded.model)
    }

    @Test
    fun `le prompt ne voyage pas en clair dans le lien`() {
        val share = BotTemplateShare.of(BotTemplateSpec.all.first { it.id == "writer" })

        val link = share.link

        assertTrue("le lien porte le schéma", link.startsWith("onyx://bot?v=1&t="))
        assertFalse("le prompt n'est pas lisible", link.contains("Help the person say what they mean"))
        assertTrue("le lien reste court", link.length < 4_000)
    }

    @Test
    fun `un modele sans identifiant en recoit un derive de son nom`() {
        val link = forged("""{"v":1,"n":"Scout Two","s":"# Scout"}""")

        val decoded = BotTemplateShareCodec.decode(link)

        assertEquals("scout_two", decoded.spec.id)
    }

    @Test
    fun `un modele sans nom est refuse plutot que partage`() {
        val failure = assertThrows(BotTemplateShareException::class.java) {
            BotTemplateShareCodec.encode(BotTemplateShare.of(BotTemplateSpec.blank))
        }

        assertEquals(BotTemplateShareError.INVALID_FIELD, failure.reason)
        assertEquals("name", failure.detail)
    }

    // MARK: - Le refus des secrets

    @Test
    fun `une cle api dans la charge est refusee`() {
        val failure = rejected(forged("""{"v":1,"n":"Scout","apiKey":"abc123"}"""))

        assertEquals(BotTemplateShareError.SECRET, failure.reason)
        assertEquals("apiKey", failure.detail)
    }

    @Test
    fun `un jeton dans la charge est refuse`() {
        val failure = rejected(forged("""{"v":1,"n":"Scout","token":"abc123"}"""))

        assertEquals(BotTemplateShareError.SECRET, failure.reason)
        assertEquals("token", failure.detail)
    }

    @Test
    fun `un mot de passe dans la charge est refuse`() {
        val failure = rejected(forged("""{"v":1,"n":"Scout","a":"Mot de passe : hunter2"}"""))

        assertEquals(BotTemplateShareError.SECRET, failure.reason)
        assertTrue("le refus nomme le secret", failure.detail.isNotBlank())
    }

    @Test
    fun `une adresse de serveur dans la charge est refusee`() {
        val failure = rejected(forged("""{"v":1,"n":"Scout","s":"Monte sur http://192.168.1.10:8080"}"""))

        assertEquals(BotTemplateShareError.SECRET, failure.reason)
        assertTrue(failure.detail.startsWith("http://"))
    }

    @Test
    fun `une adresse laissee en clair dans le lien est refusee`() {
        val failure = rejected("onyx://bot?v=1&t=http://192.168.1.10:8080")

        assertEquals(BotTemplateShareError.SECRET, failure.reason)
        assertTrue(failure.detail.contains("http://"))
    }

    @Test
    fun `un champ serveur ajoute a la main est refuse`() {
        val failure = rejected(forged("""{"v":1,"n":"Scout","server":"monserveur.local"}"""))

        assertEquals(BotTemplateShareError.SECRET, failure.reason)
        assertEquals("server", failure.detail)
    }

    @Test
    fun `le codec n emet jamais un modele qui porte une adresse ou une cle`() {
        val withURL = BotTemplateSpec("fuite", "Fuite", "Rien.", "# Fuite\n\nVa sur https://exemple.test")
        val withKey = BotTemplateSpec("fuite", "Fuite", "Rien.", "# Fuite\n\nClé : sk-abcdefghijklmnopqrstuvwxyz")

        val address = assertThrows(BotTemplateShareException::class.java) {
            BotTemplateShareCodec.encode(BotTemplateShare.of(withURL))
        }
        val key = assertThrows(BotTemplateShareException::class.java) {
            BotTemplateShareCodec.encode(BotTemplateShare.of(withKey))
        }

        assertEquals(BotTemplateShareError.SECRET, address.reason)
        assertEquals(BotTemplateShareError.SECRET, key.reason)
    }

    @Test
    fun `un refus reste lisible en francais`() {
        val failure = rejected(forged("""{"v":1,"n":"Scout","apiKey":"abc123"}"""))

        val french = OnyxLocalization.format(failure.reason.key, failure.detail, locale = fr)

        assertTrue("la phrase française existe", french.contains("identifiant secret"))
        assertTrue("elle dit le refus", french.contains("refuse"))
    }

    // MARK: - Le refus d'une version inconnue

    @Test
    fun `une version de lien inconnue est refusee`() {
        val failure = rejected(forged("""{"v":1,"n":"Scout","s":"# Scout"}""", linkVersion = "2"))

        assertEquals(BotTemplateShareError.UNKNOWN_VERSION, failure.reason)
        assertEquals("2", failure.detail)
    }

    @Test
    fun `une version de charge inconnue est refusee meme si le lien dit 1`() {
        val failure = rejected(forged("""{"v":2,"n":"Scout","s":"# Scout"}""", linkVersion = "1"))

        assertEquals(BotTemplateShareError.UNKNOWN_VERSION, failure.reason)
        assertEquals("2", failure.detail)
    }

    @Test
    fun `une charge sans version est refusee`() {
        val failure = rejected(forged("""{"n":"Scout","s":"# Scout"}"""))

        assertEquals(BotTemplateShareError.UNKNOWN_VERSION, failure.reason)
        assertEquals("none", failure.detail)
    }

    @Test
    fun `un lien sans version declaree est refuse`() {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"v":1,"n":"Scout"}""".toByteArray(Charsets.UTF_8))

        val failure = rejected("onyx://bot?t=$payload")

        assertEquals(BotTemplateShareError.UNKNOWN_VERSION, failure.reason)
    }

    // MARK: - Les liens abîmés ou étrangers

    @Test
    fun `un texte quelconque n est pas un lien de modele`() {
        assertEquals(BotTemplateShareError.NOT_A_TEMPLATE, rejected("bonjour").reason)
        assertEquals(BotTemplateShareError.NOT_A_TEMPLATE, rejected("https://onyx.app/bot").reason)
        assertEquals(BotTemplateShareError.NOT_A_TEMPLATE, rejected("onyx://connect?v=1").reason)
        assertEquals(BotTemplateShareError.NOT_A_TEMPLATE, rejected("").reason)
    }

    @Test
    fun `une charge abimee est refusee sans planter`() {
        assertEquals(BotTemplateShareError.CORRUPT, rejected("onyx://bot?v=1&t=!!!").reason)
        assertEquals(BotTemplateShareError.CORRUPT, rejected("onyx://bot?v=1&t=abc").reason)
        assertEquals(
            BotTemplateShareError.CORRUPT,
            rejected(forged("""{"v":1,"n":""")).reason,
        )
    }

    @Test
    fun `un champ inconnu est refuse plutot qu ignore`() {
        val failure = rejected(forged("""{"v":1,"n":"Scout","z":"quelque chose"}"""))

        assertEquals(BotTemplateShareError.UNKNOWN_FIELD, failure.reason)
        assertEquals("z", failure.detail)
    }

    // MARK: - L'aperçu

    @Test
    fun `l apercu nomme ce que le destinataire verra`() {
        val share = BotTemplateShare(
            spec = BotTemplateSpec.all.first { it.id == "home" },
            tools = listOf("homeassistant"),
            skills = listOf("routines"),
            model = "hermes-3",
        )

        val preview = share.preview()

        assertEquals("Home", preview.name)
        assertEquals("Looks after the house: lights, heating, sensors and routines.", preview.role)
        assertEquals(listOf("homeassistant"), preview.tools)
        assertEquals(listOf("routines"), preview.skills)
        assertEquals("hermes-3", preview.model)
        assertEquals(share.spec.face, preview.face)
        assertTrue("le visage se décrit", preview.faceLabel(fr)!!.startsWith("Visage"))
    }

    @Test
    fun `l apercu nomme honnetement ce qui n est pas dans le lien`() {
        val preview = BotTemplateShare.of(BotTemplateSpec.all.first { it.id == "planner" }).preview()

        assertEquals(
            "les six absences, toutes",
            listOf(
                BotTemplateOmission.CREDENTIALS,
                BotTemplateOmission.CONVERSATIONS,
                BotTemplateOmission.ROUTINES,
                BotTemplateOmission.VOICE,
                BotTemplateOmission.VISIBILITY,
                BotTemplateOmission.FILES,
            ),
            preview.omissions,
        )

        val french = preview.omissionLabels(fr)
        assertEquals(6, french.size)
        assertTrue("la clé et le serveur sont nommés", french[0].contains("clé API") && french[0].contains("adresse du serveur"))
        assertTrue("les conversations sont nommées", french[1].contains("conversations"))
        assertTrue("les routines sont nommées", french[2].contains("routines"))
        assertTrue("la voix est nommée", french[3].contains("voix"))
        assertTrue("la visibilité est nommée", french[4].contains("qui peut voir"))
        assertTrue("les fichiers sont nommés", french[5].contains("fichiers"))
        french.forEach { line -> assertTrue("chaque absence est annoncée : $line", line.startsWith("Pas dans le lien")) }
    }

    @Test
    fun `un role vide est dit au lieu d etre laisse croire`() {
        val preview = BotTemplatePreview.from(
            BotTemplateSpec("muet", "Muet", "", "# Muet\n\nRien."),
        )

        assertEquals(OnyxLocalization.translate(BotTemplatePreview.NO_ROLE, fr), preview.role)
        assertEquals("Aucun rôle indiqué.", preview.role)
    }

    @Test
    fun `un apercu ne pretend pas porter ce qu il ne porte pas`() {
        val preview = BotTemplateShare.of(BotTemplateSpec.all.first { it.id == "coder" }).preview()

        val omissions = preview.omissionLabels(fr).joinToString(" ").lowercase(fr)
        // Ce qui est vraiment dans le lien ne figure pas parmi les absences.
        assertFalse(omissions.contains("prompt"))
        assertFalse(omissions.contains("visage"))
        assertFalse(omissions.contains("outils"))
        assertFalse(omissions.contains("compétences"))
        assertFalse(omissions.contains("modèle par défaut"))
    }
}
