package com.aadityalabs.needle2;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import java.io.IOException;

public final class ModelRepository {
    private ModelRepository() {}

    public static AssetFileDescriptor openModel(Context context) throws IOException {
        return context.getAssets().openFd("needle2.cact");
    }
}
