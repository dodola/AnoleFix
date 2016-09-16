package dodola.anole.lib

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.transforms.ProGuardTransform
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.builder.RecursiveToStringStyle
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.logging.Logger
import proguard.gradle.ProGuardTask

import java.util.jar.JarEntry
import java.util.jar.JarFile

class AnolePlugin /*extends Transform*/ implements Plugin<Project> {
    public DefaultProject project;
    public static Logger logger;
    public static final String EXTENSION_NAME = "rocoo_fix";

    private static final String MAPPING_TXT = "mapping.txt"
    private static final String HASH_TXT = "hash.txt"

    @Override
    public void apply(Project target) {
        this.project = target;
        DefaultDomainObjectSet<ApplicationVariant> variants
        if (project.getPlugins().hasPlugin(AppPlugin)) {
            variants = project.android.applicationVariants;
            project.extensions.create(EXTENSION_NAME, RocooFixExtension);
            applyTask(project, variants);

            println(ReflectionToStringBuilder.toString(project.android, RecursiveToStringStyle.MULTI_LINE_STYLE));

//            project.android.registerTransform(this);

        }
        logger = project.logger;
    }

//    @Override
//    public String getName() {
//        return "dodola";
//    }
//
//
//    @Override
//    Set<QualifiedContent.ContentType> getInputTypes() {
//        return TransformManager.CONTENT_CLASS
//    }
//
//    @Override
//    Set<QualifiedContent.Scope> getScopes() {
//        return TransformManager.SCOPE_FULL_PROJECT
//    }
//
//    @Override
//    public boolean isIncremental() {
//        return false;
//    }
//
//    @Override
//    void transform(Context context, Collection<TransformInput> inputs, Collection<TransformInput> referencedInputs,
//                   TransformOutputProvider outputProvider, boolean isIncremental) throws IOException, TransformException, InterruptedException {
//        def buildAndFlavor = context.path.split('transformClassesWithDodolaFor')[1];
//
////        println("===========:" + context.path+","+)
//        def bootclassPath = ""
//
//        project.android.bootClasspath.each {
//            if (project.android.bootClasspath.size() > 1) {
//                bootclassPath += "${it.absolutePath}:"
//            } else {
//                bootclassPath = it.absolutePath;
//            }
//        }
////        def version = project.android.defaultConfig.versionCode
////        def rocooFixRootDir = new File("${project.projectDir}${File.separator}rocoofix${File.separator}version" + version)
//////project/rocoofix/version11
////        def outputDir = new File("${rocooFixRootDir}${File.separator}${buildAndFlavor}")
//////project/rocoofix/version11/debug
////        def patchDir = new File("${outputDir}${File.separator}patch")
////        def patchPreDir = new File("${outputDir}${File.separator}patchpre")
////
//////project/rocoofix/version11/debug/patch
////        def hashFile = new File(outputDir, "${HASH_TXT}")//project/rocoofix/version11/debug/hash.txt
////        println("=========" + rocooFixRootDir);
////        println("=========" + outputDir);
////        println("=========" + patchDir);
////        println("=========" + hashFile);
////        if (!rocooFixRootDir.exists()) {
////            rocooFixRootDir.mkdirs();
////        }
////        if (!outputDir.exists()) {
////            outputDir.mkdirs();
////        }
////        if (!patchDir.exists()) {
////            patchDir.mkdirs();
////        }
//
//
//        inputs.each {
//            println("-------------------" + it.directoryInputs)
//            it.directoryInputs.each {
//                println("=================" + it.file)
//                def inputDir = it.file.absolutePath;
//
//                def outDir = outputProvider.getContentLocation(it.name, outputTypes, scopes, Format.DIRECTORY)
//
//                IncrementalSupportVisitor.mainMe(inputDir, outDir.absolutePath, bootclassPath)
//
//
//            }
//            //TODO:jar包插桩
//            it.jarInputs.each {
//                def jarOutDir = outputProvider.getContentLocation(it.name, outputTypes, scopes, Format.JAR);
//                FileUtils.mkdirs(jarOutDir.getParentFile())
//                println("------jarOutDir:" + jarOutDir + ",inputDir:" + it.file)
//                Files.copy(it.file, jarOutDir)
//            }
//        }
//    }


