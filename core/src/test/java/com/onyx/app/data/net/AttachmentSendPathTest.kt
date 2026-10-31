package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le chemin d'envoi d'une pièce jointe vers le serveur.
 *
 * Le défaut : `POST api/files/upload-stream` portait le **dossier** dans le champ `path`, alors que
 * la route y lit le **fichier cible** (`_managed_write_target(path)`). Le serveur répondait
 * `409 A directory already exists at that path` ; la pièce jointe n'arrivait jamais, et le fil
 * montrait une pastille vers un fichier inexistant.
 *
 * Ces tests tiennent la forme du corps : la route, le champ `path` (dossier **et** nom),
 * `overwrite`, et le nom de la partie `file` — comme le client web du Dashboard
 * (`joinPath(dossier, nom)`) et `ServerFiles.upload` de la référence (`join(folder, name)`).
 */
class AttachmentSendPathTest {

    @Test
    fun `le champ path porte le fichier, pas le dossier`() = runBlocking {
        val seen = Recording()
        val api = filesApi { request ->
            seen.record(request)
            request.respond(200, """{"ok":true,"path":"/home/john/Onyx attachments/x.jpg"}""")
        }

        api.upload(
            destinationFolder = "/home/john/Onyx attachments",
            name = "2026-10-27 121000 image.jpg",
            bytes = byteArrayOf(1, 2, 3),
            overwrite = false,
        )

        assertEquals("/api/files/upload-stream", seen.route)
        assertTrue("le corps doit être un multipart", seen.contentType.startsWith("multipart/form-data"))
        assertTrue(
            "path nomme le fichier cible, pas le dossier (sinon le serveur répond 409)",
            seen.body.contains("\r\n\r\n/home/john/Onyx attachments/2026-10-27 121000 image.jpg\r\n"),
        )
        assertFalse(
            "le dossier seul est le défaut d'origine",
            seen.body.contains("\r\n\r\n/home/john/Onyx attachments\r\n"),
        )
        assertTrue(
            "overwrite part avec le corps",
            seen.body.contains("name=\"overwrite\"") && seen.body.contains("\r\n\r\nfalse\r\n"),
        )
        assertTrue(
            "la partie file porte le nom du fichier",
            seen.body.contains("name=\"file\"; filename=\"2026-10-27 121000 image.jpg\""),
        )
    }

    @Test
    fun `un nom non latin passe tel quel dans path et dans le nom du fichier`() = runBlocking {
        val seen = Recording()
        val api = filesApi { request ->
            seen.record(request)
            request.respond(200, """{"ok":true,"path":"/home/john/Onyx attachments/x.jpg"}""")
        }

        api.upload(
            destinationFolder = "/home/john/Onyx attachments",
            name = "2026-10-27 121000 café 日本.jpg",
            bytes = byteArrayOf(9),
        )

        assertTrue(
            "le chemin cible garde le nom entier",
            seen.body.contains("/home/john/Onyx attachments/2026-10-27 121000 café 日本.jpg"),
        )
        assertTrue("le nom de la partie suit", seen.body.contains("name=\"file\"; filename="))
    }

    @Test
    fun `place depose dans Onyx attachments sous un nom date, au chemin complet`() = runBlocking {
        val uploads = mutableListOf<String>()
        val api = filesApi { request ->
            when {
                request.url.encodedPath.endsWith("/api/files") && request.method == "GET" ->
                    request.respond(200, """{"path":"/home/john","parent":"/home","entries":[]}""")

                request.url.encodedPath.endsWith("/api/files/mkdir") ->
                    request.respond(200, """{"ok":true,"entry":{"name":"Onyx attachments","path":"/home/john/Onyx attachments","is_directory":true}}""")

                request.url.encodedPath.endsWith("/api/files/upload-stream") -> {
                    uploads += request.bodyText()
                    request.respond(200, """{"ok":true,"path":"/home/john/Onyx attachments/x.jpg"}""")
                }

                else -> request.respond(200, "{}")
            }
        }

        val placed = ServerAttachments(api).place(
            listOf(ServerAttachment(name = "image.jpg", bytes = byteArrayOf(1, 2), isImage = true)),
        )

        assertEquals(1, uploads.size)
        assertTrue(
            "le corps de l'envoi vise le fichier complet",
            uploads.single().contains("/home/john/Onyx attachments/") &&
                uploads.single().contains(" image.jpg\r\n"),
        )
        assertEquals(1, placed.size)
        assertTrue(
            "le chemin rendu est celui du fichier déposé",
            placed.single().path.startsWith("/home/john/Onyx attachments/") && placed.single().path.endsWith(" image.jpg"),
        )
    }

    @Test
    fun `un 409 fait rejouer sous un numero de copie, et l envoi vise encore le fichier`() = runBlocking {
        val uploads = mutableListOf<String>()
        val api = filesApi { request ->
            when {
                request.url.encodedPath.endsWith("/api/files") && request.method == "GET" ->
                    request.respond(200, """{"path":"/home/john"}""")

                request.url.encodedPath.endsWith("/api/files/mkdir") ->
                    request.respond(200, "{}")

                request.url.encodedPath.endsWith("/api/files/upload-stream") -> {
                    uploads += request.bodyText()
                    if (uploads.size == 1) request.respond(409, """{"detail":"File already exists"}""")
                    else request.respond(200, """{"ok":true}""")
                }

                else -> request.respond(200, "{}")
            }
        }

        ServerAttachments(api).place(
            listOf(ServerAttachment(name = "photo.png", bytes = byteArrayOf(4), isImage = true)),
        )

        assertEquals(2, uploads.size)
        assertTrue("le premier essai vise le fichier", uploads[0].contains(" photo.png\r\n"))
        assertTrue(
            "le second vise le fichier daté numéroté",
            uploads[1].contains(" 2 photo.png\r\n"),
        )
    }
}

/** Ce qui a été vu passer sur le fil, pour l'assertion. */
private class Recording {
    var route: String = ""
    var contentType: String = ""
    var body: String = ""

    fun record(request: Request) {
        route = request.url.encodedPath
        contentType = request.body?.contentType().toString()
        body = request.bodyText()
    }
}

private fun Request.bodyText(): String =
    body?.let { payload -> Buffer().also { payload.writeTo(it) }.readUtf8() } ?: ""

private fun filesApi(respond: (Request) -> Response): ServeFilesApi {
    val http = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        if (request.url.encodedPath.endsWith("/auth/password-login")) {
            request.respond(200, """{"ok":true}""")
        } else {
            respond(request)
        }
    }.build()
    val config = ServerConfig(
        id = "test",
        serveBaseUrl = "https://hermes.test",
        serveUsername = "user",
        servePassword = "x",
    )
    return ServeFilesApi(ServeClient(config, http))
}

private fun Request.respond(code: Int, body: String): Response = Response.Builder()
    .request(this)
    .protocol(Protocol.HTTP_1_1)
    .code(code)
    .message("test")
    .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
    .build()
