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

import com.android.annotations.NonNull;
import com.android.utils.AsmUtils;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Visitor for classes that will eventually be replaceable at runtime.
 * <p>
 * Since classes cannot be replaced in an existing class loader, we use a delegation model to
 * redirect any method implementation to the AndroidInstantRuntime.
 * <p>
 * This redirection happens only when a new class implementation is available. A new version
 * will register itself in a static synthetic field called $change. Each method will be enhanced
 * with a piece of code to check if a new version is available by looking at the $change field
 * and redirect if necessary.
 * <p>
 * Redirection will be achieved by calling a
 */
public class IncrementalSupportVisitor extends IncrementalVisitor {

    private boolean disableRedirectionForClass = false;

    private static final class VisitorBuilder implements IncrementalVisitor.VisitorBuilder {

        private VisitorBuilder() {
        }

        @NonNull
        @Override
        public IncrementalVisitor build(
                @NonNull ClassNode classNode,
                @NonNull List<ClassNode> parentNodes,
                @NonNull ClassVisitor classVisitor) {
            return new IncrementalSupportVisitor(classNode, parentNodes, classVisitor);
        }

        @Override
        @NonNull
        public String getMangledRelativeClassFilePath(@NonNull String originalClassFilePath) {
            return originalClassFilePath;
        }

        @NonNull
        @Override
        public OutputType getOutputType() {
            return OutputType.INSTRUMENT;
        }
    }

    public static final IncrementalVisitor.VisitorBuilder VISITOR_BUILDER =
            new VisitorBuilder();

    public IncrementalSupportVisitor(
            @NonNull ClassNode classNode,
            @NonNull List<ClassNode> parentNodes,
            @NonNull ClassVisitor classVisitor) {
        super(classNode, parentNodes, classVisitor);
    }

