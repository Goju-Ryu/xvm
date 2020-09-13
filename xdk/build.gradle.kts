/*
 * Build files for the XDK.
 */

val ecstasy      = project(":ecstasy")
val javatools    = project(":javatools")
val bridge       = project(":javatools_bridge")
val json         = project(":lib_json");
val jsondb       = project(":lib_jsondb");
val oodb         = project(":lib_oodb");

val ecstasyMain  = "${ecstasy.projectDir}/src/main"
val bridgeMain   = "${bridge.projectDir}/src/main"
val javatoolsJar = "${javatools.buildDir}/libs/javatools.jar"
val jsonMain     = "${json.projectDir}/src/main";
val jsondbMain   = "${jsondb.projectDir}/src/main";
val oodbMain     = "${oodb.projectDir}/src/main";

tasks.register("clean") {
    group       = "Build"
    description = "Delete previous build results"
    delete("$buildDir")
}

val copyOutline = tasks.register<Copy>("copyOutline") {
    from("$projectDir/src/main/resources") {
        include("xdk/**")
    }
    into("$buildDir")
    doLast {
        println("Finished task: copyOutline")
    }
}

val copyJavatools = tasks.register<Copy>("copyJavatools") {
    from(javatoolsJar)
    into("$buildDir/xdk/javatools/")

    dependsOn(javatools.tasks["build"])
    dependsOn(copyOutline)
    doLast {
        println("Finished task: copyJavatools")
    }
}

val compileEcstasy = tasks.register<JavaExec>("compileEcstasy") {
    group       = "Execution"
    description = "Build Ecstasy.xtc and _native.xtc modules"

    dependsOn(javatools.tasks["build"])
    dependsOn(copyJavatools)

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$buildDir/xdk/lib",
            "$ecstasyMain/x/module.x",
            "$bridgeMain/x/module.x")
    main = "org.xvm.tool.Compiler"

    doLast {
        file("$buildDir/xdk/lib/_native.xtc").
           renameTo(file("$buildDir/xdk/javatools/javatools_bridge.xtc"))
        println("Finished task: compileEcstasy")
    }
}

val compileJson = tasks.register<JavaExec>("compileJson") {
    group       = "Execution"
    description = "Build Json.xtc module"

    shouldRunAfter(compileEcstasy)

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$buildDir/xdk/lib",
            "-L", "${buildDir}/xdk/lib/Ecstasy.xtc",
            "-L", "${buildDir}/xdk/javatools/javatools_bridge.xtc",
            "$jsonMain/x/module.x")
    main = "org.xvm.tool.Compiler"
}

val compileOODB = tasks.register<JavaExec>("compileOODB") {
    group       = "Execution"
    description = "Build OODB.xtc module"

    shouldRunAfter(compileEcstasy)

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$buildDir/xdk/lib",
            "-L", "${buildDir}/xdk/lib/Ecstasy.xtc",
            "-L", "${buildDir}/xdk/javatools/javatools_bridge.xtc",
            "$oodbMain/x/module.x")
    main = "org.xvm.tool.Compiler"
}

val compileJsonDB = tasks.register<JavaExec>("compileJsonDB") {
    group       = "Execution"
    description = "Build JsonDB.xtc module"

    shouldRunAfter(compileJson)
    shouldRunAfter(compileOODB)

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$buildDir/xdk/lib",
            "-L", "${buildDir}/xdk/lib/Ecstasy.xtc",
            "-L", "${buildDir}/xdk/javatools/javatools_bridge.xtc",
            "-L", "${buildDir}/xdk/lib/",
            "$jsondbMain/x/module.x")
    main = "org.xvm.tool.Compiler"
}

fun download(url : String, path : String){
    val destFile = File(path)
    destFile.parentFile.mkdirs()
    ant.invokeMethod("get", mapOf("src" to url, "dest" to destFile))
}

tasks.register("build") {
    group       = "Build"
    description = "Build the XDK"

    // we assume that the launcher project has been built
    val launcher         = project(":javatools_launcher")
    val macos_launcher   = "${launcher.buildDir}/exe/macos_launcher"
    val windows_launcher = "${launcher.buildDir}/exe/windows_launcher.exe"

    //TODO replace sourceURL with url to the xtclang releases if merged
    val sourceUrl = "https://github.com/Goju-Ryu/xvm/releases/latest/download/"
    if (!File(macos_launcher).exists()) {
        println("no macos_launcher not found, getting it from source...")
        download("$sourceUrl/macos_launcher", macos_launcher)
    }
    if (!File(windows_launcher).exists()) {
        println("no windows_launcher not found, getting it from source...")
        download("$sourceUrl/windows_launcher.exe", windows_launcher)
    }

    // compile Ecstasy
    val coreSrc = fileTree(ecstasyMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val coreDest = file("$buildDir/xdk/lib/Ecstasy.xtc").lastModified()

    if (coreSrc > coreDest) {
        dependsOn(compileEcstasy)
    } else {
        dependsOn(copyJavatools)
    }

    // compile Json
    val jsonSrc = fileTree(jsonMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val jsonDest = file("$buildDir/xdk/lib/Json.xtc").lastModified()

    if (jsonSrc > jsonDest) {
        dependsOn(compileJson)
        }

    // compile OODB
    val oodbSrc = fileTree(oodbMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val oodbDest = file("$buildDir/xdk/lib/OODB.xtc").lastModified()

    if (oodbSrc > oodbDest) {
        dependsOn(compileOODB)
        }

    // compile Json
    val jsondbSrc = fileTree(jsondbMain).getFiles().stream().
    mapToLong({f -> f.lastModified()}).max().orElse(0)
    val jsondbDest = file("$buildDir/xdk/lib/JsonDB.xtc").lastModified()

    if (jsondbSrc > jsondbDest) {
        dependsOn(compileJsonDB)
    }

    doLast {
        copy {
            from(macos_launcher, windows_launcher)
            into("$buildDir/xdk/bin/")
            }
        println("Finished task: build")
    }

// TODO wiki
// TODO ZIP the resulting xdk directory; e.g. on macOS:
//    `zip -r xdk.zip ./xdk -x *.DS_Store`
}
