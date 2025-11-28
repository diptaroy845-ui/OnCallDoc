package com.example.oncalldoc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CreateDoctorProfileActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var saveProfileBtn: Button
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_doctor_profile)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        nameInput = findViewById(R.id.doctor_name_input)
        phoneInput = findViewById(R.id.doctor_phone_input) // Corrected this to use phone input
        saveProfileBtn = findViewById(R.id.save_doctor_profile_btn)

        saveProfileBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()

            if (name.isNotEmpty() && phone.isNotEmpty()) {
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    val userUpdates = mapOf(
                        "name" to name,
                        "phone" to phone
                    )
                    firestore.collection("users").document(uid)
                        .update(userUpdates)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Profile created successfully", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, DoctorHomeActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to create profile: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                Toast.makeText(this, "Please enter your name and phone number", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
