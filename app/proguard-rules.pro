# Preserve Java methods invoked from native Sherpa-ONNX / Piper JNI code.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep enum metadata and annotations used by serialization/reflection.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
