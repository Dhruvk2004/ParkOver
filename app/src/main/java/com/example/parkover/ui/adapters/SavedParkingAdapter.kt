package com.example.parkover.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.parkover.R
import com.example.parkover.data.model.ParkingSpot
import com.example.parkover.databinding.ItemSavedParkingBinding

class SavedParkingAdapter(
    private val onItemClick: (ParkingSpot) -> Unit,
    private val onUnsaveClick: (ParkingSpot) -> Unit
) : ListAdapter<ParkingSpot, SavedParkingAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSavedParkingBinding.inflate(
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
        private val binding: ItemSavedParkingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(spot: ParkingSpot) {
            binding.tvParkingName.text = spot.name
            binding.tvAddress.text = spot.address
            binding.tvRating.text = spot.rating.toString()
            binding.tvPrice.text = "₹${spot.pricePerHourFourWheeler.toInt()}/hr"
            binding.tvSpots.text = "${spot.getTotalAvailableSpots()} spots"

            // Load image
            if (spot.images.isNotEmpty()) {
                binding.ivParkingImage.load(spot.images.first()) {
                    crossfade(true)
                    placeholder(R.drawable.img)
                }
            } else {
                binding.ivParkingImage.setImageResource(R.drawable.img)
            }

            binding.root.setOnClickListener { onItemClick(spot) }
            binding.btnUnsave.setOnClickListener { onUnsaveClick(spot) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ParkingSpot>() {
        override fun areItemsTheSame(oldItem: ParkingSpot, newItem: ParkingSpot): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ParkingSpot, newItem: ParkingSpot): Boolean {
            return oldItem == newItem
        }
    }
}
