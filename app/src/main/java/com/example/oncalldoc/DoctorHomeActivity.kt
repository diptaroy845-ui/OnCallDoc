package com.example.oncalldoc

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DoctorHomeActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var onlineSwitch: SwitchMaterial
    private lateinit var ordersRecyclerView: RecyclerView
    private lateinit var backButton: ImageButton
    private lateinit var updateLocationButton: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var pendingLocationAction: (() -> Unit)? = null
    private lateinit var orderAdapter: OrderAdapter
    private val orders = mutableListOf<Order>()

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
        ordersRecyclerView = findViewById(R.id.orders_recycler_view)
        backButton = findViewById(R.id.backFromDocHome)
        updateLocationButton = findViewById(R.id.update_location_button)

        ordersRecyclerView.layoutManager = LinearLayoutManager(this)
        orderAdapter = OrderAdapter(orders)
        ordersRecyclerView.adapter = orderAdapter

        backButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
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
                    }
                    .addOnFailureListener { 
                        Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show()
                    }
            }

            // Listen for order changes
            firestore.collection("orders")
                .whereEqualTo("doctorId", uid)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        return@addSnapshotListener
                    }

                    orders.clear()
                    for (doc in snapshots!!.documents) {
                        val order = doc.toObject(Order::class.java)
                        if (order != null) {
                            orders.add(order)
                        }
                    }
                    orderAdapter.notifyDataSetChanged()
                }
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
