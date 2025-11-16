package com.example.oncalldoc

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DoctorHomeActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var onlineSwitch: SwitchMaterial
    private lateinit var ordersCountText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doctor_home)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        onlineSwitch = findViewById(R.id.online_switch)
        ordersCountText = findViewById(R.id.orders_count_text)

        val uid = auth.currentUser?.uid
        if (uid != null) {
            val doctorRef = firestore.collection("users").document(uid)

            // Set initial switch state
            doctorRef.get().addOnSuccessListener {
                val isOnline = it.getBoolean("isOnline") ?: false
                onlineSwitch.isChecked = isOnline
                onlineSwitch.text = if (isOnline) "Online" else "Offline"
            }

            onlineSwitch.setOnCheckedChangeListener { _, isChecked ->
                doctorRef.update("isOnline", isChecked)
                    .addOnSuccessListener {
                        onlineSwitch.text = if (isChecked) "Online" else "Offline"
                        Toast.makeText(this, "Status updated", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { 
                        Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show()
                    }
            }

            // Listen for order count changes
            firestore.collection("orders")
                .whereEqualTo("doctorId", uid)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        return@addSnapshotListener
                    }

                    val count = snapshots?.size() ?: 0
                    ordersCountText.text = "You have $count orders"
                }
        }
    }
}
