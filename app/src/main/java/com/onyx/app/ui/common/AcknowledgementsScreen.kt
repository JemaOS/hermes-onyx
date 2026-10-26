package com.onyx.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * D'où vient une ligne de remerciements, et donc comment la vérifier.
 *
 * La provenance n'est pas décorative: `AcknowledgementsPageTest` la relit dans le dépôt, ce qui
 * interdit de créditer un projet qui ne figure ni dans les dépendances ni dans les ressources
 * livrées — une page de remerciements fausse est pire qu'aucune page.
 */
sealed interface Provenance {
    /** Une dépendance déclarée dans `gradle/libs.versions.toml` et référencée par un module. */
    data class Dependency(val alias: String, val coordinate: String) : Provenance

    /** Une ressource livrée dans les `assets` du module `core`. */
    data class Asset(val path: String) : Provenance

    /** Du code de ce dépôt: il n'y a personne d'autre à créditer. */
    data class Source(val path: String) : Provenance

    /** Un service du poste; sa licence a été lue à la source ([licenceSource]). */
    data class Desktop(val licenceSource: String) : Provenance
}

/** Où tourne le composant: sur ce téléphone, ou sur le poste qui l'alimente. */
enum class Where { PHONE, COMPUTER }

/** Une ligne de remerciements: le nom technique (jamais traduit) et ce qu'il faut créditer. */
data class Acknowledgement(
    val name: String,
    val where: Where,
    val credit: String,
    val provenance: Provenance,
)

/**
 * La phrase d'introduction: ce que l'app utilise vraiment pour les mathématiques, les diagrammes
 * et le terminal. Aucune bibliothèque d'une autre plateforme n'y figure.
 */
const val ACKNOWLEDGEMENTS_INTRO: String =
    "Onyx draws math with KaTeX and diagrams with mermaid, both embedded in the app and used offline; its terminal is written here and speaks SSH with sshj. The rest comes from Android's libraries and from the services running on your own PC."

/**
 * Ce que l'app embarque et ce qui tourne sur le poste, chacun avec sa licence et son attribution.
 *
 * Les trois moteurs que la phrase d'introduction nomme sont ici: KaTeX (mathématiques), Mermaid
 * (diagrammes) et le terminal — ce dernier n'est **pas** un composant tiers, et la ligne le dit.
 */
