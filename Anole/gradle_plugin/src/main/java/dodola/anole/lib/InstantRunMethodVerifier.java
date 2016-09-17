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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMultimap;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.MethodNode;

/**
 * Verifies that a method implementation is compatible with the InstantRun current capabilities.
 */
public class InstantRunMethodVerifier {

    /**
     * Verifies a method implementation against the blacklisted list of APIs.
     *
     * @param method the method to verify
     * @return a {@link InstantRunVerifierStatus} instance or null if the method is not making any
     * blacklisted calls.
     */
    public static InstantRunVerifierStatus verifyMethod(MethodNode method) {

        VerifierMethodVisitor mv = new VerifierMethodVisitor(method);
        method.accept(mv);
        return mv.incompatibleChange.or(InstantRunVerifierStatus.COMPATIBLE);
    }

    /**
     * {@link MethodVisitor} implementation that checks methods invocation from this method against
     * a list of blacklisted methods that is not compatible with the current InstantRun class
     * reloading capability.
     */
    public static class VerifierMethodVisitor extends MethodNode {

        Optional<InstantRunVerifierStatus> incompatibleChange = Optional.absent();

        public VerifierMethodVisitor(MethodNode method) {
            super(Opcodes.ASM5, method.access, method.name, method.desc, method.signature,
                    (String[]) method.exceptions.toArray(new String[method.exceptions.size()]));
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                                    boolean itf) {

            Type receiver = Type.getType(owner);
            if (!incompatibleChange.isPresent()) {
                if (opcode == Opcodes.INVOKEVIRTUAL && blackListedMethods.containsKey(receiver)) {
                    for (Method method : blackListedMethods.get(receiver)) {
                        if (method.getName().equals(name) && method.getDescriptor().equals(desc)) {
                            incompatibleChange = Optional.of(InstantRunVerifierStatus.REFLECTION_USED);
                        }
                    }
                }
            }

            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }

    // List of all black listed methods.
    // All these methods are java.lang.reflect classes and associated : since the new version of the
    // class is loaded in a different class loader, the classes are in a different package and
    // package private methods would need a setAccessble(true) to work correctly. Eventually, we
    // could transform all reflection calls to automatically insert these setAccessible calls but
    // at this point, we just don't enable InstantRun on those.
    private static final ImmutableMultimap<Type, Method> blackListedMethods =
            ImmutableMultimap.<Type, Method>builder()
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("Object get(Object)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("boolean getBoolean(Object)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("byte getByte(Object)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("char getChar(Object)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("double getDouble(Object)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("float getFloat(Object)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("int getInt(Object)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("long getLong(Object)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("short getShort(Object)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("void set(Object, Object)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("void setBoolean(Object, boolean)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("void setByte(Object, byte)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("void setChar(Object, char)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("void setDouble(Object, double)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("void setFloat(Object, float)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("void setInt(Object, int)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("void setLong(Object, long)"))
                    .put(Type.getType("java/lang/reflect/Field"), Method.getMethod("void setShort(Object, short)"))
                    .put(Type.getType("java/lang/reflect/Constructor"), Method.getMethod("Object newInstance(Object[])"))
                    .put(Type.getType("java/lang/Class"), Method.getMethod("Object newInstance()"))
                    .put(Type.getType("java/lang/reflect/Method"), Method.getMethod("Object invoke(Object, Object[])"))
                    .build();
}
