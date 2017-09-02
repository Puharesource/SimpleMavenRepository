package io.puharesource.simplemavenrepo

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import spark.Spark.*
import spark.staticfiles.MimeType
import java.io.File

internal val gson : Gson = GsonBuilder().setPrettyPrinting().create()
internal val mimeTypes : MutableMap<String, String> by lazy {
    val field = MimeType::class.java.getDeclaredField("mappings")
    field.isAccessible = true

    return@lazy field.get(null) as MutableMap<String, String>
}

fun main(args: Array<String>) {
    // Load config file
    val config = Config.loadConfig(File("config.json"))

    // Create directories / files
    val repositoryDirectory = File(config.storagePath)
    val tmpDirectory = File("tmp")
    val usersFile = File("users.json")
    val defaultPomFile = File("default_pom.xml")

    if (!repositoryDirectory.exists()) {
        repositoryDirectory.mkdirs()
    }

    if (!tmpDirectory.exists()) {
        repositoryDirectory.mkdirs()
    }

    if (!defaultPomFile.exists()) {
        defaultPomFile.createNewFile()

        defaultPomFile.writeText(Authentication::class.java.getResourceAsStream("/default_pom.xml").bufferedReader().readText())
    }

    port(config.port)

    Routes.registerRoutes(
            config = config,
            authentication = Authentication(usersFile),
            tmpDirectory = tmpDirectory,
            repositoryDirectory = repositoryDirectory,
            adminCss = Authentication::class.java.getResourceAsStream("/admin.css").bufferedReader().readText(),
            defaultPom = defaultPomFile.readText()
    )
}