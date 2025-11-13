package com.example.oncalldoc

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DoctorHomeActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                updateLocation()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doctor_home)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val updateLocationBtn: Button = findViewById(R.id.update_location_btn)
        updateLocationBtn.setOnClickListener {
            checkPermissionAndUpdateLocation()
        }
    }

    private fun checkPermissionAndUpdateLocation() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                updateLocation()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun updateLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val uid = auth.currentUser?.uid
                        if (uid != null) {
                            val geohash = GeoFireUtils.getGeoHashForLocation(
                                GeoLocation(location.latitude, location.longitude)
                            )
                            val userUpdates = mapOf(
                                "latitude" to location.latitude,
                                "longitude" to location.longitude,
                                "geohash" to geohash
                            )

                            firestore.collection("users").document(uid)
                                .update(userUpdates)
                                .addOnSuccessListener {
                                    Toast.makeText(
                                        this,
                                        "Location updated successfully",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(
                                        this,
                                        "Failed to update location: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                    } else {
                        Toast.makeText(this, "Could not get location.", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}
