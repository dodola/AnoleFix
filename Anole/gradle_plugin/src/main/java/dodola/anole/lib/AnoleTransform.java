/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package dodola.anole.lib;///*
// * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
// */
//package dodola.anole.lib;
//
//import com.android.build.api.transform.Context;
//import com.android.build.api.transform.DirectoryInput;
//import com.android.build.api.transform.QualifiedContent;
//import com.android.build.api.transform.Transform;
//import com.android.build.api.transform.TransformException;
//import com.android.build.api.transform.TransformInput;
//import com.android.build.api.transform.TransformOutputProvider;
//import com.android.build.gradle.internal.pipeline.TransformManager;
//
//import java.io.IOException;
//import java.util.Collection;
//import java.util.Set;
//
///**
// * Created by sunpengfei on 16/9/15.
// */
//
//public class AnoleTransform extends Transform {
//    @Override
//    public String getName() {
//        return "dodola";
//    }
//
//    @Override
//    public Set<QualifiedContent.ContentType> getInputTypes() {
//        return TransformManager.CONTENT_CLASS;
//    }
//
//    @Override
//    public Set<QualifiedContent.Scope> getScopes() {
//        return TransformManager.SCOPE_FULL_PROJECT;
//    }
//
//    @Override
//    public boolean isIncremental() {
//        return false;
//    }
//
//    @Override
//    public void transform(Context context, Collection<TransformInput> inputs, Collection<TransformInput> referencedInputs, TransformOutputProvider outputProvider, boolean isIncremental) throws IOException, TransformException, InterruptedException {
////        super.transform(context, inputs, referencedInputs, outputProvider, isIncremental);
//
//        for (TransformInput input : inputs) {
//            Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
//            for (DirectoryInput directoryInput : directoryInputs) {
//                System.out.println("====" + directoryInput.getFile().getAbsolutePath());
//                IncrementalSupportVisitor.mainMe(new String[]{directoryInput.getFile().getAbsolutePath(), directoryInput.getFile().getAbsolutePath()});
//
//            }
//        }
//    }
//}
