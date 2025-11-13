package com.example.oncalldoc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CreatePatientProfileActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var saveProfileBtn: Button
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_patient_profile)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        nameInput = findViewById(R.id.patient_name_input)
        saveProfileBtn = findViewById(R.id.save_patient_profile_btn)

        saveProfileBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isNotEmpty()) {
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    val userUpdates = mapOf("name" to name)
                    firestore.collection("users").document(uid)
                        .update(userUpdates)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Profile created successfully", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, PatientHomeActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to create profile: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
