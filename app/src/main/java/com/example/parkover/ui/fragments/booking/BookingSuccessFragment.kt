package com.example.parkover.ui.fragments.booking

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.parkover.R
import com.example.parkover.data.model.BookingData
import com.example.parkover.databinding.FragmentBookingSuccessBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookingSuccessFragment : Fragment() {

    private var _binding: FragmentBookingSuccessBinding? = null
    private val binding get() = _binding!!
    
    private var bookingData: BookingData? = null
    private var bookingId: String? = null
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookingSuccessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        bookingData = arguments?.getParcelable("booking_data")
        bookingId = arguments?.getString("booking_id")
        
        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        val data = bookingData ?: return
        val id = bookingId ?: "PK000000"
        
        binding.tvBookingId.text = "Booking ID: #$id"
        binding.tvParkingName.text = data.parkingName
        binding.tvSpotInfo.text = "${data.floorName} • Spot ${data.spotNumber}"
        
        val checkInTime = timeFormat.format(Date(data.checkInTime))
        val checkOutTime = timeFormat.format(Date(data.checkOutTime))
        binding.tvTimeInfo.text = "$checkInTime - $checkOutTime"
        
        // Generate QR code
        generateQRCode(id)
    }

    private fun generateQRCode(bookingId: String) {
        try {
            val qrData = "PARKOVER:$bookingId"
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                qrData,
                BarcodeFormat.QR_CODE,
                300,
                300
            )
            
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(
                        x, y,
                        if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    )
                }
            }
            
            binding.ivQrCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            // Handle QR generation error
            e.printStackTrace()
        }
    }

    private fun setupClickListeners() {
        binding.btnViewBooking.setOnClickListener {
            // Navigate to bookings tab
            findNavController().navigate(R.id.bookingFragment)
        }
        
        binding.btnBackHome.setOnClickListener {
            // Navigate back to home
            findNavController().navigate(R.id.homeFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
