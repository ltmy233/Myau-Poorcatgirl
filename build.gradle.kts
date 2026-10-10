import org.apache.commons.lang3.SystemUtils
plugins {
    idea
    java
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}
// 常量
val baseGroup: String by project
val mcVersion: String by project
val version: String by project
val mixinGroup = "$baseGroup.mixin"
val modid: String by project
val jarName: String by project
val transformerFile = file("src/main/resources/accesstransformer.cfg")
// JDK 版本
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}
// MC 相关配置
loom {
    log4jConfigs.from(file("log4j2.xml"))
    launchConfigs {
        "client" {
            // 不用 mixin 的话把这三行删了
            property("mixin.debug", "true")
            arg("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
        }
    }
    runConfigs {
        "client" {
            if (SystemUtils.IS_OS_MAC_OSX) {
                // macOS 上这个参数会崩，得去掉
                vmArgs.remove("-XstartOnFirstThread")
            }
        }
        remove(getByName("server"))
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        // 不用 mixin 的话把这行删了
        mixinConfig("mixins.$modid.json")
	    if (transformerFile.exists()) {
			println("Installing access transformer")
		    accessTransformer(transformerFile)
	    }
    }
    // 不用 mixin 的话把这几行删了
    mixin {
        defaultRefmapName.set("mixins.$modid.refmap.json")
    }
}
sourceSets.main {
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
}
// 依赖
repositories {
    maven("https://maven.aliyun.com/repository/central")
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
    // 不想用正版账号登录测试的话，把这行删了
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}
val shadowImpl: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}
dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")
    // 不用 mixin 的话把这几行删了
    shadowImpl("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        isTransitive = false
    }
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")
    // 不想用正版账号登录测试的话，把这行删了
    runtimeOnly("me.djtheredstoner:DevAuth-forge-legacy:1.2.1")
}
// Essential 只在编译时用到，给 MixinGameProfileManager 那个兼容混入用
val essentialForCompile: File? = sequenceOf(
    file("libs/essential-compile.jar"),
    *fileTree("D:/PCL/PCL/versions") { include("**/essential/Essential*.jar") }.files.toTypedArray()
).firstOrNull { it.isFile }
if (essentialForCompile != null) {
    dependencies { compileOnly(files(essentialForCompile)) }
} else {
    println("WARNING: Essential jar not found, compat mixin will not compile")
}
// Sidebar Revamp 只在编译时用到，给 MixinGuiSidebar 的记分板圆角用
if (file("libs/sidebar-revamp.jar").isFile) {
    dependencies { compileOnly(files("libs/sidebar-revamp.jar")) }
} else {
    println("WARNING: sidebar-revamp.jar not found, MixinGuiSidebar will not compile")
}
// 任务
tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
}
tasks.withType(org.gradle.jvm.tasks.Jar::class) {
    archiveBaseName.set(jarName)
    manifest.attributes.run {
        this["FMLCorePluginContainsFMLMod"] = "true"
        this["ForceLoadAsMod"] = "true"
        // 不用 mixin 的话把这几行删了
        this["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
        this["MixinConfigs"] = "mixins.$modid.json"
	    if (transformerFile.exists())
			this["FMLAT"] = "${modid}_at.cfg"
    }
}
tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mcversion", mcVersion)
    inputs.property("modid", modid)
    inputs.property("basePackage", baseGroup)
    filesMatching(listOf("mcmod.info", "mixins.$modid.json","version.json")) {
        expand(inputs.properties)
    }
    rename("accesstransformer.cfg", "META-INF/${modid}_at.cfg")
}
val remapJar by tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveClassifier.set("")
    archiveVersion.set("")
    from(tasks.shadowJar)
    input.set(tasks.shadowJar.get().archiveFile)
}
tasks.jar {
    archiveClassifier.set("without-deps")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}
tasks.shadowJar {
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    archiveClassifier.set("non-obfuscated-with-deps")
    configurations = listOf(shadowImpl)
    doLast {
        configurations.forEach {
            println("Copying dependencies into mod: ${it.files}")
        }
    }
    // 以后要打包别的依赖进来，relocate 写这儿
    fun relocate(name: String) = relocate(name, "$baseGroup.deps.$name")
}
tasks.assemble.get().dependsOn(tasks.remapJar)
