package com.example.oncalldoc

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ChatActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var toolbar: Toolbar

    private val messages = mutableListOf<ChatMessage>()

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var chatRoomId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val doctorUid = intent.getStringExtra("DOCTOR_UID")
        val doctorName = intent.getStringExtra("DOCTOR_NAME")
        val patientUid = auth.currentUser?.uid

        if (doctorUid == null || patientUid == null) {
            Toast.makeText(this, "Error: Could not start chat.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        chatRoomId = if (patientUid < doctorUid) {
            "${patientUid}_${doctorUid}"
        } else {
            "${doctorUid}_${patientUid}"
        }

        toolbar = findViewById(R.id.chat_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = doctorName ?: "Chat"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        chatRecyclerView = findViewById(R.id.chat_recycler_view)
        messageInput = findViewById(R.id.message_input)
        sendButton = findViewById(R.id.send_button)

        chatAdapter = ChatAdapter(messages)
        val layoutManager = LinearLayoutManager(this)
        chatRecyclerView.layoutManager = layoutManager
        chatRecyclerView.adapter = chatAdapter

        listenForMessages()

        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText, patientUid)
            }
        }
    }

    private fun sendMessage(text: String, senderId: String) {
        val chatMessage = ChatMessage(
            text = text,
            senderId = senderId,
            timestamp = System.currentTimeMillis()
        )

        chatRoomId?.let {
            firestore.collection("chats").document(it).collection("messages")
                .add(chatMessage)
                .addOnSuccessListener {
                    messageInput.text.clear()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun listenForMessages() {
        chatRoomId?.let {
            firestore.collection("chats").document(it).collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        return@addSnapshotListener
                    }

                    messages.clear()
                    snapshots?.forEach { doc ->
                        val message = doc.toObject(ChatMessage::class.java)
                        messages.add(message)
                    }
                    chatAdapter.notifyDataSetChanged()
                    chatRecyclerView.scrollToPosition(messages.size - 1)
                }
        }
    }
}
