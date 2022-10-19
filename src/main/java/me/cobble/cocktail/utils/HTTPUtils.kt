package me.cobble.cocktail.utils

import org.bukkit.Bukkit
import java.io.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


object HTTPUtils {
    private val DATAPACK_PATH = "${Bukkit.getServer().getWorld("world")!!.worldFolder}/datapacks/"

    private val client = HttpClient
        .newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()

    fun getDatapacks() {
        val uri = URI.create(Config.getString("datapack-url"))
        val request = HttpRequest.newBuilder().uri(uri).build()

        val stream = client.sendAsync(request, BodyHandlers.ofInputStream())
            .thenApply { obj: HttpResponse<InputStream> -> obj.body() }.join()

        FileOutputStream("$DATAPACK_PATH/pack.zip").use { out -> stream.transferTo(out) }

        fixZip()

        Bukkit.getServer().reloadData()
    }

    private fun fixZip() {
        val fileZip = "$DATAPACK_PATH/pack.zip"
        val destDir = File("$DATAPACK_PATH/pack-temp/")

        val buffer = ByteArray(4096)
        val zis = ZipInputStream(FileInputStream(fileZip))
        var zipEntry = zis.nextEntry
        while (zipEntry != null) {
            val newFile: File = newFile(destDir, zipEntry)
            if (zipEntry.isDirectory) {
                if (!newFile.isDirectory && !newFile.mkdirs()) {
                    throw IOException("Failed to create directory $newFile")
                }
            } else {
                // fix for Windows-created archives
                val parent = newFile.parentFile
                if (!parent.isDirectory && !parent.mkdirs()) {
                    throw IOException("Failed to create directory $parent")
                }

                // write file content
                val fos = FileOutputStream(newFile)
                var len: Int
                while (zis.read(buffer).also { len = it } > 0) {
                    fos.write(buffer, 0, len)
                }
                fos.close()
            }
            zipEntry = zis.nextEntry
        }

        zis.closeEntry()
        zis.close()

        val path = File("$DATAPACK_PATH/pack-temp/datapack-main")
        val packPath = File("$DATAPACK_PATH/pack")

        if (packPath.exists()) {
            packPath.deleteRecursively()
        }

        Files.move(path.toPath(), packPath.toPath())

        if (destDir.isDirectory && destDir.exists()) {
            destDir.deleteRecursively()
        }
        File(fileZip).delete()

    }

    private fun newFile(destinationDir: File, zipEntry: ZipEntry): File {
        val destFile = File(destinationDir, zipEntry.name)
        val destDirPath = destinationDir.canonicalPath
        val destFilePath = destFile.canonicalPath
        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw IOException("Entry is outside of the target dir: " + zipEntry.name)
        }
        return destFile
    }
}
