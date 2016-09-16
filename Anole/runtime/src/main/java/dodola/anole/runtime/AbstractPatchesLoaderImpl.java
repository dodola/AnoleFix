/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package dodola.anole.runtime;


import java.lang.reflect.Field;

public abstract class AbstractPatchesLoaderImpl implements PatchesLoader {

    public abstract String[] getPatchedClasses();

    @Override
    public boolean load() {
        try {
            for (String className : getPatchedClasses()) {
                ClassLoader cl = getClass().getClassLoader();
                Class<?> aClass = cl.loadClass(className + "$override");
                Object o = aClass.newInstance();
                Class<?> originalClass = cl.loadClass(className);
                Field changeField = originalClass.getDeclaredField("$change");
                // force the field accessibility as the class might not be "visible"
                // from this package.
                changeField.setAccessible(true);

                // If there was a previous change set, mark it as obsolete:
                Object previous = changeField.get(null);
                if (previous != null) {
                    Field isObsolete = previous.getClass().getDeclaredField("$obsolete");
                    if (isObsolete != null) {
                        isObsolete.set(null, true);
                    }
                }
                changeField.set(null, o);

            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
