package com.aadityalabs.needle2;
public final class NativeNeedle {
    static { System.loadLibrary("needle2_jni"); }
    private NativeNeedle(){}
    public static native int nativeLoadModel(String path);
    public static native int nativeInit(String systemPrompt,String toolsJson,String toolIndexPath);
    public static native String nativeComplete(String input,int maxTokens);
    public static native void nativeReset();
}
