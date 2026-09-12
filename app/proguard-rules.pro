# R8 rules for the release build.
#
# These were arrived at empirically rather than copied. The merged configuration R8 actually ran
# with is written to build/outputs/mapping/release/configuration.txt, and reading it showed that
# almost everything this app needs is already supplied by somebody else:
#
#   * proguard-android-optimize.txt (AGP 9.3.2) already keeps AnnotationDefault, EnclosingMethod,
#     InnerClasses, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations and Signature.
#   * com.squareup.retrofit2:retrofit:2.11.0 ships META-INF/proguard/retrofit2.pro, which keeps any
#     interface carrying @retrofit2.http.* methods and re-keeps the same attributes.
#   * org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3 ships the conditional rules that
#     keep the Companion and the generated serializer() of @Serializable classes.
#   * com.squareup.okhttp3:okhttp, com.google.dagger:hilt-android, com.google.dagger:dagger and
#     com.google.android.gms:play-services-* all contribute their own sections.
#
# That was checked, not assumed: building with these app rules reduced to nothing produced a
# byte-identical APK, with all five Retrofit interfaces, all fifteen generated serializers and
# every URL and JSON key string still present in classes.dex. So only the rule below is
# load-bearing, and the rest of this file is deliberately empty of cargo.

# The one attribute the AGP default does NOT keep. Without it a release stack trace has no line
# numbers and no source file, and mapping.txt can only recover class and method names -- which is
# the difference between a crash report that points at a line and one that points at a class.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Notes on the three reflection-adjacent paths that were specifically verified to survive, none of
# which needed a rule here:
#
#   Retrofit proxies -- the five interfaces in data.remote are kept by retrofit2.pro and their
#   generic return types by the AGP default's Signature. The @GET paths ("v1/forecast",
#   "route/v1/driving", "alerts/active", "v1/search") are all still in the dex.
#
#   kotlinx.serialization -- property names need no protection at all, because the compiler plugin
#   writes them into the generated descriptor as string literals at compile time rather than
#   reading them from the field names at runtime. Obfuscating the fields therefore cannot change
#   the JSON. Confirmed by finding temperature_2m, weather_code, messageType and the rest intact in
#   the dex of the shrunk APK.
#
#   Play Services availability -- DeviceLocationProvider calls GoogleApiAvailability directly to
#   decide between the fused provider and the platform LocationManager fallback. It is an ordinary
#   call, not reflection, so shrinking cannot lose it.
#
# There is no enum parsed by valueOf() anywhere in the app: the CAP severity and urgency strings
# are matched with a when block over string literals, so enum name obfuscation is not a hazard.
