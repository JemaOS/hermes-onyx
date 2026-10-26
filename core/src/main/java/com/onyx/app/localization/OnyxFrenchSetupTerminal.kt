package com.onyx.app.localization

/** Libellés du scanner, de la confirmation et du terminal SSH. */
internal val onyxSetupTerminalFrench: Map<String, String> = linkedMapOf(
    // Scanner et collage.
    "Point the camera at a Onyx setup code." to "Pointez la caméra vers un code de configuration Onyx.",
    "That isn't a Onyx setup code." to "Ce n’est pas un code de configuration Onyx.",
    "No camera access" to "Accès à la caméra refusé",
    "Allow the camera for Onyx in Settings to scan a setup code, or paste the setup link instead." to "Autorisez la caméra pour Onyx dans les réglages afin de scanner un code de configuration, ou collez plutôt le lien.",
    "Open Settings" to "Ouvrir les réglages",
    "Camera unavailable" to "Caméra indisponible",
    "This device has no camera available for scanning. Paste the setup link instead." to "Aucune caméra ne permet de scanner sur cet appareil. Collez plutôt le lien de configuration.",
    "Onyx couldn't start the camera. Close other camera apps and try again." to "Onyx n’a pas pu démarrer la caméra. Fermez les autres apps qui l’utilisent et réessayez.",
    "Paste a setup link" to "Coller un lien de configuration",
    "That isn't a Onyx setup link." to "Ce n’est pas un lien de configuration Onyx.",

    // Lecture et confirmation du code.
    "Setup code" to "Code de configuration",
    "Couldn't save the setup code." to "Impossible d’enregistrer le code de configuration.",
    "This setup code was made by a newer version of Onyx. Update the app, then try again." to "Ce code de configuration vient d’une version plus récente de Onyx. Mettez l’app à jour, puis réessayez.",
    "This setup code has no server address in it." to "Ce code de configuration ne contient aucune adresse de serveur.",
    "This setup code is too long to be one of Onyx's." to "Ce code est trop long pour être un code de configuration Onyx.",
    "Testing the connection…" to "Test de la connexion…",
    "Add server" to "Ajouter le serveur",
    "Set up" to "Configurer",
    "Test again" to "Tester à nouveau",
    "Remove this server" to "Supprimer ce serveur",
    "Onyx will use this" to "Onyx utilisera cette connexion",
    "password included" to "mot de passe inclus",
    "no password" to "aucun mot de passe",
    "key included" to "clé incluse",
    "no key" to "aucune clé",
    "Client ID only" to "ID client uniquement",
    "Service token included" to "jeton de service inclus",
    "Custom header" to "En-tête personnalisé",
    "Custom headers" to "En-têtes personnalisés",
    "This is added as a new server and Onyx switches to it, which starts a new conversation. Your other servers stay as they are." to "Ce serveur sera ajouté et Onyx basculera dessus, ce qui démarrera une nouvelle conversation. Vos autres serveurs resteront inchangés.",
    "You already have a server at this address (%1\$s). This adds another." to "Vous avez déjà un serveur à cette adresse (%1\$s). Celui-ci sera ajouté en plus.",
    "This sets up your server's connection." to "Ceci configurera la connexion de votre serveur.",
    "The endpoint replaces the one you have now (%1\$s), and that one's key is forgotten." to "Le point de terminaison remplacera l’actuel (%1\$s), dont la clé sera oubliée.",
    "Only use a code from someone you trust: what you type and say is sent to these addresses." to "N’utilisez un code que s’il vient d’une personne de confiance : ce que vous écrivez et dites sera envoyé à ces adresses.",
    "The code is saved. If the address is only reachable on your home network or VPN, connect to that and test again." to "Le code est enregistré. Si l’adresse n’est accessible que sur votre réseau domestique ou VPN, connectez-vous-y et testez à nouveau.",
    "The code is saved, but it came without a password or key. " to "Le code est enregistré, mais il ne contient ni mot de passe ni clé. ",
    "Add it under Edit connection." to "Ajoutez-le dans Modifier la connexion.",
    "Tap Done and add it in the form." to "Touchez Terminé et ajoutez-le dans le formulaire.",

    // Terminal, compte et clés.
    "Reconnect" to "Reconnecter",
    "Copy This Phone's Key" to "Copier la clé de ce téléphone",
    "Trust this server?" to "Faire confiance à ce serveur ?",
    "Trust" to "Faire confiance",
    "Not connected: the server's key wasn't trusted." to "Non connecté : la clé du serveur n’a pas été approuvée.",
    "Edit Account" to "Modifier le compte",
    "Sign in to your server over SSH, with a key made on this phone. Passwords aren't used." to "Connectez-vous à votre serveur en SSH avec une clé créée sur ce téléphone. Aucun mot de passe n’est utilisé.",
    "Host" to "Hôte",
    "Port" to "Port",
    "User" to "Utilisateur",
    "the server's host name" to "le nom d’hôte du serveur",
    "a port (22, unless your server uses another)" to "un port (22, sauf si votre serveur en utilise un autre)",
    "the user: the account you log in to on the server, such as hermes" to "l’utilisateur : le compte utilisé sur le serveur, par exemple hermes",
    "Connect" to "Connecter",
    "This phone's key" to "Clé de ce téléphone",
    "Unlock this phone to make its key." to "Déverrouillez ce téléphone pour créer sa clé.",
    "Copied" to "Copiée",
    "Share SSH public key" to "Partager la clé publique SSH",
    "Open Terminal" to "Ouvrir le terminal",
    "required, such as hermes" to "requis, par exemple hermes",
    "Server's key" to "Clé du serveur",
    "Fingerprint" to "Empreinte",
    "Forget This Server's Key" to "Oublier la clé de ce serveur",
    "Not met yet: the first connection shows its key's fingerprint and asks." to "Pas encore rencontré : la première connexion affichera l’empreinte de sa clé et demandera votre accord.",
    "A server that shows another key later is refused, in case something is in the way. Forget the old one only if the server was reinstalled." to "Un serveur qui présente ensuite une autre clé sera refusé, au cas où un tiers s’interposerait. N’oubliez l’ancienne que si le serveur a été réinstallé.",
    "Make a New Key for This Phone" to "Créer une nouvelle clé pour ce téléphone",
    "Servers that have the old key won't let this phone in until they have the new one." to "Les serveurs qui ont l’ancienne clé refuseront ce téléphone tant qu’ils n’auront pas la nouvelle.",
    "Make a new key?" to "Créer une nouvelle clé ?",
    "Make a New Key" to "Créer une nouvelle clé",
    "The connection closed." to "La connexion est fermée.",
    "This device couldn't make or keep its SSH key. Unlock it and try again." to "Cet appareil n’a pas pu créer ou conserver sa clé SSH. Déverrouillez-le et réessayez.",
    "The server didn't accept this device's key. Add the key below to ~/.ssh/authorized_keys for that user." to "Le serveur a refusé la clé de cet appareil. Ajoutez la clé ci-dessous à ~/.ssh/authorized_keys pour cet utilisateur.",
    "The server didn't start a shell." to "Le serveur n’a pas démarré de shell.",
    "the connection closed." to "la connexion s’est fermée.",

    // Sonde Dashboard affichée dans la confirmation.
    "The Dashboard address isn't a valid URL." to "L’adresse du Dashboard n’est pas une URL valide.",
)

