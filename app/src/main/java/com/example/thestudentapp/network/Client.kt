package com.example.thestudentapp.network

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.thestudentapp.models.ContentModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.net.Socket
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.util.Base64
import kotlin.concurrent.thread

class Client(
    private val serverIp: String,
    private val serverPort: Int,
    private val studentID: String,  // Student ID as a property
    private val networkMessageInterface: NetworkMessageInterface  // To handle message callback
) {

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private var aesKey: SecretKeySpec? = null
    private var aesIv: IvParameterSpec? = null
    var isAuthenticated = false

    init {
        // Establish connection in a separate thread
        thread {
            try {
                socket = Socket(serverIp, serverPort)
                reader = socket?.inputStream?.bufferedReader()
                writer = socket?.outputStream?.bufferedWriter()
                Log.d("TcpClient", "Connected to server")

                // Send initial studentID for challenge-response authentication
                writer?.write("$studentID\n")
                writer?.flush()
                Log.d("Tcp Client", "Student ID sent: $studentID")

                // Listen for server messages
                while (socket?.isConnected == true) {
                    try {
                        val serverResponse = reader?.readLine()
                        if (serverResponse != null) {
                            handleServerResponse(serverResponse)
                        }
                    } catch (e: Exception) {
                        Log.e("TcpClient", "Error receiving message", e)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e("TcpClient", "Error connecting to server", e)
            }
        }
    }

    private fun handleServerResponse(response: String) {
        try {
            val serverContent = Gson().fromJson(response, ContentModel::class.java)
            networkMessageInterface.onContent(serverContent)  // Handle the content via interface
        } catch (e: Exception) {
            Log.e("TcpClient", "Error parsing server response", e)
        }
    }
    suspend fun sendInitialMessage() {
        // Create a message object to send, for example:
        val initialMessage = ContentModel("Student ID: $studentID", serverIp) // Adjust as needed
        sendMessage(initialMessage) // Send the message using your existing sendMessage method
    }

    fun sendMessage(content: ContentModel) {
        thread {
            try {
                val contentAsStr = Gson().toJson(content)
                writer?.write("$contentAsStr\n")
                writer?.flush()
                Log.d("TcpClient", "Message sent: $contentAsStr")
            } catch (e: Exception) {
                Log.e("TcpClient", "Error sending message", e)
            }
        }
    }

    // Encryption and decryption methods
    @RequiresApi(Build.VERSION_CODES.O)
    private fun encryptMessage(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun decryptMessage(encryptedText: String): String {
        val decoded = Base64.getDecoder().decode(encryptedText)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, aesIv)
        val decrypted = cipher.doFinal(decoded)
        return String(decrypted, Charsets.UTF_8)
    }

    private fun hashStrSha256(str: String): String {
        val algorithm = "SHA-256"
        val hashedString = MessageDigest.getInstance(algorithm).digest(str.toByteArray(Charsets.UTF_8))
        return hashedString.joinToString("") { String.format("%02x", it) }
    }

    private fun generateAESKey(seed: String): SecretKeySpec {
        val first32Chars = seed.take(32).padEnd(32, '0')  // Ensure length is 32
        return SecretKeySpec(first32Chars.toByteArray(), "AES")
    }

    private fun generateIV(seed: String): IvParameterSpec {
        val first16Chars = seed.take(16).padEnd(16, '0')  // Ensure length is 16
        return IvParameterSpec(first16Chars.toByteArray())
    }

    fun close() {
        thread {
            try {
                socket?.close()
                Log.d("TcpClient", "Disconnected from server")
            } catch (e: Exception) {
                Log.e("TcpClient", "Error disconnecting", e)
            }
        }
    }
}

