package com.example.thestudentapp.network

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.thestudentapp.models.ContentModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.util.Base64

class Client(private val serverIp: String, private val serverPort: Int) {

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null

    // Connect to the TCP server
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun connect(studentID: String) {
        withContext(Dispatchers.IO) {
            try {
                socket = Socket(serverIp, serverPort)
                reader = BufferedReader(InputStreamReader(socket?.getInputStream()))
                writer = socket?.getOutputStream()?.let { PrintWriter(it, true) }
                Log.d("TcpClient", "Connected to server")

                withContext(Dispatchers.IO){
                    try{
                        writer?.println(studentID)
                        Log.d("Tcp Client", "Student ID sent: $studentID")

                        val challenge = reader?.readLine()
                        if(challenge != null){
                            Log.d("Tcp Server", "Challenged received: $challenge")
                            val seed = hashStrSha256(studentID)
                            val aesKey = generateAESKey(seed)
                            val aesIv = generateIV(seed)

                            val encryptedText = encryptMessage(challenge,aesKey,aesIv)
                            writer?.println(encryptedText)
                            Log.d("Tcp Client","Encrypted text sent: $encryptedText")
                        }else{
                            Log.e("Tcp Client", "Challenge not received")
                        }
                    }catch (e:Exception){
                        Log.d("Tcp Client", "ERROR")
                    }
                }
            } catch (e: Exception) {
                Log.e("TcpClient", "Error connecting to server", e)
            }
        }
    }

    suspend fun sendMessage(message: String) {
        withContext(Dispatchers.IO) {
            try {
                writer?.println(message)
                writer?.flush()
                Log.d("TcpClient", "Message sent: $message")
            } catch (e: Exception) {
                Log.e("TcpClient", "Error sending message", e)
            }
        }
    }

    // Disconnect from the server
    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                socket?.close()
                Log.d("TcpClient", "Disconnected from server")
            } catch (e: Exception) {
                Log.e("TcpClient", "Error disconnecting", e)
            }
        }
    }

    // Encryption and decryption functions (similar to the server)
    @RequiresApi(Build.VERSION_CODES.O)
    private fun encryptMessage(plaintext: String, aesKey: SecretKeySpec, aesIv: IvParameterSpec): String {
        val plainTextByteArr = plaintext.toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv)

        val encrypted = cipher.doFinal(plainTextByteArr)
        return Base64.getEncoder().encodeToString(encrypted)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun decryptMessage(encryptedText: String, aesKey: SecretKeySpec, aesIv: IvParameterSpec): String {
        val textToDecrypt = Base64.getDecoder().decode(encryptedText)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, aesIv)

        val decrypted = cipher.doFinal(textToDecrypt)
        return String(decrypted)
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
}