    /**
     * Ensures that the class contains a $change field used for referencing the
     * IncrementalChange dispatcher.
     * <p/>
     * Also updates package_private visiblity to public so we can call into this class from
     * outside the package.
     */
    @Override
    public void visit(int version, int access, String name, String signature, String superName,
                      String[] interfaces) {
        visitedClassName = name;
        visitedSuperName = superName;

        super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
                        | Opcodes.ACC_VOLATILE | Opcodes.ACC_SYNTHETIC,
                "$change", getRuntimeTypeName(CHANGE_TYPE), null, null);
        access = transformClassAccessForInstantRun(access);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (desc.equals(DISABLE_ANNOTATION_TYPE.getDescriptor())) {
            disableRedirectionForClass = true;
        }
        return super.visitAnnotation(desc, visible);
    }


    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature,
                                   Object value) {

        access = transformAccessForInstantRun(access);
        return super.visitField(access, name, desc, signature, value);
    }

    /**
     * Insert Constructor specific logic({@link ConstructorArgsRedirection} and
     * {@link ConstructorDelegationDetector}) for constructor redirecting or
     * normal method redirecting ({@link MethodRedirection}) for other methods.
     */
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                     String[] exceptions) {

        access = transformAccessForInstantRun(access);

        MethodVisitor defaultVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        MethodNode method = getMethodByNameInClass(name, desc, classNode);
        // does the method use blacklisted APIs.
        boolean hasIncompatibleChange = InstantRunMethodVerifier.verifyMethod(method)
                != InstantRunVerifierStatus.COMPATIBLE;

        if (hasIncompatibleChange || disableRedirectionForClass
                || !isAccessCompatibleWithInstantRun(access)
                || name.equals(AsmUtils.CLASS_INITIALIZER)) {
            return defaultVisitor;
        } else {
            ISMethodVisitor mv = new ISMethodVisitor(defaultVisitor, access, name, desc);
            if (name.equals(AsmUtils.CONSTRUCTOR)) {

                ConstructorDelegationDetector.Constructor constructor =
                        ConstructorDelegationDetector.deconstruct(visitedClassName, method);
                LabelNode start = new LabelNode();
                LabelNode after = new LabelNode();
                method.instructions.insert(constructor.loadThis, start);
                if (constructor.lineForLoad != -1) {
                    // Record the line number from the start of LOAD_0 for uninitialized 'this'.
                    // This allows a breakpoint to be set at the line with this(...) or super(...)
                    // call in the constructor.
                    method.instructions.insert(constructor.loadThis,
                            new LineNumberNode(constructor.lineForLoad, start));
                }
                method.instructions.insert(constructor.delegation, after);
                mv.addRedirection(
                        new ConstructorArgsRedirection(
                                start,
                                visitedClassName,
                                constructor.args.name + "." + constructor.args.desc,
                                after,
                                Type.getArgumentTypes(constructor.delegation.desc)));

                mv.addRedirection(new MethodRedirection(after, constructor.body.name + "."
                        + constructor.body.desc, Type.getReturnType(desc)));
            } else {
                mv.addRedirection(new MethodRedirection(
                        new LabelNode(mv.getStartLabel()),
                        name + "." + desc,
                        Type.getReturnType(desc)));
            }
            method.accept(mv);
            return null;
        }
    }

    /**
     * If a class is package private, make it public so instrumented code living in a different
     * class loader can instantiate them.
     *
     * @param access the original class/method/field access.
     * @return the new access or the same one depending on the original access rights.
     */
    private static int transformClassAccessForInstantRun(int access) {
        AccessRight accessRight = AccessRight.fromNodeAccess(access);
        return accessRight == AccessRight.PACKAGE_PRIVATE ? access | Opcodes.ACC_PUBLIC : access;
    }

    /**
     * If a method/field is not private, make it public. This is to workaround the fact
     * <ul>Our restart.dex files are loaded with a different class loader than the main dex file
     * class loader on restart. so we need methods/fields to be public</ul>
     * <ul>Our reload.dex are loaded from a different class loader as well but methods/fields
     * are accessed through reflection, yet you need class visibility.</ul>
     * <p>
     * remember that in Java, protected methods or fields can be acessed by classes in the same
     * package :
     * {@see https://docs.oracle.com/javase/tutorial/java/javaOO/accesscontrol.html}
     *
     * @param access the original class/method/field access.
     * @return the new access or the same one depending on the original access rights.
     */
    private static int transformAccessForInstantRun(int access) {
        AccessRight accessRight = AccessRight.fromNodeAccess(access);
        if (accessRight != AccessRight.PRIVATE) {
            access &= ~Opcodes.ACC_PROTECTED;
            access &= ~Opcodes.ACC_PRIVATE;
            return access | Opcodes.ACC_PUBLIC;
        }
        return access;
    }

    private class ISMethodVisitor extends GeneratorAdapter {

        private boolean disableRedirection = false;
        private int change;
        private final List<Type> args;
        private final List<Redirection> redirections;
        private final Map<Label, Redirection> resolvedRedirections;
        private final Label start;

        public ISMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM5, mv, access, name, desc);
            this.change = -1;
            this.redirections = new ArrayList<Redirection>();
            this.resolvedRedirections = new HashMap<Label, Redirection>();
            this.args = new ArrayList<Type>(Arrays.asList(Type.getArgumentTypes(desc)));
            this.start = new Label();
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            // if this is not a static, we add a fictional first parameter what will contain the
            // "this" reference which can be loaded with ILOAD_0 bytecode.
            if (!isStatic) {
                args.add(0, Type.getType(Object.class));
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (desc.equals(DISABLE_ANNOTATION_TYPE.getDescriptor())) {
                disableRedirection = true;
            }
            return super.visitAnnotation(desc, visible);
        }

        /**
         * inserts a new local '$change' in each method that contains a reference to the type's
         * IncrementalChange dispatcher, this is done to avoid threading issues.
         * <p/>
         * Pseudo code:
         * <code>
         * $package/IncrementalChange $local1 = $className$.$change;
         * </code>
         */
        @Override
        public void visitCode() {
            if (!disableRedirection) {
                // Labels cannot be used directly as they are volatile between different visits,
                // so we must use LabelNode and resolve before visiting for better performance.
                for (Redirection redirection : redirections) {
                    resolvedRedirections.put(redirection.getPosition().getLabel(), redirection);
                }

                super.visitLabel(start);
                change = newLocal(CHANGE_TYPE);
                visitFieldInsn(Opcodes.GETSTATIC, visitedClassName, "$change",
                        getRuntimeTypeName(CHANGE_TYPE));
                storeLocal(change);

                redirectAt(start);
            }
            super.visitCode();
        }

        @Override
        public void visitLabel(Label label) {
            super.visitLabel(label);
            redirectAt(label);
        }

        private void redirectAt(Label label) {
            if (disableRedirection) return;
            Redirection redirection = resolvedRedirections.get(label);
            if (redirection != null) {
                // A special line number to mark this area of code.
                super.visitLineNumber(0, label);
                redirection.redirect(this, change, args);
            }
        }

        public void addRedirection(@NonNull Redirection redirection) {
            redirections.add(redirection);
        }


        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start,
                                       Label end, int index) {
            // In dex format, the argument names are separated from the local variable names. It
            // seems to be needed to declare the local argument variables from the beginning of
            // the methods for dex to pick that up. By inserting code before the first label we
            // break that. In Java this is fine, and the debugger shows the right thing. However
            // if we don't readjust the local variables, we just don't see the arguments.
            if (!disableRedirection && index < args.size()) {
                start = this.start;
            }
            super.visitLocalVariable(name, desc, signature, start, end, index);
        }

        public Label getStartLabel() {
            return start;
        }
    }

    /**
     * Decorated {@link MethodNode} that maintains a reference to the class declaring the method.
     */
    private static class MethodReference {
        final MethodNode method;
        final ClassNode owner;

        private MethodReference(MethodNode method, ClassNode owner) {
            this.method = method;
            this.owner = owner;
        }
    }

    /***
     * Inserts a trampoline to this class so that the updated methods can make calls to super
     * class methods.
     * <p/>
     * Pseudo code for this trampoline:
     * <code>
     * Object access$super($classType instance, String name, object[] args) {
     * switch(name) {
     * case "firstMethod.(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;":
     * return super~instance.firstMethod((String)arg[0], arg[1]);
     * case "secondMethod.(Ljava/lang/String;I)V":
     * return super~instance.firstMethod((String)arg[0], arg[1]);
     * <p>
     * default:
     * StringBuilder $local1 = new StringBuilder();
     * $local1.append("Method not found ");
     * $local1.append(name);
     * $local1.append(" in " $classType $super implementation");
     * throw new $package/InstantReloadException($local1.toString());
     * }
     * </code>
     */
    private void createAccessSuper() {
        int access = Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC
                | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_VARARGS;
        Method m = new Method("access$super", "(L" + visitedClassName
                + ";Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
        MethodVisitor visitor = super.visitMethod(access,
                m.getName(),
                m.getDescriptor(),
                null, null);

        final GeneratorAdapter mv = new GeneratorAdapter(access, m, visitor);

        // Gather all methods from itself and its superclasses to generate a giant access$super
        // implementation.
        // This will work fine as long as we don't support adding methods to a class.
        final Map<String, MethodReference> uniqueMethods =
                new HashMap<String, MethodReference>();
        if (parentNodes.isEmpty()) {
            // if we cannot determine the parents for this class, let's blindly add all the
            // method of the current class as a gateway to a possible parent version.
            addAllNewMethods(uniqueMethods, classNode);
        } else {
            // otherwise, use the parent list.
            for (ClassNode parentNode : parentNodes) {
                addAllNewMethods(uniqueMethods, parentNode);
            }
        }

        new StringSwitch() {
            @Override
            void visitString() {
                mv.visitVarInsn(Opcodes.ALOAD, 1);
            }

            @Override
            void visitCase(String methodName) {
                MethodReference methodRef = uniqueMethods.get(methodName);

                mv.visitVarInsn(Opcodes.ALOAD, 0);

                Type[] args = Type.getArgumentTypes(methodRef.method.desc);
                int argc = 0;
                for (Type t : args) {
                    mv.visitVarInsn(Opcodes.ALOAD, 2);
                    mv.push(argc);
                    mv.visitInsn(Opcodes.AALOAD);
                    ByteCodeUtils.unbox(mv, t);
                    argc++;
                }

                if (TRACING_ENABLED) {
                    trace(mv, "super selected ", methodRef.owner.name,
                            methodRef.method.name, methodRef.method.desc);
                }
                // Call super on the other object, yup this works cos we are on the right place to
                // call from.
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                        methodRef.owner.name,
                        methodRef.method.name,
                        methodRef.method.desc, false);

                Type ret = Type.getReturnType(methodRef.method.desc);
                if (ret.getSort() == Type.VOID) {
                    mv.visitInsn(Opcodes.ACONST_NULL);
                } else {
                    mv.box(ret);
                }
                mv.visitInsn(Opcodes.ARETURN);
            }

            @Override
            void visitDefault() {
                writeMissingMessageWithHash(mv, visitedClassName);
            }
        }.visit(mv, uniqueMethods.keySet());

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /***
     * Inserts a trampoline to this class so that the updated methods can make calls to
     * constructors.
     * <p>
     * <p/>
     * Pseudo code for this trampoline:
     * <code>
     * ClassName(Object[] args, Marker unused) {
     * String name = (String) args[0];
     * if (name.equals(
     * "java/lang/ClassName.(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;")) {
     * this((String)arg[1], arg[2]);
     * return
     * }
     * if (name.equals("SuperClassName.(Ljava/lang/String;I)V")) {
     * super((String)arg[1], (int)arg[2]);
     * return;
     * }
     * ...
     * StringBuilder $local1 = new StringBuilder();
     * $local1.append("Method not found ");
     * $local1.append(name);
     * $local1.append(" in " $classType $super implementation");
     * throw new $package/InstantReloadException($local1.toString());
     * }
     * </code>
     */
    private void createDispatchingThis() {
        // Gather all methods from itself and its superclasses to generate a giant constructor
        // implementation.
        // This will work fine as long as we don't support adding constructors to classes.
        final Map<String, MethodNode> uniqueMethods = new HashMap<String, MethodNode>();

        addAllNewConstructors(uniqueMethods, classNode, true /*keepPrivateConstructors*/);
        for (ClassNode parentNode : parentNodes) {
            addAllNewConstructors(uniqueMethods, parentNode, false /*keepPrivateConstructors*/);
        }

        int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC;

        Method m = new Method(AsmUtils.CONSTRUCTOR,
                ConstructorArgsRedirection.DISPATCHING_THIS_SIGNATURE);
        MethodVisitor visitor = super.visitMethod(0, m.getName(), m.getDescriptor(), null, null);
        final GeneratorAdapter mv = new GeneratorAdapter(access, m, visitor);

        mv.visitCode();
        // Mark this code as redirection code
        Label label = new Label();
        mv.visitLineNumber(0, label);

        // Get and store the constructor canonical name.
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.push(0);
        mv.visitInsn(Opcodes.AALOAD);
        mv.unbox(Type.getType("Ljava/lang/String;"));
        final int constructorCanonicalName = mv.newLocal(Type.getType("Ljava/lang/String;"));
        mv.storeLocal(constructorCanonicalName);

        new StringSwitch() {

            @Override
            void visitString() {
                mv.loadLocal(constructorCanonicalName);
            }

            @Override
            void visitCase(String canonicalName) {
                MethodNode methodNode = uniqueMethods.get(canonicalName);
                String owner = canonicalName.split("\\.")[0];

                // Parse method arguments and
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                Type[] args = Type.getArgumentTypes(methodNode.desc);
                int argc = 0;
                for (Type t : args) {
                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                    mv.push(argc + 1);
                    mv.visitInsn(Opcodes.AALOAD);
                    ByteCodeUtils.unbox(mv, t);
                    argc++;
                }

                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, AsmUtils.CONSTRUCTOR,
                        methodNode.desc, false);

                mv.visitInsn(Opcodes.RETURN);
            }

            @Override
            void visitDefault() {
                writeMissingMessageWithHash(mv, visitedClassName);
            }
        }.visit(mv, uniqueMethods.keySet());

        mv.visitMaxs(1, 3);
        mv.visitEnd();
    }

    @Override
    public void visitEnd() {
        createAccessSuper();
        createDispatchingThis();
        super.visitEnd();
    }

    /**
     * Add all unseen methods from the passed ClassNode's methods. {@see ClassNode#methods}
     *
     * @param methods   the methods already encountered in the ClassNode hierarchy
     * @param classNode the class to save all new methods from.
     */
    private static void addAllNewMethods(Map<String, MethodReference> methods, ClassNode classNode) {
        //noinspection unchecked
        for (MethodNode method : (List<MethodNode>) classNode.methods) {
            if (method.name.equals(AsmUtils.CONSTRUCTOR) || method.name.equals("<clinit>")) {
                continue;
            }
            String name = method.name + "." + method.desc;
            if (isAccessCompatibleWithInstantRun(method.access)
                    && !methods.containsKey(name) &&
                    (method.access & Opcodes.ACC_STATIC) == 0 &&
                    (method.access & Opcodes.ACC_PRIVATE) == 0) {
                methods.put(name, new MethodReference(method, classNode));
            }
        }
    }

    /**
     * Add all constructors from the passed ClassNode's methods. {@see ClassNode#methods}
     *
     * @param methods                 the constructors already encountered in the ClassNode hierarchy
     * @param classNode               the class to save all new methods from.
     * @param keepPrivateConstructors whether to keep the private constructors.
     */
    private void addAllNewConstructors(Map<String, MethodNode> methods, ClassNode classNode,
                                       boolean keepPrivateConstructors) {
        //noinspection unchecked
        for (MethodNode method : (List<MethodNode>) classNode.methods) {
            if (!method.name.equals(AsmUtils.CONSTRUCTOR)) {
                continue;
            }

            if (!isAccessCompatibleWithInstantRun(method.access)) {
                continue;
            }

            if (!keepPrivateConstructors && (method.access & Opcodes.ACC_PRIVATE) != 0) {
                continue;
            }
            if (!classNode.name.equals(visitedClassName)
                    && !classNode.name.equals(visitedSuperName)) {
                continue;
            }
            String key = classNode.name + "." + method.desc;
            if (methods.containsKey(key)) {
                continue;
            }
            methods.put(key, method);
        }
    }

    /**
     * Command line invocation entry point. Expects 2 parameters, first is the source directory
     * with .class files as produced by the Java compiler, second is the output directory where to
     * store the bytecode enhanced version.
     *
     * @param args the command line arguments.
     * @throws IOException if some files cannot be read or written.
     */
    public static void mainMe(String args1, String arg2,String arg3) throws IOException {
        IncrementalVisitor.main(new String[]{args1, arg2,arg3}, VISITOR_BUILDER);
    }
}
