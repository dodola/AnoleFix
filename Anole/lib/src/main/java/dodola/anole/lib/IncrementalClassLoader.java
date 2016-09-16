/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */

package dodola.anole.lib;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import dalvik.system.BaseDexClassLoader;


// This is based on com.google.devtools.build.android.incrementaldeployment.IncrementalClassLoader
// with some cleanup around path handling and made it compile on JDK 6 (e.g. removed multicatch
// etc)
// See
//  https://github.com/google/bazel/blob/master/src/tools/android/java/com/google/devtools/build/android/incrementaldeployment/IncrementalClassLoader.java
// (May 11 revision, ca96e11)

/**
 * A class loader that loads classes from any .dex file in a particular directory on the SD card.
 * <p>
 * <p>Used to implement incremental deployment to Android phones.
 */
public class IncrementalClassLoader extends ClassLoader {
    /**
     * When false, compiled out of runtime library
     */
    public static final boolean DEBUG_CLASS_LOADING = false;
    private static final String LOG_TAG = IncrementalClassLoader.class.getName();

    private final DelegateClassLoader delegateClassLoader;

    public IncrementalClassLoader(
            ClassLoader original, String nativeLibraryPath, String codeCacheDir, List<String> dexes) {
        super(original.getParent());

        // TODO(bazel-team): For some mysterious reason, we need to use two class loaders so that
        // everything works correctly. Investigate why that is the case so that the code can be
        // simplified.
        delegateClassLoader = createDelegateClassLoader(nativeLibraryPath, codeCacheDir, dexes,
                original);
    }

    @Override
    public Class<?> findClass(String className) throws ClassNotFoundException {
        try {
            Class<?> aClass = delegateClassLoader.findClass(className);
            //noinspection PointlessBooleanExpression,ConstantConditions
            if (DEBUG_CLASS_LOADING && Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                Log.v(LOG_TAG, "Incremental class loader: findClass(" + className + ") = " + aClass);
            }

            return aClass;
        } catch (ClassNotFoundException e) {
            //noinspection PointlessBooleanExpression,ConstantConditions
            if (DEBUG_CLASS_LOADING && Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                Log.v(LOG_TAG, "Incremental class loader: findClass(" + className + ") : not found");
            }
            throw e;
        }
    }

    /**
     * A class loader whose only purpose is to make {@code findClass()} public.
     */
    private static class DelegateClassLoader extends BaseDexClassLoader {
        private DelegateClassLoader(
                String dexPath, File optimizedDirectory, String libraryPath, ClassLoader parent) {
            super(dexPath, optimizedDirectory, libraryPath, parent);
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                Class<?> aClass = super.findClass(name);
                //noinspection PointlessBooleanExpression,ConstantConditions
                if (DEBUG_CLASS_LOADING && Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Delegate class loader: findClass(" + name + ") = " + aClass);
                }

                return aClass;
            } catch (ClassNotFoundException e) {
                //noinspection PointlessBooleanExpression,ConstantConditions
                if (DEBUG_CLASS_LOADING && Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Delegate class loader: findClass(" + name + ") : not found");
                }
                throw e;
            }
        }
    }

    private static DelegateClassLoader createDelegateClassLoader(
            String nativeLibraryPath, String codeCacheDir, List<String> dexes,
            ClassLoader original) {
        String pathBuilder = createDexPath(dexes);
        return new DelegateClassLoader(pathBuilder, new File(codeCacheDir),
                nativeLibraryPath, original);
    }

    @NonNull
    private static String createDexPath(List<String> dexes) {
        StringBuilder pathBuilder = new StringBuilder();
        boolean first = true;
        for (String dex : dexes) {
            if (first) {
                first = false;
            } else {
                pathBuilder.append(File.pathSeparator);
            }

            pathBuilder.append(dex);
        }

        return pathBuilder.toString();
    }

    private static void setParent(ClassLoader classLoader, ClassLoader newParent) {
        try {
            Field parent = ClassLoader.class.getDeclaredField("parent");
            parent.setAccessible(true);
            parent.set(classLoader, newParent);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static ClassLoader inject(
            ClassLoader classLoader, String nativeLibraryPath, String codeCacheDir,
            List<String> dexes) {
        IncrementalClassLoader incrementalClassLoader =
                new IncrementalClassLoader(classLoader, nativeLibraryPath, codeCacheDir, dexes);
        setParent(classLoader, incrementalClassLoader);

        // This works as follows:
        // We're given the current class loader that's used to load the bootstrap application.
        // We have a new class loader which reads patches/overrides from the data directory
        // instead. We want *that* class loader to have the bootstrap class loader's parent
        // as its parent, and then we make the bootstrap class loader parented by our
        // class loader.
        //
        // In other words, we have this:
        //      BootstrapApplication.classLoader = ClassLoader1, parent=ClassLoader2
        // We create ClassLoader3 from the .dex files in the data directory, and arrange for
        // the hierarchy to be like this:
        //      BootstrapApplication.classLoader = ClassLoader1, parent=ClassLoader3, parent=ClassLoader2
        // With this approach, a class find (which should always look at the parents first) should
        // find anything from ClassLoader3 before they get them from ClassLoader1.
        // (Note that ClassLoader2 in the above is generally the BootClassLoader, not containing
        // any classes we care about.)

        return incrementalClassLoader;
    }
}
