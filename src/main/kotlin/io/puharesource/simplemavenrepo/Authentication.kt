package io.puharesource.simplemavenrepo

import com.github.salomonbrys.kotson.gsonTypeToken
import spark.Request
import spark.Response
import spark.Spark
import java.io.File
import java.nio.charset.Charset
import java.util.*

class Authentication(usersFile: File) {
    val users: MutableMap<String, String>

    init {
        if (!usersFile.exists()) {
            usersFile.createNewFile()
            usersFile.writeText("{}")
        }

        users = gson.fromJson<MutableMap<String, String>>(usersFile.readText(), gsonTypeToken<MutableMap<String, String>>())
    }

    fun handle(request: Request, response: Response) {
        if (!request.isAuthenticated()) {
            response.header("WWW-Authenticate", "Basic")
            Spark.halt(401)
        }
    }

    private fun getEncodedHeader(request: Request): String? = request.headers("Authorization")?.substringAfter("Basic ")

    private fun decodeHeader(encodedHeader: String): String = Base64.getDecoder().decode(encodedHeader).toString(Charset.defaultCharset())

    private fun getCredentialsFromHeader(header: String): Pair<String, String>? {
        val credentials = header.split(":")

        if (credentials.size != 2) {
            return null
        }

        return credentials[0] to credentials[1]
    }

    private fun isAuthenticated(credentials: Pair<String, String>): Boolean = users[credentials.first] == credentials.second

    private fun Request.isAuthenticated(): Boolean {
        val encodedHeader = getEncodedHeader(this) ?: return false
        val credentials = getCredentialsFromHeader(decodeHeader(encodedHeader)) ?: return false

        return isAuthenticated(credentials)
    }
}