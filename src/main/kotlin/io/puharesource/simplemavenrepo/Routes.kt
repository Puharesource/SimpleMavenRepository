package io.puharesource.simplemavenrepo

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.apache.commons.io.FileUtils
import org.xml.sax.InputSource
import spark.Spark.*
import java.io.File
import javax.servlet.MultipartConfigElement
import javax.servlet.http.Part
import javax.xml.parsers.DocumentBuilderFactory

object Routes {
    fun registerRoutes(
            config: Config,
            authentication: Authentication,
            tmpDirectory: File,
            repositoryDirectory: File,
            adminCss: String,
            defaultPom: String) {
        get("/") { request, response ->
            authentication.handle(request, response)

            createHTML(prettyPrint = true).html {
                head {
                    title { +config.title }

                    style { +adminCss }
                }

                body {
                    div(classes = "main aligner") {
                        div(classes = "aligner-item") {
                            h2 { +"Users" }
                            table {
                                tr {
                                    th { +"Name" }
                                }

                                for ((key) in authentication.users) {
                                    tr {
                                        td { +key }
                                    }
                                }
                            }

                            h2 { +"Upload" }
                            form(action = "upload", method = FormMethod.post, encType = FormEncType.multipartFormData) {
                                +"Artifact: "
                                input(type = InputType.file, name = "artifact")

                                br()
                                +"POM: "
                                input(type = InputType.file, name = "pom")

                                br()
                                b { +"POM Generator:" }
                                br()
                                br()
                                +"groupId: "
                                input(type = InputType.text, name = "pomGroupId")

                                br()
                                +"artifactId: "
                                input(type = InputType.text, name = "pomArtifactId")

                                br()
                                +"version: "
                                input(type = InputType.text, name = "pomVersion")

                                br()
                                input(type = InputType.submit)
                            }
                        }
                    }

                    footer(classes = "aligner") {
                        div(classes = "aligner-item") {
                            a(href = "https://github.com/Puharesource/SimpleMavenRepository") { +"Simple Maven Repository" }
                            +" by "
                            a(href = "https://github.com/Puharesource") { +"Tarkan Nielsen" }
                            +"."
                        }
                    }
                }
            }
        }

        post("/upload", "multipart/form-data") { request, response ->
            authentication.handle(request, response)

            val multipartConfigElement = MultipartConfigElement(tmpDirectory.path)
            request.raw().setAttribute("org.eclipse.jetty.multipartConfig", multipartConfigElement)

            fun getPart(name: String): Part? = request.raw().getPart(name)
            fun Part.getText(): String? = if (size > 0) inputStream.bufferedReader().readText() else null

            val artifact = getPart("artifact")

            if (artifact == null) {
                response.status(500)
                return@post "No artifact specified"
            }

            var pomText = getPart("pom")?.inputStream?.bufferedReader()?.readText()

            var pomGroupId = getPart("pomGroupId")?.getText()
            var pomArtifactId = getPart("pomArtifactId")?.getText()
            var pomVersion = getPart("pomVersion")?.getText()

            if (pomText != null && pomText.isNotBlank()) {
                val documentFactory = DocumentBuilderFactory.newInstance()

                documentFactory.isIgnoringElementContentWhitespace = true

                val documentBuilder = documentFactory.newDocumentBuilder()
                val document = documentBuilder.parse(InputSource(pomText.byteInputStream()))

                fun useTagValue(tagName: String, body: (String) -> Unit) {
                    document.getElementsByTagName(tagName)
                            ?.item(0)
                            ?.textContent
                            ?.let(body)
                }

                useTagValue("groupId") { pomGroupId = it }
                useTagValue("artifactId") { pomArtifactId = it }
                useTagValue("version") { pomVersion = it }
            } else if (pomGroupId != null || pomArtifactId != null || pomVersion != null) {
                pomText = defaultPom
                        .replace("%groupId", pomGroupId!!)
                        .replace("%artifactId", pomArtifactId!!)
                        .replace("%version", pomVersion!!)
            }

            if (pomGroupId == null) {
                response.status(500)
                return@post "No group id provided!"
            }

            if (pomArtifactId == null) {
                response.status(500)
                return@post "No artifact id provided!"
            }

            if (pomVersion == null) {
                response.status(500)
                return@post "No version provided!"
            }

            val groupPath = repositoryDirectory.resolve(pomGroupId!!.replace('.', '/'))
            val idPath = groupPath.resolve(pomArtifactId!!)
            val versionPath = idPath.resolve(pomVersion!!)
            val artifactPath = versionPath.resolve("$pomArtifactId-$pomVersion.jar")
            val pomPath = versionPath.resolve("$pomArtifactId-$pomVersion.pom")

            versionPath.mkdirs()

            artifactPath.createNewFile()
            pomPath.createNewFile()

            artifactPath.writeBytes(artifact.inputStream.readBytes())
            pomPath.writeText(pomText!!)

            artifactPath.createChecksumFiles()
            pomPath.createChecksumFiles()

            response.redirect("/")
        }

        get("/repo") { _, response -> response.redirect("/repo/") }

        get("/repo/*") { request, response ->
            val relativePath = request.uri().removePrefix("/repo/")
            val file = repositoryDirectory.resolve(relativePath)

            if (!file.exists()) {
                response.status(404)

                return@get "Path not found."
            } else if (file.isFile) {
                response.type(mimeTypes[file.extension])

                return@get file.readBytes()
            } else if (file.isDirectory && (relativePath.isNotEmpty() && !relativePath.endsWith("/"))) {
                response.redirect(request.uri() + "/")
                halt()
            }

            val listedFiles = file.listFiles()
            listedFiles.sortBy { it.name }

            createHTML(prettyPrint = true).html {
                val indexOf = file.path.removePrefix(repositoryDirectory.path).removePrefix(File.separator) + "/"

                head {
                    title("Index of $indexOf")

                    style {
                        +"""
                            th {
                                text-align: left;
                            }

                            table {
                                border-spacing: 5px;
                            }
                        """.trimIndent()
                    }
                }

                body {
                    h1 { +"Index of $indexOf" }

                    hr()

                    table {
                        tr {
                            th { +"Name" }
                            th { +"Last modified" }
                            th { +"Size" }
                        }

                        if (relativePath.isNotBlank()) {
                            tr {
                                td { a(href = "../") { +"../" } }
                                td()
                                td()
                            }
                        }

                        for (listedFile in listedFiles) {
                            val lastModified = listedFile.getLastModifiedString()
                            val trimmedPath = listedFile.path.removePrefix(listedFile.parent).removePrefix(File.separator)

                            val href: String = if (listedFile.isDirectory) {
                                trimmedPath + "/"
                            } else {
                                trimmedPath
                            }

                            tr {
                                td { a(href = href) { +trimmedPath } }
                                td { +lastModified }
                                td { +FileUtils.byteCountToDisplaySize(listedFile.length()) }
                            }
                        }
                    }

                    hr()

                    address {
                        a(href = "https://github.com/Puharesource/SimpleMavenRepository") { +"Simple Maven Repository" }
                        +" by "
                        a(href = "https://github.com/Puharesource") { +"Tarkan Nielsen" }
                        +"."
                    }
                }
            }
        }

        put("/repo/*") { request, response ->
            authentication.handle(request, response)

            val relativePath = request.uri().removePrefix("/repo/")
            val file = repositoryDirectory.resolve(relativePath)

            if (file.exists()) {
                file.delete()
            }

            file.parentFile.mkdirs()
            file.createNewFile()
            file.writeBytes(request.bodyAsBytes())
        }
    }
}