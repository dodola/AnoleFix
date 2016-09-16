/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package dodola.anole.lib;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import dalvik.system.DexClassLoader;
import dodola.anole.runtime.PatchesLoader;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by sunpengfei on 16/9/15.
 */

public class Anole {


    private static final String LOG_TAG = "!!Anole!!";

    public static void applyPatch(Context context, String dexFile) {
        try {
            ClassLoader classLoader = context.getClass().getClassLoader();

            String nativeLibraryPath;
            try {
                nativeLibraryPath = (String) classLoader.getClass().getMethod("getLdLibraryPath")
                        .invoke(classLoader);
            } catch (Throwable t) {
                nativeLibraryPath = getNativeLibraryFolder(context).getPath();
            }
            DexClassLoader dexClassLoader = new DexClassLoader(dexFile,
                    context.getCacheDir().getPath(), nativeLibraryPath,
                    context.getClass().getClassLoader());

            // we should transform this process with an interface/impl
            Class<?> aClass = Class.forName(
                    "dodola.anole.runtime.AppPatchesLoaderImpl", true, dexClassLoader);
            try {

                PatchesLoader loader = (PatchesLoader) aClass.newInstance();
                String[] getPatchedClasses = (String[]) aClass
                        .getDeclaredMethod("getPatchedClasses").invoke(loader);
                Log.v(LOG_TAG, "Got the list of classes ");
                for (String getPatchedClass : getPatchedClasses) {
                    Log.v(LOG_TAG, "class " + getPatchedClass);
                }
                if (!loader.load()) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    /**
     * 从Assets里取出补丁，一般用于测试
     *
     * @param context
     * @param assetName
     */
    public static String getPathFromAssets(Context context, String assetName) {
        File dexDir = new File(context.getFilesDir(), "hotfix");
        dexDir.mkdirs();
        String dexPath = null;
        try {
            dexPath = copyAsset(context, assetName, dexDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return dexPath;
    }

    public static String copyAsset(Context context, String assetName, File dir) throws IOException {
        File outFile = new File(dir, assetName);
        if (outFile.exists()) {
            outFile.delete();
        }
        AssetManager assetManager = context.getAssets();
        InputStream in = assetManager.open(assetName);
        OutputStream out = new FileOutputStream(outFile);
        copyFile(in, out);
        in.close();
        out.close();
        return outFile.getAbsolutePath();
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private static File getNativeLibraryFolder(Context context) {
        return context.getDir("lib", MODE_PRIVATE);
    }

}
