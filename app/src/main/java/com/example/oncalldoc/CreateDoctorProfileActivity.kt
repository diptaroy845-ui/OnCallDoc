package com.example.oncalldoc

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CreateDoctorProfileActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var specialitySpinner: Spinner
    private lateinit var saveProfileBtn: Button
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val specialities = arrayOf("Cardiologist", "Dermatologist", "Pediatrician", "Neurologist", "General Physician")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_doctor_profile)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        nameInput = findViewById(R.id.doctor_name_input)
        specialitySpinner = findViewById(R.id.doctor_speciality_spinner)
        saveProfileBtn = findViewById(R.id.save_doctor_profile_btn)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, specialities)
        specialitySpinner.adapter = adapter

        saveProfileBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val speciality = specialitySpinner.selectedItem.toString()

            if (name.isNotEmpty()) {
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    val userUpdates = mapOf(
                        "name" to name,
                        "speciality" to speciality
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
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
