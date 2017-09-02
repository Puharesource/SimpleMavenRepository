package io.puharesource.simplemavenrepo

import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Checksum

internal fun File.createMd5ChecksumFile() : File? {
    if (!exists()) {
        return null
    }

    val checksumFile = File(parent, name + ".md5")

    if (!checksumFile.exists()) {
        checksumFile.createNewFile()
        checksumFile.writeText(getMd5Checksum())
    }

    return checksumFile
}

internal fun File.createSha1ChecksumFile() : File? {
    if (!exists()) {
        return null
    }

    val checksumFile = File(parent, name + ".sha1")

    if (!checksumFile.exists()) {
        checksumFile.createNewFile()
        checksumFile.writeText(getSha1Checksum())
    }

    return checksumFile
}

internal fun File.createChecksumFiles() {
    createMd5ChecksumFile()
    createSha1ChecksumFile()
}

internal fun File.getMd5Checksum() : String = DigestUtils.md5Hex(readBytes())
internal fun File.getSha1Checksum() : String = DigestUtils.sha1Hex(readBytes())

// Other

internal fun File.getLastModifiedString() : String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(Date(lastModified()))