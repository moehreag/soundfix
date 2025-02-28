plugins {
	id("maven-publish")
	id("fabric-loom") version "1.10-SNAPSHOT"
	id("ploceus") version "1.10-SNAPSHOT"
}

base {
	archivesName = "soundfix"
}
version = "${project.version}+mc${project.property("minecraft_version")}"
group = project.property("maven_group")!!

repositories {
	maven ("https://moehreag.duckdns.org/maven/releases")
}

dependencies {
	minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
	mappings("net.fabricmc:yarn:${project.property("minecraft_version")}+build.${project.property("yarn_build")}")

	modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fapi")}")
    implementation(include("org.jcraft:jorbis:0.0.17")!!)
}

tasks.processResources {
    inputs.property("version", version)

    filesMatching("fabric.mod.json") {
        expand("version" to version)
    }
}

tasks.withType(JavaCompile::class).configureEach {
    options.encoding = "UTF-8"

    if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_18)) {
        options.release = 17
    }
}

java {
	// Still required by IDEs such as Eclipse and Visual Studio Code
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17

	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	// If this mod is going to be a library, then it should also generate Javadocs in order to aid with development.
	// Uncomment this line to generate them.
	// withJavadocJar()
}

// If you plan to use a different file for the license, don't forget to change the file name here!
tasks.getByName("jar", Jar::class) {
    from("LICENSE") {
        rename("^(LICENSE.*?)(\\..*)?$", "\$1_${archiveBaseName}\$2")
    }
}

tasks.runClient {
    // might not be set
    if (project.properties["native_glfw"] == "true") {
        val glfwPath = project.properties.getOrDefault("native_glfw_path", "/usr/lib/libglfw.so")
        jvmArgs("-Dorg.lwjgl.glfw.libname=$glfwPath")
    }
    classpath(sourceSets.getByName("test").runtimeClasspath)
}

// Configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = base.archivesName.get()
            from(components["java"])
        }
    }

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