val ONYX_ACKNOWLEDGEMENTS: List<Acknowledgement> = listOf(
    Acknowledgement(
        name = "Jetpack Compose",
        where = Where.PHONE,
        credit = "Every screen is drawn with Compose — Apache License 2.0, © The Android Open Source Project.",
        provenance = Provenance.Dependency("androidx-ui", "androidx.compose.ui:ui"),
    ),
    Acknowledgement(
        name = "AndroidX",
        where = Where.PHONE,
        credit = "AndroidX and Jetpack bring the platform pieces the app relies on (lifecycle, activity, DataStore, CameraX, Biometric, Glance) — Apache License 2.0, © The Android Open Source Project.",
        provenance = Provenance.Dependency("androidx-core-ktx", "androidx.core:core-ktx"),
    ),
    Acknowledgement(
        name = "kotlinx.coroutines",
        where = Where.PHONE,
        credit = "Coroutines keep the screens responsive while the network works — Apache License 2.0, © JetBrains s.r.o. and contributors.",
        provenance = Provenance.Dependency("kotlinx-coroutines-android", "org.jetbrains.kotlinx:kotlinx-coroutines-android"),
    ),
    Acknowledgement(
        name = "kotlinx.serialization",
        where = Where.PHONE,
        credit = "Serialization reads the JSON the gateway returns — Apache License 2.0, © JetBrains s.r.o. and contributors.",
        provenance = Provenance.Dependency("kotlinx-serialization-json", "org.jetbrains.kotlinx:kotlinx-serialization-json"),
    ),
    Acknowledgement(
        name = "OkHttp",
        where = Where.PHONE,
        credit = "OkHttp carries every request to your server and fetches the map tiles — Apache License 2.0, © Square, Inc.",
        provenance = Provenance.Dependency("okhttp", "com.squareup.okhttp3:okhttp"),
    ),
    Acknowledgement(
        name = "Markwon",
        where = Where.PHONE,
        credit = "Markwon renders the markdown of replies — Apache License 2.0, © Dimitry Ivanov (noties).",
        provenance = Provenance.Dependency("markwon-core", "io.noties.markwon:core"),
    ),
    Acknowledgement(
        name = "sshj",
        where = Where.PHONE,
        credit = "sshj opens the SSH sessions of the terminal — Apache License 2.0, © Jeroen van Erp and contributors.",
        provenance = Provenance.Dependency("sshj", "com.hierynomus:sshj"),
    ),
    Acknowledgement(
        name = "EdDSA-Java",
        where = Where.PHONE,
        credit = "EdDSA-Java makes the Ed25519 keys this phone presents to your servers — CC0 1.0 Universal, © str4d and contributors.",
        provenance = Provenance.Dependency("eddsa", "net.i2p.crypto:eddsa"),
    ),
    Acknowledgement(
        name = "ZXing",
        where = Where.PHONE,
        credit = "ZXing reads the QR codes that set the app up — Apache License 2.0, © the ZXing authors and JourneyApps.",
        provenance = Provenance.Dependency("zxing-embedded", "com.journeyapps:zxing-android-embedded"),
    ),
    Acknowledgement(
        name = "KaTeX",
        where = Where.PHONE,
        credit = "KaTeX renders mathematics offline, from the copy shipped in the app — MIT License, © 2013-2020 Khan Academy and other contributors.",
        provenance = Provenance.Asset("rich/katex.min.js"),
    ),
    Acknowledgement(
        name = "KaTeX fonts",
        where = Where.PHONE,
        credit = "The mathematical fonts come with KaTeX, derived from Computer Modern — SIL Open Font License 1.1, © 2013-2020 Khan Academy and other contributors.",
        provenance = Provenance.Asset("rich/fonts/KaTeX_Main-Regular.woff2"),
    ),
    Acknowledgement(
        name = "Mermaid",
        where = Where.PHONE,
        credit = "Mermaid draws the diagrams offline, from the copy shipped in the app — MIT License, © 2014-2022 Knut Sveidqvist.",
        provenance = Provenance.Asset("rich/mermaid.min.js"),
    ),
    Acknowledgement(
        name = "Inter",
        where = Where.PHONE,
        credit = "Inter is the text face of the app — SIL Open Font License 1.1, © 2016 The Inter Project Authors.",
        provenance = Provenance.Asset("fonts/Inter-Regular.ttf"),
    ),
    Acknowledgement(
        name = "Source Code Pro",
        where = Where.PHONE,
        credit = "Source Code Pro sets the code and the terminal — SIL Open Font License 1.1, © 2023 Adobe, with Reserved Font Name 'Source'.",
        provenance = Provenance.Asset("fonts/SourceCodePro-Medium.ttf"),
    ),
    Acknowledgement(
        name = "OpenStreetMap",
        where = Where.PHONE,
        credit = "The maps of replies are drawn from OpenStreetMap tiles, with the credit © OpenStreetMap kept under every map — Open Data Commons Open Database License 1.0, © OpenStreetMap contributors.",
        provenance = Provenance.Source("core/src/main/java/com/onyx/app/ui/cards/OsmTiles.kt"),
    ),
    Acknowledgement(
        name = "Terminal",
        where = Where.PHONE,
        credit = "The terminal is not a third-party component: its screen, its buffer and its SSH session are written in this app, on top of the sshj library above — no other project to credit.",
        provenance = Provenance.Source("app/src/main/java/com/onyx/app/ui/terminal/TerminalBuffer.kt"),
    ),
    Acknowledgement(
        name = "Hermes",
        where = Where.COMPUTER,
        credit = "Hermes, your own server, is the one that answers — MIT License, © 2025 Nous Research.",
        provenance = Provenance.Desktop("https://github.com/NousResearch/hermes-agent"),
    ),
    Acknowledgement(
        name = "Kokoro",
        where = Where.COMPUTER,
        credit = "Kokoro gives the app its voices: the model weights are Apache License 2.0 (© hexgrad), served by Kokoro-FastAPI, also Apache License 2.0 (© remsky).",
        provenance = Provenance.Desktop("https://huggingface.co/hexgrad/Kokoro-82M"),
    ),
    Acknowledgement(
        name = "Piper",
        where = Where.COMPUTER,
        credit = "Piper adds French voices behind the same address — GNU General Public License 3.0 or later, © the Piper authors (piper-tts 1.8.0).",
        provenance = Provenance.Desktop("https://github.com/OHF-Voice/piper1-gpl"),
    ),
    Acknowledgement(
        name = "faster-whisper",
        where = Where.COMPUTER,
        credit = "faster-whisper turns recorded speech into text on the PC — MIT License, © 2023 SYSTRAN.",
        provenance = Provenance.Desktop("https://github.com/SYSTRAN/faster-whisper"),
    ),
    Acknowledgement(
        name = "FFmpeg",
        where = Where.COMPUTER,
        credit = "FFmpeg reads and converts the audio of recordings and videos. The build installed here is GPL (built with --enable-gpl, GNU General Public License 3); a build without those parts stays LGPL.",
        provenance = Provenance.Desktop("https://ffmpeg.org/legal.html"),
    ),
    Acknowledgement(
        name = "cloudflared",
        where = Where.COMPUTER,
        credit = "cloudflared carries the tunnels that reach the voices and the computer screen when you are away from the PC — Apache License 2.0, © Cloudflare, Inc.",
        provenance = Provenance.Desktop("https://github.com/cloudflare/cloudflared"),
    ),
)

/**
 * Les remerciements: la phrase d'introduction, puis une section par composant — son nom en
 * en-tête, sa licence dans le corps. Ce que l'app embarque d'un côté, ce que le poste fait
 * tourner de l'autre.
 */
@Composable
fun AcknowledgementsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Acknowledgements", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                    BasicText(
                        text = ACKNOWLEDGEMENTS_INTRO,
                        style = theme.footnote.copy(color = theme.secondaryText),
                    )
                }
            }

            AcknowledgementsGroup(title = "On this phone")
            ONYX_ACKNOWLEDGEMENTS.filter { it.where == Where.PHONE }.forEach { acknowledgementSection(it) }

            AcknowledgementsGroup(title = "On your PC")
            ONYX_ACKNOWLEDGEMENTS.filter { it.where == Where.COMPUTER }.forEach { acknowledgementSection(it) }
        }
    }
}

/** Le titre d'un groupe, du même ton que l'en-tête d'une section. */
@Composable
private fun AcknowledgementsGroup(title: String) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = OnyxLocalization[title].uppercase(),
        localize = false,
        style = theme.footnote.copy(color = theme.secondaryText),
        modifier = Modifier.padding(start = 20.dp, top = 26.dp),
    )
}

/** Une section: le nom du composant en en-tête (nom propre, jamais traduit), sa licence dessous. */
@Composable
private fun acknowledgementSection(entry: Acknowledgement) {
    val theme = LocalOnyxTheme.current
    OnyxFormSection(header = entry.name, localizeHeader = false) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
            BasicText(
                text = entry.credit,
                style = theme.footnote.copy(color = theme.textColor),
            )
        }
    }
}
