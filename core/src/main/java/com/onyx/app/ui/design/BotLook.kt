package com.onyx.app.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * Le visage d'un bot, quelle que soit la façon dont il a été choisi.
 *
 * Un visage dessiné (forme, yeux, bouche, marque, couleur), explicite ou déterminé par le nom.
 * Seules les anciennes valeurs numériques utilisent encore une orbe historique.
 */
@Composable
fun BotLook(
    name: String,
    faceCode: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    mood: BotMood = BotMood.IDLE,
    showsInitial: Boolean = true,
    /** La barre du haut veut un visage **à plat et immobile**: `false` fige le clignement. */
    blinks: Boolean = true,
    /** Un instant du visage qui bouge (la scène vocale); `null`: le visage ordinaire. */
    pose: BotPose? = null,
) {
    // Comme `BotAvatar(face:)`: sans visage enregistré, le nom du bot
    // détermine une créature dessinée. Seules les anciennes valeurs purement
    // numériques conservent le fallback d'orbe historique.
    if (faceCode?.toIntOrNull() != null) {
        OrbAvatar(name = name, size = size, override = faceCode.toIntOrNull(), modifier = modifier)
    } else {
        BotAvatar(
            name = name,
            size = size,
            faceCode = faceCode,
            modifier = modifier,
            mood = mood,
            showsInitial = showsInitial,
            blinks = blinks,
            pose = pose,
        )
    }
}

/**
 * La couleur d'un bot, telle qu'elle se voit: la plus profonde de son dégradé.
 *
 * C'est l'aura de la scène vocale — la lumière autour du grand visage qui parle — et elle est
 * **celle du bot**, comme sur le modèle, et non celle de la phase.
 */
fun botFaceColour(name: String, faceCode: String?, theme: ResolvedTheme): Color {
    val face = botFace(name, faceCode)
    return BotFace.gradient(face.colour, theme).second
}

/**
 * La couleur de l'**aura** d'un bot, pour la page où elle se pose.
 *
 * L'aura est une lumière ajoutée à la page: c'est le **clair** du dégradé qui éclaire une page
 * sombre (une couleur profonde n'y fait qu'un halo éteint, à peine plus clair que le noir), et le
 * **profond** qui tient sur une page claire, où le clair se délaie en blanc bleuté. Une seule
 * teinte pour les deux pages donne donc, dans l'un des deux thèmes, l'orbe délavé que le
 * propriétaire signale; la teinte suit la page, jamais le bot.
 */
fun botFaceAuraColour(name: String, faceCode: String?, theme: ResolvedTheme): Color {
    val face = botFace(name, faceCode)
    val (light, deep) = BotFace.gradient(face.colour, theme)
    return if (theme.dark) light else deep
}

/** Le visage qu'un nom (ou le code enregistré) donne: ce que dessine [BotLook]. */
fun botFace(name: String, faceCode: String?): BotFace =
    BotFace.parse(faceCode) ?: BotFace.forName(name)
