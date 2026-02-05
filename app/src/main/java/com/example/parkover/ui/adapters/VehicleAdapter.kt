package com.example.parkover.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.parkover.R
import com.example.parkover.data.model.Vehicle
import com.example.parkover.data.model.VehicleType
import com.example.parkover.databinding.ItemVehicleBinding

class VehicleAdapter(
    private val onVehicleSelected: (Vehicle) -> Unit
) : ListAdapter<Vehicle, VehicleAdapter.VehicleViewHolder>(VehicleDiffCallback()) {

    private var selectedVehicleId: String? = null

    fun setSelectedVehicle(vehicleId: String) {
        val previousSelected = selectedVehicleId
        selectedVehicleId = vehicleId
        
        // Refresh items
        currentList.forEachIndexed { index, vehicle ->
            if (vehicle.id == previousSelected || vehicle.id == vehicleId) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleViewHolder {
        val binding = ItemVehicleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VehicleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VehicleViewHolder(
        private val binding: ItemVehicleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(vehicle: Vehicle) {
            val isSelected = vehicle.id == selectedVehicleId
            
            binding.tvVehicleName.text = "${vehicle.brand} ${vehicle.model}"
            binding.tvVehicleNumber.text = vehicle.number
            binding.radioButton.isChecked = isSelected
            
            // Set vehicle image from resource ID
            binding.ivVehicle.setImageResource(vehicle.imageResId)
            
            // Update card appearance based on selection
            val strokeColor = if (isSelected) {
                ContextCompat.getColor(binding.root.context, R.color.mainpurple)
            } else {
                ContextCompat.getColor(binding.root.context, R.color.button_border)
            }
            binding.cardVehicle.strokeColor = strokeColor
            binding.cardVehicle.strokeWidth = if (isSelected) 2 else 1
            
            binding.root.setOnClickListener {
                onVehicleSelected(vehicle)
            }
        }
    }

    class VehicleDiffCallback : DiffUtil.ItemCallback<Vehicle>() {
        override fun areItemsTheSame(oldItem: Vehicle, newItem: Vehicle): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Vehicle, newItem: Vehicle): Boolean {
            return oldItem == newItem
        }
    }
}
