package com.example.parkover.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.parkover.R
import com.example.parkover.data.model.Booking
import com.example.parkover.databinding.ItemParkingCardBinding

class BookingAdapter(
    private val onItemClick: (Booking) -> Unit
) : ListAdapter<Booking, BookingAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemParkingCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemParkingCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(booking: Booking) {
            binding.tvParkingName.text = booking.parkingName
            binding.tvAddress.text = booking.parkingAddress
            binding.tvPrice.text = "₹${booking.totalPrice.toInt()}"
            binding.tvDuration.text = "/ ${booking.durationHours} hours"

            // Load image - for now use placeholder
            binding.ivParkingImage.setImageResource(R.drawable.img)

            binding.root.setOnClickListener { onItemClick(booking) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Booking>() {
        override fun areItemsTheSame(oldItem: Booking, newItem: Booking): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Booking, newItem: Booking): Boolean {
            return oldItem == newItem
        }
    }
}
