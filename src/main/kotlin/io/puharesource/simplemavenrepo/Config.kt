package io.puharesource.simplemavenrepo

import java.io.File

data class Config(
        val port: Int = 8234,
        val title: String = "Simple Maven Repository",
        val storagePath: String = "repository",
        val extraRepoPaths: Array<String> = arrayOf("repository")) {
    companion object {
        fun loadConfig(configFile: File): Config {
            if (!configFile.exists()) {
                configFile.createNewFile()

                configFile.writeText(gson.toJson(Config()))
            }

            return gson.fromJson(configFile.readText(), Config::class.java)
        }
    }
}