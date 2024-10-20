package com.example.thestudentapp

import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.thestudentapp.chatlist.ChatListAdapter
import com.example.thestudentapp.models.ContentModel
import com.example.thestudentapp.network.Client
import com.example.thestudentapp.network.NetworkMessageInterface
import com.example.thestudentapp.peerlist.PeerListAdapter
import com.example.thestudentapp.peerlist.PeerListAdapterInterface
import com.example.thestudentapp.wifidirect.WifiDirectInterface
import com.example.thestudentapp.wifidirect.WifiDirectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CommunicationActivity : AppCompatActivity(), WifiDirectInterface, PeerListAdapterInterface, NetworkMessageInterface {

    private var wfdManager: WifiDirectManager? = null

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private var peerListAdapter: PeerListAdapter? = null
    private var chatListAdapter: ChatListAdapter? = null

    private var wfdAdapterEnabled = false
    private var wfdHasConnection = false
    private var hasDevices = false
    private var client: Client? = null
    private var deviceIp: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_communication)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val manager: WifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, mainLooper, null)
        wfdManager = WifiDirectManager(manager, channel, this)

        peerListAdapter = PeerListAdapter(this)
        val rvPeerList: RecyclerView = findViewById(R.id.rvPeerListing)
        rvPeerList.adapter = peerListAdapter
        rvPeerList.layoutManager = LinearLayoutManager(this)

        chatListAdapter = ChatListAdapter()
        val rvChatList: RecyclerView = findViewById(R.id.rvChat)
        rvChatList.adapter = chatListAdapter
        rvChatList.layoutManager = LinearLayoutManager(this)
    }

    override fun onResume() {
        super.onResume()
        wfdManager?.also {
            registerReceiver(it, intentFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        wfdManager?.also {
            unregisterReceiver(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        CoroutineScope(Dispatchers.Main).launch {
            client?.close() // Clean up the client connection
        }
    }

    fun disconnect(view: View) {
        wfdManager?.disconnect()
        updateUI()
    }

    fun discoverNearbyPeers(view: View) {
        val wfdNoConnectionView: ConstraintLayout = findViewById(R.id.clNoWifiDirectConnection)
        val id: EditText = wfdNoConnectionView.findViewById(R.id.etStudentID)
        val studentID = id.text.toString().trim()

        if (isValidStudentID(studentID)) {
            wfdManager?.discoverPeers()
            Toast.makeText(this, "Searching...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Invalid ID", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI() {
        val wfdAdapterErrorView: ConstraintLayout = findViewById(R.id.clWfdAdapterDisabled)
        wfdAdapterErrorView.visibility = if (!wfdAdapterEnabled) View.VISIBLE else View.GONE

        val wfdNoConnectionView: ConstraintLayout = findViewById(R.id.clNoWifiDirectConnection)
        wfdNoConnectionView.visibility = if (wfdAdapterEnabled && !wfdHasConnection) View.VISIBLE else View.GONE

        val rvPeerList: RecyclerView = findViewById(R.id.rvPeerListing)
        rvPeerList.visibility = if (wfdAdapterEnabled && !wfdHasConnection && hasDevices) View.VISIBLE else View.GONE

        val wfdConnectedView: ConstraintLayout = findViewById(R.id.clHasConnection)
        wfdConnectedView.visibility = if (wfdHasConnection) View.VISIBLE else View.GONE
    }

    fun sendMessage(view: View) {
        val etMessage: EditText = findViewById(R.id.etMessage)
        val etString = etMessage.text.toString()

        // Check if the message is not empty before proceeding
        if (etString.isNotEmpty()) {
            val content = ContentModel(etString, deviceIp) // Create content model
            etMessage.text.clear()

            CoroutineScope(Dispatchers.IO).launch { // Use IO context for network operation
                try {
                    client?.sendMessage(content) // Use the updated sendMessage method
                    runOnUiThread {
                        chatListAdapter?.addItemToEnd(content) // Update chat list
                        updateUI() // Refresh UI after sending
                    }
                } catch (e: Exception) {
                    Log.e("CommunicationActivity", "Failed to send message", e)
                    runOnUiThread {
                        Toast.makeText(this@CommunicationActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            // Optionally show a message to the user if the input is empty
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onWiFiDirectStateChanged(isEnabled: Boolean) {
        wfdAdapterEnabled = isEnabled
        val text = if (isEnabled) {
            "WiFi Direct is enabled!"
        } else {
            "WiFi Direct is disabled! Try turning on the WiFi adapter."
        }

        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        updateUI()
    }

    override fun onPeerListUpdated(deviceList: Collection<WifiP2pDevice>) {
        Toast.makeText(this, "Updated listing of nearby WiFi Direct devices", Toast.LENGTH_SHORT).show()
        hasDevices = deviceList.isNotEmpty()
        peerListAdapter?.updateList(deviceList)
        updateUI()
    }

    override fun onDeviceStatusChanged(thisDevice: WifiP2pDevice) {
        Toast.makeText(this, "Device parameters have been updated", Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPeerClicked(peer: WifiP2pDevice) {
        val wfdNoConnectionView: ConstraintLayout = findViewById(R.id.clNoWifiDirectConnection)
        val id: EditText = wfdNoConnectionView.findViewById(R.id.etStudentID)
        val studentID = id.text.toString().trim()

        wfdManager?.connectToPeer(peer)

        // Use peer.deviceAddress to get the device's address
        val serverIp = peer.deviceAddress // Use the correct property for the peer's address
        val serverPort = 12345 // Keep this as an Int, but check if Client requires a String

        // Initialize the client with the server's IP, port, and studentID
        client = Client( serverIp, serverPort, studentID, this ) // Convert port to String if necessary

        // Connect to the server
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client?.sendInitialMessage() // Ensure sendInitialMessage is defined in Client
                runOnUiThread {
                    Toast.makeText(this@CommunicationActivity, "Connected to the server", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@CommunicationActivity, "Connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        wfdHasConnection = true
        updateUI()
    }

    override fun onContent(content: ContentModel) {
        runOnUiThread {
            chatListAdapter?.addItemToEnd(content)
        }
        updateUI()
    }

    private fun isValidStudentID(studentID: String): Boolean {
        return try {
            val id = studentID.toInt()
            id in 816000000..816999999
        } catch (e: NumberFormatException) {
            false
        }
    }
}
