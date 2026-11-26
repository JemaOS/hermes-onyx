package com.onyx.app.data.model

/** Les fichiers nommés dans un message texte (`AttachedFileNote`), cachés à la lecture. */
object AttachedFileNote {
    private const val opening = "\n\n[The user attached a file, saved on your machine at "
    private const val pictureOpening = "\n\n[The user attached a picture, saved on your machine at "

    data class Split(val text: String, val paths: List<String>)

    fun note(path: String): String = "$opening$path — read it with your tools.]"
    fun picture(path: String): String = "$pictureOpening$path — look at it with your tools.]"

    fun strip(text: String): String = split(text).text
    fun paths(text: String): List<String> = split(text).paths

    /** Retire les notes terminales une par une, y compris la première sans ses deux retours. */
    fun split(text: String): Split {
        var remaining = text
        val paths = mutableListOf<String>()
        while (remaining.endsWith("]")) {
            data class Candidate(val noteStart: Int, val pathStart: Int)
            val candidates = buildList {
                listOf(opening, pictureOpening).forEach { marker ->
                    val index = remaining.lastIndexOf(marker)
                    if (index >= 0) add(Candidate(index, index + marker.length))
                    val first = marker.drop(2)
                    if (remaining.startsWith(first)) add(Candidate(0, first.length))
                }
            }
            val note = candidates.maxByOrNull { it.noteStart } ?: break
            val after = remaining.substring(note.pathStart)
            if (after.contains('\n')) break
            val end = after.lastIndexOf(" — ")
            if (end < 0) break
            paths.add(0, after.take(end))
            remaining = remaining.take(note.noteStart)
        }
        return Split(remaining, paths)
    }

    /** Le nom affiché dans une chip, sans la date ni le numéro de copie de l'upload. */
    fun sentName(path: String): String {
        val name = path.substringAfterLast('/')
        return name.replace(Regex("""^\d{4}-\d{2}-\d{2} \d{6}( \d)? """), "")
    }
}
