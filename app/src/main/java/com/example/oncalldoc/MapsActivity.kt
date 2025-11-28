package com.example.oncalldoc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var firestore: FirebaseFirestore
    private var patientLocation: Location? = null
    private lateinit var backButton: ImageButton

    private val doctorMarkers = mutableMapOf<String, Marker>()
    private var firestoreListener: ListenerRegistration? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getCurrentPatientLocation()
        } else {
            Toast.makeText(this, "Location permission is required to show the map.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        firestore = FirebaseFirestore.getInstance()

        backButton = findViewById(R.id.back_from_maps_button)
        backButton.setOnClickListener {
            finish()
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        val paddingTopInPx = 150
        val paddingBottomInPx = 150
        mMap.setPadding(0, paddingTopInPx, 0, paddingBottomInPx)

        mMap.setOnInfoWindowClickListener { marker ->
            val doctor = marker.tag as? Doctor
            if (doctor != null) {
                showContactDialog(doctor)
            }
        }

        mMap.setOnPoiClickListener { poi ->
            val poiMarker = mMap.addMarker(MarkerOptions().position(poi.latLng).title(poi.name))
            poiMarker?.showInfoWindow()
        }

        checkPermissionAndGetLocation()
    }

    private fun showContactDialog(doctor: Doctor) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Contact ${doctor.name}")
        builder.setMessage("How would you like to contact the doctor?")

        builder.setPositiveButton("Call") { _, _ ->
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${doctor.phone}"))
            startActivity(intent)
        }

        builder.setNeutralButton("Chat") { _, _ ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("DOCTOR_UID", doctor.uid)
            intent.putExtra("DOCTOR_NAME", doctor.name)
            startActivity(intent)
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun checkPermissionAndGetLocation() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentPatientLocation()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun getCurrentPatientLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        mMap.isMyLocationEnabled = true

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    patientLocation = location
                    val patientLatLng = LatLng(location.latitude, location.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(patientLatLng, 10f))
                    mMap.addCircle(CircleOptions().center(patientLatLng).radius(2000.0).strokeColor(Color.BLUE).strokeWidth(2f).fillColor(Color.parseColor("#220000FF")))
                    listenForDoctorUpdates()
                } else {
                    Toast.makeText(this, "Could not get your location.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun listenForDoctorUpdates() {
        if (patientLocation == null) return

        firestoreListener?.remove()

        val query = firestore.collection("users")
            .whereEqualTo("role", "doctor")
            .whereEqualTo("isOnline", true)

        firestoreListener = query.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w("MapsActivity", "Listen failed.", e)
                return@addSnapshotListener
            }

            for (dc in snapshots!!.documentChanges) {
                val doctor = dc.document.toObject(Doctor::class.java)
                val doctorUid = dc.document.id

                when (dc.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                        if (doctor.latitude != null && doctor.longitude != null) {
                            val doctorLatLng = LatLng(doctor.latitude, doctor.longitude)
                            val existingMarker = doctorMarkers[doctorUid]

                            if (existingMarker == null) {
                                // Add new marker
                                val docLocation = Location("").apply { latitude = doctor.latitude; longitude = doctor.longitude }
                                val distanceInM = patientLocation!!.distanceTo(docLocation)
                                val distanceInKm = distanceInM / 1000.0
                                val markerTitle = String.format("%s (%.2fkm away)", doctor.name, distanceInKm)
                                
                                val marker = mMap.addMarker(
                                    MarkerOptions()
                                        .position(doctorLatLng)
                                        .title(markerTitle)
                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                                )
                                marker?.tag = doctor
                                if (marker != null) {
                                    doctorMarkers[doctorUid] = marker
                                }
                            } else {
                                // Update existing marker
                                existingMarker.position = doctorLatLng
                                val docLocation = Location("").apply { latitude = doctor.latitude; longitude = doctor.longitude }
                                val distanceInM = patientLocation!!.distanceTo(docLocation)
                                val distanceInKm = distanceInM / 1000.0
                                existingMarker.title = String.format("%s (%.2fkm away)", doctor.name, distanceInKm)
                                existingMarker.tag = doctor
                            }
                        }
                    }
                    DocumentChange.Type.REMOVED -> {
                        doctorMarkers[doctorUid]?.remove()
                        doctorMarkers.remove(doctorUid)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        firestoreListener?.remove()
    }
}
