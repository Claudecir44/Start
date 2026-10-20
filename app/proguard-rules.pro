# Regras do R8 (otimização/ofuscação do release) — ver app/build.gradle.kts.

# Linhas reais nos relatórios do Crashlytics.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Firestore converte documento <-> classe por REFLECTION. Se o R8 renomear
# os getters/setters ou o construtor vazio, toObject() falha só no release
# (aconteceu no Caronas). Todo modelo do Firestore fica no pacote
# data.model — classe nova ali já fica protegida.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class com.cjstudio.start.data.model.** { *; }