internal val onyxSetupTerminalFrenchPatterns: List<Pair<Regex, (MatchResult) -> String>> = listOf(
    Regex("^This setup code has an address Onyx can't use: (.+)$") to { match ->
        "Ce code contient une adresse que Onyx ne peut pas utiliser : ${match.groupValues[1]}"
    },
    Regex("^user (.+)$") to { match -> "utilisateur ${match.groupValues[1]}" },
    Regex("^Connecting to (.+)…$") to { match -> "Connexion à ${match.groupValues[1]}…" },
    Regex("^To connect, fill in (.+)\\.$") to { match ->
        "Pour vous connecter, renseignez ${OnyxLocalization.translate(match.groupValues[1], java.util.Locale.FRANCE)}."
    },
    Regex("^Add it as one line to ~/\\.ssh/authorized_keys for (.+) on (.+)\\. The key was made on this phone and never leaves it\\.$") to { match ->
        val who = match.groupValues[1].let { if (it == "that user") "cet utilisateur" else it }
        "Ajoutez-la sur une ligne dans ~/.ssh/authorized_keys pour $who sur ${match.groupValues[2]}. La clé a été créée sur ce téléphone et ne le quitte jamais."
    },
    Regex("^This is the first time this phone connects to (.+)\\. Its key's fingerprint is (.+)\\. If you can, check it on the server with ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key\\.pub\\.$") to { match ->
        "C’est la première connexion de ce téléphone à ${match.groupValues[1]}. L’empreinte de sa clé est ${match.groupValues[2]}. Si possible, vérifiez-la sur le serveur avec ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub."
    },
    Regex("^The shell ended \\(exit status (\\d+)\\)\\.$") to { match ->
        "Le shell s’est terminé (code de sortie ${match.groupValues[1]})."
    },
    Regex("^This device hasn't met this server before \\(its key is (.+)\\)\\.$") to { match ->
        "Cet appareil n’a encore jamais rencontré ce serveur (sa clé est ${match.groupValues[1]})."
    },
    Regex("^The server's key has changed since you trusted it \\(was (.+), now (.+)\\)\\. If the server was reinstalled, forget its old key in Settings › Terminal and connect again; if not, someone may be in the way\\.$") to { match ->
        "La clé du serveur a changé depuis votre accord (avant ${match.groupValues[1]}, maintenant ${match.groupValues[2]}). Si le serveur a été réinstallé, oubliez son ancienne clé dans Réglages › Terminal et reconnectez-vous ; sinon, un tiers pourrait s’interposer."
    },
    Regex("^Couldn't reach the server: (.+)$") to { match -> "Impossible de joindre le serveur : ${match.groupValues[1]}" },
    Regex("^No Hermes Dashboard answered at (.+)\\.$") to { match -> "Aucun Dashboard Hermes n’a répondu à ${match.groupValues[1]}." },
    Regex("^Hermes Dashboard answered HTTP (\\d+)\\.$") to { match -> "Le Dashboard Hermes a répondu HTTP ${match.groupValues[1]}." },
    Regex("^Connected to Hermes Dashboard (.+) \\(no login required\\)\\.$") to { match ->
        "Connecté au Dashboard Hermes ${match.groupValues[1]} (aucune connexion requise)."
    },
    Regex("^Connected to Hermes Dashboard (.+)\\. Login accepted\\.$") to { match ->
        "Connecté au Dashboard Hermes ${match.groupValues[1]}. Identifiants acceptés."
    },
    Regex("^Hermes Dashboard (.+) reachable, but login failed: (.+)$") to { match ->
        "Le Dashboard Hermes ${match.groupValues[1]} est accessible, mais la connexion a échoué : ${match.groupValues[2]}"
    },
    Regex("^(.+) is behind an access gate \\(HTTP (\\d+)\\)\\. Add its headers under Settings → Connection details\\.$") to { match ->
        "${match.groupValues[1]} est protégé par un accès (HTTP ${match.groupValues[2]}). Ajoutez ses en-têtes dans Réglages → Détails de connexion."
    },
)
