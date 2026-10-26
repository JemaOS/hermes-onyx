# Onyx — règles ProGuard du release.

# kotlinx.serialization génère les sérialiseurs dans les classes annotées; le plugin les garde,
# mais R8 doit garder les `Companion` et les champs des sérialiseurs générés.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.onyx.app.**$$serializer { *; }
-keepclassmembers class com.onyx.app.** {
    *** Companion;
}
-dontnote kotlinx.serialization.**

# OkHttp / Okio: R8 sait les traiter, mais les avertissements sur les plateformes absentes
# (Conscrypt, BouncyCastle, JVM) ne doivent pas faire échouer le build.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn kotlinx.serialization.**

# Markwon se résout par réflexion sur ses plugins.
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**

# Glance construit ses compositions à partir de classes nommées.
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# Le noyau expose ses modèles à la sérialisation, au widget et à la montre.
-keep class com.onyx.app.data.model.** { *; }
-keep class com.onyx.app.data.net.** { *; }

# sshj (terminal): des classes de plateforme absentes sur Android, et le fournisseur EdDSA
# qui les référence — R8 n'a pas à échouer pour autant.
-dontwarn sun.security.x509.**
-dontwarn net.i2p.crypto.eddsa.**
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-keep class net.schmizz.sshj.** { *; }
-dontwarn javax.security.auth.login.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.naming.**
-dontwarn java.awt.**
-dontwarn javax.swing.**