    private void applyTask(Project project, DomainObjectCollection<BaseVariant> variants) {

        project.afterEvaluate {
            variants.all { variant ->
                def preDexTask = project.tasks.findByName(RocooUtils.getPreDexTaskName(project, variant))
                def dexTask = project.tasks.findByName(RocooUtils.getDexTaskName(project, variant))
                def proguardTask = project.tasks.findByName(RocooUtils.getProGuardTaskName(project, variant))

//                println(ReflectionToStringBuilder.toString(dexTask.inputs.files.each, RecursiveToStringStyle.MULTI_LINE_STYLE));


                applyMapping(project, variant, proguardTask)

                def dirName = variant.dirName

                def rocooFixRootDir = new File("${project.projectDir}${File.separator}rocoofix${File.separator}version" + variant.getVersionCode())//project/rocoofix/version11
                def outputDir = new File("${rocooFixRootDir}${File.separator}${dirName}")//project/rocoofix/version11/debug
                def patchDir = new File("${outputDir}${File.separator}patch")//project/rocoofix/version11/debug/patch
                def hashFile = new File(outputDir, "${HASH_TXT}")//project/rocoofix/version11/debug/hash.txt
                def patchPreDir = new File("${outputDir}${File.separator}patchpre")

                if (!rocooFixRootDir.exists()) {
                    rocooFixRootDir.mkdirs();
                }
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                if (!patchDir.exists()) {
                    patchDir.mkdirs();
                }
                def rocooPatchTaskName = "applyRocoo${variant.name.capitalize()}Patch"
                project.task(rocooPatchTaskName) << {
                    if (patchDir) {
                        RocooUtils.makeDex(project, patchDir)
                    }
                }
                def rocooPatchTask = project.tasks[rocooPatchTaskName]

                Closure prepareClosure = {
                    if (!hashFile.exists()) {
                        hashFile.createNewFile()
                    } else {
                        hashFile.delete()
                        hashFile.createNewFile()
                    }
                }

                Closure copyMappingClosure = {

                    if (proguardTask) {
                        def mapFile = new File("${project.buildDir}${File.separator}outputs${File.separator}mapping${File.separator}${variant.dirName}${File.separator}mapping.txt")
                        if (mapFile.exists()) {

                            def newMapFile = new File("${rocooFixRootDir}${File.separator}${dirName}${File.separator}mapping.txt");
                            org.apache.commons.io.FileUtils.copyFile(mapFile, newMapFile)
                        }
                    }
                }


                def rocooJarBeforeDex = "rocooJarBeforeDex${variant.name.capitalize()}"
                project.task(rocooJarBeforeDex) << {


                    Set<File> inputFiles = RocooUtils.getDexTaskInputFiles(project, variant, dexTask)


                    def bootclassPath = ""

                    inputFiles.each {
                        bootclassPath += "${it.absolutePath}:"
                    }
                    project.android.bootClasspath.each {
                        bootclassPath += "${it.absolutePath}:"
                    }
                    if (bootclassPath.endsWith(":")) {
                        bootclassPath = bootclassPath.substring(0, bootclassPath.length() - 1)
                    }

                    println("__________" + bootclassPath)


                    Map hashMap
                    RocooFixExtension rocooConfig = RocooFixExtension.getConfig(project);
                    if (rocooConfig.preVersionPath != null) {
                        def preVersionPath = new File("${project.projectDir}${File.separator}rocoofix${File.separator}version" + rocooConfig.preVersionPath)
                        if (preVersionPath.exists()) {
                            def preHashFile = new File("${preVersionPath}${File.separator}${variant.dirName}${File.separator}${HASH_TXT}")
                            hashMap = RocooUtils.parseMap(preHashFile)
                        }
                    }
                    inputFiles.each { inputFile ->
                        println("***********" + inputFile)
                        def inputPath = inputFile.absolutePath

                        if (inputPath.endsWith(com.android.SdkConstants.DOT_JAR)) {
//混淆后会生成一个main.jar,解压之
                            def file = new JarFile(inputFile);
                            Enumeration enumeration = file.entries();
                            def unzipDir = new File("${outputDir}${File.separator}unzipjar")

                            def finalJarOutput = new File("${outputDir}${File.separator}finaljar")

//project/rocoofix/version11/debug/patch

                            if (unzipDir.exists()) {
                                FileUtils.deleteFolder(unzipDir)
                                unzipDir.mkdirs()
                            }

                            if (patchDir.exists()) {
                                FileUtils.deleteFolder(patchDir)
                                patchDir.mkdirs()
                            }

                            if (patchPreDir.exists()) {
                                FileUtils.deleteFolder(patchPreDir)
                                patchPreDir.mkdirs()
                            }

                            def changedFiles = new ArrayList<String>()
                            while (enumeration.hasMoreElements()) {
                                JarEntry jarEntry = (JarEntry) enumeration.nextElement();
                                String entryName = jarEntry.getName();
                                InputStream inputStream = file.getInputStream(jarEntry);
                                def hash = DigestUtils.shaHex(inputStream)
                                hashFile.append(RocooUtils.format(entryName, hash))

                                if (jarEntry.isDirectory()) {
                                    continue;
                                }
                                File outFileName = new File("${unzipDir.absolutePath}/${jarEntry.getName()}");

                                if (!outFileName.parentFile.exists()) {
                                    outFileName.parentFile.mkdirs()
                                }

                                writeFile(file.getInputStream(jarEntry), (outFileName));

                                if (hashMap != null) {
                                    if (RocooUtils.notSame(hashMap, entryName, hash)) {
                                        File diffFile = new File("${patchPreDir.absolutePath}/${jarEntry.getName()}");
                                        if (!diffFile.parentFile.exists()) {
                                            diffFile.parentFile.mkdirs()
                                        }
                                        changedFiles.add(entryName.substring(0, entryName.length() - ".class".length()).replace(File.separatorChar as String, '.'))
//保存列表生成patchloader
                                        writeFile(file.getInputStream(jarEntry), (diffFile));
                                    }
                                }
                            }

                            if (hashMap != null) {
                                IncrementalChangeVisitor.main([patchPreDir.absolutePath, patchDir.absolutePath, bootclassPath] as String[])
                                InstantRunTransform.writePatchFileContents(changedFiles, patchDir as File)
                            }
                            IncrementalSupportVisitor.mainMe(unzipDir.absolutePath, finalJarOutput.absolutePath, bootclassPath)
                            //重新打包成jar,删除旧的

                            inputFile.delete();
                            JARCompress.doIt(finalJarOutput.absolutePath, inputFile.absolutePath)


                        } else if (inputFile.isDirectory()) {//不开混淆的处理 暂不开放TODO:
//                            def outDir = inputFile
//                            FileWriter hashWriter = new FileWriter(hashFile, false)
//                            Iterable<File> files =
//                                    Files.fileTreeTraverser().preOrderTraversal(outDir).filter(Files.isFile());
//                            for (File classFile : files) {
//                                //计算hash
//                                def hash = DigestUtils.shaHex(new FileInputStream(classFile));
//                                def path = classFile.getAbsolutePath().split("${outDir}/")[1]
//                                hashWriter.append(RocooUtils.format(path, hash))
//                                if (hashMap != null) {
//                                    if (path.endsWith(".class") && !path.contains("/R\$") && !path.endsWith("/R.class") && !path.endsWith("/BuildConfig.class")) {
//                                        if (RocooUtils.notSame(hashMap, path, hash)) {
//                                            //拷贝到补丁包里
//
//                                            def changeFile = new File("${patchPreDir}${File.separator}${path}")
//                                            println("===================copy===============" + classFile + ",patchDir:" + patchPreDir + ",changeFile:" + changeFile)
//
//                                            changeFile.getParentFile().mkdirs()
////                                FileUtils.copy(classFile, changeFile.getParentFile())
//                                            Files.copy(classFile, changeFile)
//                                        }
//                                    }
//                                }
//                            }
//                            hashWriter.close()
//                            if (hashMap != null) {
//                                IncrementalChangeVisitor.main([patchPreDir, patchDir, bootclassPath] as String[])
//                            }


                        }
                    }
                }
                def rocooJarBeforeDexTask = project.tasks[rocooJarBeforeDex]

                rocooJarBeforeDexTask.dependsOn dexTask.taskDependencies.getDependencies(dexTask)
                rocooJarBeforeDexTask.doFirst(prepareClosure)
                rocooJarBeforeDexTask.doLast(copyMappingClosure)
                rocooPatchTask.dependsOn rocooJarBeforeDexTask
                dexTask.dependsOn rocooPatchTask
            }
        }
    }

