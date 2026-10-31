# Le noyau n'expose que du Kotlin : rien à garder pour les consommateurs.
# kotlinx.serialization génère ses sérialiseurs dans les classes concernées, mais ses
# MetadataKt/`$$serializer` restent invisibles depuis l'extérieur.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
