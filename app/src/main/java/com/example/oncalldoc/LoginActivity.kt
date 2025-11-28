package com.example.oncalldoc

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginBtn: Button
    private lateinit var signupRedirect: TextView
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // If the user is already logged in, redirect them to the correct home screen.
        if (auth.currentUser != null) {
            redirectUser(auth.currentUser!!.uid)
            return // Skip displaying the login UI
        }

        // Only show the login page if the user is not logged in.
        setContentView(R.layout.login_page)

        firestore = FirebaseFirestore.getInstance()

        emailInput = findViewById(R.id.email_input)
        passwordInput = findViewById(R.id.password_input)
        loginBtn = findViewById(R.id.login_btn)
        signupRedirect = findViewById(R.id.signup_btn)
        loadingSpinner = findViewById(R.id.loading_spinner)

        loginBtn.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loadingSpinner.visibility = View.VISIBLE
            loginBtn.isEnabled = false

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        redirectUser(task.result.user!!.uid)
                    } else {
                        Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        loadingSpinner.visibility = View.GONE
                        loginBtn.isEnabled = true
                    }
                }
        }

        signupRedirect.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun redirectUser(uid: String) {
        firestore = FirebaseFirestore.getInstance()
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role") ?: "patient"
                val name = doc.getString("name")

                val intent = if (name == null) {
                    // New user, needs to create a profile
                    if (role == "patient") {
                        Intent(this, CreatePatientProfileActivity::class.java)
                    } else {
                        Intent(this, CreateDoctorProfileActivity::class.java)
                    }
                } else {
                    // Existing user, go to home screen
                    if (role == "patient") {
                        Intent(this, PatientHomeActivity::class.java)
                    } else {
                        Intent(this, DoctorHomeActivity::class.java)
                    }
                }
                startActivity(intent)
                finish() // This is crucial to prevent the user from coming back to the login screen.
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching user details: ${e.message}", Toast.LENGTH_SHORT).show()
                // If something goes wrong, stay on the login page
                setContentView(R.layout.login_page) 
            }
    }
}
