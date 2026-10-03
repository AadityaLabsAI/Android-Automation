-keep class com.aadityalabs.needle2.NativeNeedle { *; }
-keep class com.aadityalabs.needle2.NeedleEngine { *; }
-keep class com.aadityalabs.needle2.AutomationService { *; }
-keep class com.aadityalabs.needle2.TaskReceiver { *; }
-keep class com.aadityalabs.needle2.TaskExecutionService { *; }
-keep class com.aadityalabs.needle2.TaskJobService { *; }
-keepclasseswithmembers,includedescriptorclasses class com.aadityalabs.needle2.** {
    native <methods>;
}
