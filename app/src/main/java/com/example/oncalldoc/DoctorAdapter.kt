package com.example.oncalldoc

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DoctorAdapter(private val doctors: List<Doctor>) : RecyclerView.Adapter<DoctorAdapter.DoctorViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DoctorViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.doctor_list_item, parent, false)
        return DoctorViewHolder(view)
    }

    override fun onBindViewHolder(holder: DoctorViewHolder, position: Int) {
        val doctor = doctors[position]
        holder.bind(doctor)
    }

    override fun getItemCount() = doctors.size

    class DoctorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.doctor_name)
        private val specialityTextView: TextView = itemView.findViewById(R.id.doctor_speciality)
        private val distanceTextView: TextView = itemView.findViewById(R.id.doctor_distance)

        fun bind(doctor: Doctor) {
            nameTextView.text = doctor.name
            specialityTextView.text = doctor.speciality
            distanceTextView.text = String.format("%.2f km away", doctor.distance / 1000)
        }
    }
}
