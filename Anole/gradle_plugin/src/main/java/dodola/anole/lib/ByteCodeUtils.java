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

package dodola.anole.lib;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * Bytecode generation utilities to work around some ASM / Dex issues.
 */
public class ByteCodeUtils {

    private static final Type NUMBER_TYPE = Type.getObjectType("java/lang/Number");
    private static final Method SHORT_VALUE = Method.getMethod("short shortValue()");
    private static final Method BYTE_VALUE = Method.getMethod("byte byteValue()");

    /**
     * Generates unboxing bytecode for the passed type. An {@link Object} is expected to be on the
     * stack when these bytecodes are inserted.
     *
     * ASM takes a short cut when dealing with short/byte types and convert them into int rather
     * than short/byte types. This is not an issue on the jvm nor Android's ART but it is an issue
     * on Dalvik.
     *
     * @param mv the {@link GeneratorAdapter} generating a method implementation.
     * @param type the expected un-boxed type.
     */
    public static void unbox(GeneratorAdapter mv, Type type) {
        if (type.equals(Type.SHORT_TYPE)) {
            mv.checkCast(NUMBER_TYPE);
            mv.invokeVirtual(NUMBER_TYPE, SHORT_VALUE);
        } else if (type.equals(Type.BYTE_TYPE)) {
            mv.checkCast(NUMBER_TYPE);
            mv.invokeVirtual(NUMBER_TYPE, BYTE_VALUE);
        } else {
            mv.unbox(type);
        }
    }
}
