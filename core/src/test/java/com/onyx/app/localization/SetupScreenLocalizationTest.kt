package com.onyx.app.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * L'écran « Configurer Onyx » et sa feuille Scanner, **entièrement en français** quand la langue
 * est « Système » sur un téléphone français.
 *
 * Le défaut d'origine : le paragraphe d'aide du lot « tout en un » n'était pas passé par le
 * dictionnaire (il restait en anglais) et le titre de section était le verbe à l'infinitif
 * « Connecter » au lieu de la question de la référence.
 */
class SetupScreenLocalizationTest {
    private val fr = Locale.FRANCE

    private fun assertFrench(cases: Map<String, String>) {
        cases.forEach { (english, french) ->
            assertEquals("français : $english", french, OnyxLocalization.translate(english, fr))
            assertEquals("anglais : $english", english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
    }

    @Test
    fun `l ecran de configuration parle entierement francais`() {
        assertFrench(linkedMapOf(
            "Set up Onyx" to "Configurer Onyx",
            "Skip" to "Ignorer",
            "Done" to "Terminé",
            "Welcome to Onyx" to "Bienvenue dans Onyx",
            "Talk to your own AI agent by voice or chat." to "Parlez à votre propre agent IA par voix ou par chat.",
            "Have a setup code?" to "Vous avez un code de configuration ?",
            "One link sets up everything at once: server, key, model, voices, the Computer bridge and the language. Make one on a device that is already set up: Settings › Connection › Set up another device." to "Un seul lien configure tout d’un coup : serveur, clé, modèle, voix, le pont Ordinateur et la langue. Créez-en un sur un appareil déjà configuré : Réglages › Connexion › Configurer un autre appareil.",
            "Scan a setup code" to "Scanner un code de configuration",
            "Paste a setup link" to "Coller un lien de configuration",
            "That isn't a Onyx setup link." to "Ce n’est pas un lien de configuration Onyx.",
            "Your own server" to "Votre propre serveur",
            "Works with Hermes, the open-source agent you run on your own machine, or any OpenAI-compatible model server." to "Fonctionne avec Hermes, l’agent open source que vous exécutez sur votre propre machine, ou tout serveur de modèle compatible OpenAI.",
            "Private by design" to "Confidentialité dès la conception",
            "Your conversations go only to the server you enter below." to "Vos conversations ne vont qu’au serveur saisi ci-dessous.",
            "Speech stays on device" to "La parole reste sur l’appareil",
            "Your voice is recognized on this device, not in the cloud." to "Votre voix est reconnue sur cet appareil, pas dans le cloud.",
            "How does Onyx reach it?" to "Comment Onyx s’y connecte-t-il ?",
            "Advanced" to "Avancé",
            "Address and key. Onyx finds the rest." to "Adresse et clé. Onyx trouve le reste.",
            "Choose the transport and fill everything by hand." to "Choisissez le transport et remplissez tout à la main.",
            "Address" to "Adresse",
            "API key" to "Clé API",
            "None" to "Aucun",
            "Finding the rest…" to "Recherche du reste…",
            "Model" to "Modèle",
            "Voices" to "Voix",
            "Computer" to "Ordinateur",
            "Language" to "Langue",
            "Find the rest" to "Trouver le reste",
            "Hermes Dashboard (WebSocket)" to "Dashboard Hermes (WebSocket)",
            "Hermes API (shared sessions)" to "API Hermes (sessions partagées)",
            "OpenAI-compatible (direct to a model)" to "Compatible OpenAI (directement vers un modèle)",
            "Hermes API server" to "Serveur API Hermes",
            "The gateway's API server and its API_SERVER_KEY. Sessions from every platform appear in Onyx." to "Le serveur API de la passerelle et sa API_SERVER_KEY. Les sessions de toutes les plateformes apparaissent dans Onyx.",
            "Hermes Dashboard" to "Dashboard Hermes",
            "The same login as the Hermes Dashboard: its username and password, or Sign in with a browser where it uses Google or another provider. Adds tool approvals and slash commands." to "Les mêmes identifiants que le Dashboard Hermes : nom d’utilisateur et mot de passe, ou une connexion par navigateur là où il utilise Google ou un autre fournisseur. Ajoute les approbations d’outils et les commandes slash.",
            "OpenAI-compatible endpoint" to "Point de terminaison compatible OpenAI",
            "Straight to a model with no agent in between: llama.cpp, llama-swap, vLLM, Ollama, or a hosted provider." to "Directement vers un modèle, sans agent intermédiaire : llama.cpp, llama-swap, vLLM, Ollama ou un fournisseur hébergé.",
            "Username" to "Identifiant",
            "Password" to "Mot de passe",
            "Dashboard username" to "Identifiant du Dashboard",
            "Dashboard password" to "Mot de passe du Dashboard",
            "Model name" to "Nom du modèle",
            "Testing…" to "Test en cours…",
            "Test connection" to "Tester la connexion",
            "Paste" to "Coller",
            "Set the transport by hand. Simple fills these in from the server." to "Réglez le transport à la main. Simple les remplit depuis le serveur.",
            "Enter the server's address and its key. Onyx finds the model, the voices, the Computer bridge and the language on its own — leave anything it doesn't announce empty." to "Saisissez l’adresse du serveur et sa clé. Onyx trouve seul le modèle, les voix, le pont Ordinateur et la langue — laissez vide ce qu’il n’annonce pas.",
            "Nothing answered at that address. Check it, or add the connection by hand under Advanced." to "Rien n’a répondu à cette adresse. Vérifiez-la, ou ajoutez la connexion à la main dans Avancé.",
            "token included" to "jeton inclus",
        ))
    }

    @Test
    fun `la feuille Scanner parle entierement francais`() {
        assertFrench(linkedMapOf(
            "Point the camera at a Onyx setup code." to "Pointez la caméra vers un code de configuration Onyx.",
            "That isn't a Onyx setup code." to "Ce n’est pas un code de configuration Onyx.",
            "No camera access" to "Accès à la caméra refusé",
            "Allow the camera for Onyx in Settings to scan a setup code, or paste the setup link instead." to "Autorisez la caméra pour Onyx dans les réglages afin de scanner un code de configuration, ou collez plutôt le lien.",
            "Open Settings" to "Ouvrir les réglages",
            "Camera unavailable" to "Caméra indisponible",
            "This device has no camera available for scanning. Paste the setup link instead." to "Aucune caméra ne permet de scanner sur cet appareil. Collez plutôt le lien de configuration.",
            "Onyx couldn't start the camera. Close other camera apps and try again." to "Onyx n’a pas pu démarrer la caméra. Fermez les autres apps qui l’utilisent et réessayez.",
            "Try Again" to "Réessayer",
            "Cancel" to "Annuler",
            "Paste the link" to "Coller le lien",
            "I can't see a code yet — bring it closer, and avoid the glare." to "Je ne vois encore aucun code — approchez-le, et évitez le reflet.",
            "A code is in view, but I can't read it — move closer, and avoid the glare." to "Un code est bien dans le champ, mais je n’arrive pas à le lire — approchez-vous, et évitez le reflet.",
            "Paste the link instead." to "Collez plutôt le lien.",
        ))
    }

    @Test
    fun `la confirmation du code parle entierement francais`() {
        assertFrench(linkedMapOf(
            "Setup code" to "Code de configuration",
            "Server" to "Serveur",
            "Name" to "Nom",
            "Hermes API" to "API Hermes",
            "Profile" to "Profil",
            "Custom header" to "En-tête personnalisé",
            "Custom headers" to "En-têtes personnalisés",
            "Also set up" to "Également configuré",
            "Voice server" to "Serveur de voix",
            "Kokoro-compatible" to "Compatible Kokoro",
            "no token" to "aucun jeton",
            "Computer bridge" to "Pont ordinateur",
            "Language" to "Langue",
            "Add server" to "Ajouter le serveur",
            "Set up" to "Configurer",
            "Testing the connection…" to "Test de la connexion…",
            "Test again" to "Tester à nouveau",
            "Edit connection…" to "Modifier la connexion…",
            "Remove this server" to "Supprimer ce serveur",
            "Onyx will use this" to "Onyx utilisera cette connexion",
            "no key" to "aucune clé",
            "no password" to "aucun mot de passe",
            "key included" to "clé incluse",
            "password included" to "mot de passe inclus",
            "Client ID only" to "ID client uniquement",
            "Service token included" to "jeton de service inclus",
        ))
    }

    @Test
    fun `l anatomie de la mise en route parle francais`() {
        // Les libellés neufs du lot « mise en route sur l'anatomie de la référence » : la phrase
        // qui rassure, le champ, son aide, les trois cas d'adressage, la protection d'accès et ses
        // deux repliables. La clé reste l'anglais ; le français vient d'ici.
        assertFrench(linkedMapOf(
            "Connect to your Hermes gateway. Nothing is sent until you connect." to "Connectez-vous à votre passerelle Hermes. Rien n’est envoyé tant que vous ne vous êtes pas connecté.",
            "Gateway address" to "Adresse de la passerelle",
            "Where your dashboard runs: LAN, Tailscale or HTTPS." to "Où tourne votre tableau de bord : LAN, Tailscale ou HTTPS.",
            "Which address do I use?" to "Quelle adresse utiliser ?",
            "Same Wi-Fi / local network" to "Même Wi-Fi / réseau local",
            "The address of the machine that runs the dashboard, on your own network. Start the dashboard with --host 0.0.0.0 so it answers on the network, and not only on the machine itself." to "L’adresse de la machine qui fait tourner le tableau de bord, sur votre réseau. Lancez le tableau de bord avec --host 0.0.0.0 pour qu’il réponde sur le réseau, et pas seulement sur la machine elle-même.",
            "Tailscale" to "Tailscale",
            "A tailnet address (100.64.0.0/10), or a machine name such as machine.tailnet-name.ts.net. It works from anywhere, as long as Tailscale is up on both ends." to "Une adresse de tailnet (100.64.0.0/10), ou un nom de machine comme machine.tailnet-name.ts.net. Elle fonctionne de partout, tant que Tailscale est actif des deux côtés.",
            "HTTPS" to "HTTPS",
            "Behind a reverse proxy. The proxy has to let the WebSockets through on /api/ws — without that, the reply is written but never streams." to "Derrière un proxy inverse. Le proxy doit laisser passer les WebSocket sur /api/ws — sans cela, la réponse s’écrit mais n’arrive jamais en flux.",
            "Behind Cloudflare Access?" to "Derrière Cloudflare Access ?",
            "If the dashboard sits behind Cloudflare Access instead of a tailnet, make a service token in Zero Trust. The pair is sent as CF-Access-Client-Id / -Secret on every request and WebSocket." to "Si le tableau de bord est derrière Cloudflare Access plutôt que sur un tailnet, créez un jeton de service dans Zero Trust. Le couple part en CF-Access-Client-Id / -Secret à chaque requête et chaque WebSocket.",
            "Stored encrypted on this phone, and sent only to this address." to "Stocké chiffré sur ce téléphone, et envoyé uniquement à cette adresse.",
            "Connect another way" to "Connecter autrement",
            "What else is on the server?" to "Quoi d’autre sur le serveur ?",
            "What does Onyx do?" to "Que fait Onyx ?",
            "Test" to "Tester",
            "Connected" to "Connecté",
            "Failed" to "Échec",
            "Client ID" to "ID client",
            "Client secret" to "Secret client",
        ))
    }

    @Test
    fun `le titre de section est la question de la reference pas un infinitif`() {
        // La référence pose la question ; l'écran ne dit plus « Connecter ».
        assertEquals("Comment Onyx s’y connecte-t-il ?", OnyxLocalization.translate("How does Onyx reach it?", fr))
        assertNotEquals("Connecter", OnyxLocalization.translate("How does Onyx reach it?", fr))
        assertEquals("Connecter", OnyxLocalization.translate("Connect", fr))
    }

    @Test
    fun `le paragraphe du lien tout en un est traduit par le dictionnaire`() {
        val english = "One link sets up everything at once: server, key, model, voices, the Computer bridge and the language. Make one on a device that is already set up: Settings › Connection › Set up another device."
        val french = OnyxLocalization.translate(english, fr)
        assertNotEquals("le paragraphe reste en anglais", english, french)
        assertEquals(
            "Un seul lien configure tout d’un coup : serveur, clé, modèle, voix, le pont Ordinateur et la langue. Créez-en un sur un appareil déjà configuré : Réglages › Connexion › Configurer un autre appareil.",
            french,
        )
        // Un passage en anglais le rend bien : c'est la clé anglaise, pas une chaîne en dur.
        assertEquals(english, OnyxLocalization.translate(english, Locale.ENGLISH))
    }
}
