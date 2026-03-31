-dontwarn com.gemalto.jp2.**

# ML Kit discovers Firebase component registrars from manifest metadata and
# instantiates them reflectively in release builds.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
    java.util.List getComponents();
}

-keep class com.google.mlkit.common.internal.MlKitInitProvider
