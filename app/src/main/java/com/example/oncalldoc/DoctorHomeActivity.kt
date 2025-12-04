package com.example.oncalldoc

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DoctorHomeActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var onlineSwitch: SwitchMaterial
    private lateinit var backButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var updateLocationButton: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var conversationsRecyclerView: RecyclerView
    private var pendingLocationAction: (() -> Unit)? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                pendingLocationAction?.let { executeWithLocationSettingsCheck(it) }
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private val locationSettingsLauncher = 
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                pendingLocationAction?.invoke()
            } else {
                Toast.makeText(this, "Location must be enabled for this feature.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doctor_home)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        onlineSwitch = findViewById(R.id.online_switch)
        conversationsRecyclerView = findViewById(R.id.conversations_recycler_view)
        backButton = findViewById(R.id.backFromDocHome)
        settingsButton = findViewById(R.id.settings_doctor)
        updateLocationButton = findViewById(R.id.update_location_button)

        backButton.setOnClickListener {
            finish()
        }

        settingsButton.setOnClickListener {
            showSettingsMenu()
        }

        updateLocationButton.setOnClickListener {
            checkLocationPermissionAndSettings { updateDoctorLocation() }
        }

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
                        if (isChecked) {
                            checkLocationPermissionAndSettings { updateDoctorLocation() }
                        }
                    }
                    .addOnFailureListener { 
                        Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show()
                    }
            }

            // We will set up the conversations RecyclerView in the next step
        }
    }

    private fun showSettingsMenu() {
        val options = arrayOf("Edit Profile", "Delete Account", "Logout")
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, EditDoctorProfileActivity::class.java))
                    1 -> showDeleteAccountDialog()
                    2 -> showLogoutConfirmationDialog()
                }
            }
            .show()
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Logout") { _, _ ->
                auth.signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteAccountDialog() {
        val passwordInput = EditText(this)
        passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        passwordInput.hint = "Enter your password to confirm"

        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("This action is permanent. To delete your account, please enter your password.")
            .setView(passwordInput)
            .setPositiveButton("Delete") { _, _ ->
                val password = passwordInput.text.toString()
                if (password.isNotEmpty()) {
                    deleteUserAccount(password)
                } else {
                    Toast.makeText(this, "Password is required.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteUserAccount(password: String) {
        val user = auth.currentUser
        if (user?.email == null) return

        val credential = EmailAuthProvider.getCredential(user.email!!, password)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                val uid = user.uid
                firestore.collection("users").document(uid).delete()
                    .addOnSuccessListener {
                        user.delete()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Account deleted successfully.", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this, LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to delete account: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to delete user data: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { 
                Toast.makeText(this, "Authentication failed. Incorrect password.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkLocationPermissionAndSettings(action: () -> Unit) {
        pendingLocationAction = action
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                executeWithLocationSettingsCheck(action)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun executeWithLocationSettingsCheck(action: () -> Unit) {
        val locationRequest = LocationRequest.create().apply {
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            action.invoke()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettingsLauncher.launch(intentSenderRequest)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }

    private fun updateDoctorLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return 
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude
                    val geohash = GeoFireUtils.getGeoHashForLocation(GeoLocation(lat, lon))
                    val uid = auth.currentUser?.uid

                    if (uid != null) {
                        val doctorRef = firestore.collection("users").document(uid)
                        val locationData = hashMapOf(
                            "latitude" to lat,
                            "longitude" to lon,
                            "geohash" to geohash
                        )

                        doctorRef.update(locationData as Map<String, Any>)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Location updated successfully!", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to update location: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Toast.makeText(this, "Could not get current location.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to get location: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
