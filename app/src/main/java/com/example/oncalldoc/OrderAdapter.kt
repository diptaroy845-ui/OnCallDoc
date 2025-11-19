package com.example.oncalldoc

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class OrderAdapter(private val orders: List<Order>) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.order_list_item, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]
        holder.bind(order)
    }

    override fun getItemCount() = orders.size

    inner class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val patientNameTextView: TextView = itemView.findViewById(R.id.patient_name_text)
        private val orderStatusTextView: TextView = itemView.findViewById(R.id.order_status_text)

        fun bind(order: Order) {
            orderStatusTextView.text = order.status

            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users").document(order.patientId).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        val patientName = document.getString("name")
                        patientNameTextView.text = patientName
                    }
                }
        }
    }
}
