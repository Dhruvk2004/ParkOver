package com.example.parkover.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.parkover.R
import com.example.parkover.data.model.ParkingSlot
import com.example.parkover.databinding.ItemParkingSpotBinding

class ParkingSpotAdapter(
    private val onSpotSelected: (ParkingSlot) -> Unit
) : ListAdapter<List<ParkingSlot>, ParkingSpotAdapter.SpotRowViewHolder>(SpotRowDiffCallback()) {

    private var selectedSpotId: String? = null

    fun setSelectedSpot(spotId: String?) {
        val previousSelected = selectedSpotId
        selectedSpotId = spotId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpotRowViewHolder {
        val binding = ItemParkingSpotBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SpotRowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SpotRowViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SpotRowViewHolder(
        private val binding: ItemParkingSpotBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(spotRow: List<ParkingSlot>) {
            // Left spot
            val leftSpot = spotRow.getOrNull(0)
            if (leftSpot != null) {
                setupSpot(
                    binding.leftSpot,
                    binding.bgLeft,
                    binding.ivCarLeft,
                    binding.tvSpotLeft,
                    leftSpot
                )
            } else {
                binding.leftSpot.visibility = View.INVISIBLE
            }

            // Right spot
            val rightSpot = spotRow.getOrNull(1)
            if (rightSpot != null) {
                setupSpot(
                    binding.rightSpot,
                    binding.bgRight,
                    binding.ivCarRight,
                    binding.tvSpotRight,
                    rightSpot
                )
            } else {
                binding.rightSpot.visibility = View.INVISIBLE
            }
        }

        private fun setupSpot(
            container: FrameLayout,
            background: View,
            carImage: ImageView,
            spotLabel: TextView,
            spot: ParkingSlot
        ) {
            container.visibility = View.VISIBLE
            val isSelected = spot.id == selectedSpotId
            val context = container.context

            // Reset container background
            container.setBackgroundColor(android.graphics.Color.TRANSPARENT)

            if (!spot.isAvailable) {
                // OCCUPIED - Show car image only, no background, no label
                background.visibility = View.GONE
                carImage.visibility = View.VISIBLE
                carImage.setImageResource(R.drawable.car_top_view)
                spotLabel.visibility = View.GONE
                
                // Disable click completely for occupied spots
                container.isClickable = false
                container.isFocusable = false
                container.setOnClickListener(null)
            } else {
                // AVAILABLE - Show border background and label
                carImage.visibility = View.GONE
                spotLabel.visibility = View.VISIBLE
                spotLabel.text = spot.spotNumber
                background.visibility = View.VISIBLE
                
                if (isSelected) {
                    // Selected state - purple stroke only, light purple fill
                    background.setBackgroundResource(R.drawable.parking_spot_selected_stroke)
                    spotLabel.setBackgroundResource(R.drawable.spot_label_selected_background)
                    spotLabel.setTextColor(ContextCompat.getColor(context, R.color.white))
                } else {
                    // Available state - light purple background with light border
                    background.setBackgroundResource(R.drawable.parking_spot_available)
                    spotLabel.setBackgroundResource(R.drawable.spot_label_background)
                    spotLabel.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                }
                
                // Enable click for available spots
                container.isClickable = true
                container.isFocusable = true
                container.setOnClickListener {
                    onSpotSelected(spot)
                }
            }
        }
    }

    class SpotRowDiffCallback : DiffUtil.ItemCallback<List<ParkingSlot>>() {
        override fun areItemsTheSame(oldItem: List<ParkingSlot>, newItem: List<ParkingSlot>): Boolean {
            return oldItem.firstOrNull()?.id == newItem.firstOrNull()?.id
        }

        override fun areContentsTheSame(oldItem: List<ParkingSlot>, newItem: List<ParkingSlot>): Boolean {
            return oldItem == newItem
        }
    }
}
