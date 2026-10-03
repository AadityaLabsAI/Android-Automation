package com.aadityalabs.needle2;
import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
public final class ModelRepository {
    private static final String MODEL="needle2.cact";
    private ModelRepository(){}
    public static File ensure(Context c)throws Exception{
        File out=new File(c.getFilesDir(),MODEL);
        if(out.exists()&&out.length()>10000000)return out;
        File tmp=new File(c.getFilesDir(),MODEL+".tmp");
        try(InputStream in=c.getAssets().open(MODEL);FileOutputStream f=new FileOutputStream(tmp)){
            byte[] b=new byte[65536];int n;
            while((n=in.read(b))>0)f.write(b,0,n);
            f.getFD().sync();
        }
        if(!tmp.renameTo(out))throw new IllegalStateException("Could not install local model");
        return out;
    }
}