    private static void writeFile(InputStream ips, File outputFile) throws IOException {
        OutputStream ops = new BufferedOutputStream(new FileOutputStream(outputFile));
        try {
            byte[] buffer = new byte[1024];
            int nBytes = 0;
            while ((nBytes = ips.read(buffer)) > 0) {
                ops.write(buffer, 0, nBytes);
            }
        } catch (IOException ioe) {
            throw ioe;
        } finally {
            try {
                if (null != ops) {
                    ops.flush();
                    ops.close();
                }
            } catch (IOException ioe) {
                throw ioe;
            } finally {
                if (null != ips) {
                    ips.close();
                }
            }
        }
    }


    private static void applyMapping(Project project, BaseVariant variant, Task proguardTask) {

        RocooFixExtension rocooConfig = RocooFixExtension.getConfig(project);
        if (rocooConfig.preVersionPath != null) {

            def preVersionPath = new File("${project.projectDir}${File.separator}rocoofix${File.separator}version" + rocooConfig.preVersionPath)
//project/rocoofix/version11

            if (preVersionPath.exists()) {
                def mappingFile = new File("${preVersionPath}${File.separator}${variant.dirName}${File.separator}${MAPPING_TXT}")
                if (mappingFile.exists()) {
                    if (proguardTask instanceof ProGuardTask) {
                        if (mappingFile.exists()) {
                            proguardTask.applymapping(mappingFile)
                        }
                    } else {//兼容gradle1.4 增加了transformapi
                        def manager = variant.variantData.getScope().transformManager;
                        def proguardTransform = manager.transforms.find {
                            it.class.name == ProGuardTransform.class.name
                        };
                        if (proguardTransform) {
                            proguardTransform.configuration.applyMapping = mappingFile
                        }
                    }
                }
            }
        }
    }
}