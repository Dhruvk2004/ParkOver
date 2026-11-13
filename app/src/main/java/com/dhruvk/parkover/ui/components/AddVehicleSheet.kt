package com.dhruvk.parkover.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dhruvk.parkover.data.model.VehicleType
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.utils.isValidVehicleNumber
import com.dhruvk.parkover.viewmodels.VehicleCatalog

/** Add-vehicle bottom sheet (Compose port of the XML dialog_add_vehicle). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleSheet(
    catalog: VehicleCatalog,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (type: VehicleType, brand: String, model: String, number: String, makeDefault: Boolean) -> Unit,
    initialType: VehicleType = VehicleType.FOUR_WHEELER
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember { mutableStateOf(initialType) }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var makeDefault by remember { mutableStateOf(false) }
    var numberError by remember { mutableStateOf<String?>(null) }
    var brandError by remember { mutableStateOf<String?>(null) }

    fun save() {
        brandError = if (brand.isBlank()) "Pick or type a brand" else null
        numberError = when {
            number.isBlank() -> "Enter the number plate"
            !number.isValidVehicleNumber() -> "Use the plate format, e.g. MH 02 AB 1234"
            else -> null
        }
        if (brandError == null && numberError == null) onSave(type, brand, model, number, makeDefault)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Add a vehicle", style = MaterialTheme.typography.titleLarge, color = TextPrimary)

            // Type segmented control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEEF0F4))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TypeSegment(Icons.Rounded.TwoWheeler, "Bike", type == VehicleType.TWO_WHEELER, Modifier.weight(1f)) {
                    type = VehicleType.TWO_WHEELER; brand = ""; model = ""
                }
                TypeSegment(Icons.Rounded.DirectionsCar, "Car", type == VehicleType.FOUR_WHEELER, Modifier.weight(1f)) {
                    type = VehicleType.FOUR_WHEELER; brand = ""; model = ""
                }
                TypeSegment(Icons.Rounded.LocalShipping, "Heavy", type == VehicleType.HEAVY, Modifier.weight(1f)) {
                    type = VehicleType.HEAVY; brand = ""; model = ""
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ComboField(
                    label = "Brand",
                    value = brand,
                    options = catalog.brands(type),
                    onValueChange = { brand = it; model = ""; brandError = null },
                    error = brandError,
                    modifier = Modifier.weight(1f)
                )
                ComboField(
                    label = "Model",
                    value = model,
                    options = catalog.models(type, brand),
                    onValueChange = { model = it },
                    modifier = Modifier.weight(1f)
                )
            }

            ParkTextField(
                value = number,
                onValueChange = { number = it.uppercase().take(13); numberError = null },
                label = "Number plate",
                placeholder = "MH 02 AB 1234",
                error = numberError,
                monospace = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done)
            )
            if (number.isValidVehicleNumber()) {
                NumberPlate(number)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use as my default vehicle", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = makeDefault,
                    onCheckedChange = { makeDefault = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Purple)
                )
            }

            PrimaryButton(text = "Save vehicle", onClick = ::save, loading = saving)
        }
    }
}

@Composable
private fun TypeSegment(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Purple else TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) TextPrimary else TextSecondary
        )
    }
}

/** Text field with a dropdown of suggestions; free text is allowed for brands/models not listed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComboField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = options.filter { value.isBlank() || it.contains(value, ignoreCase = true) }
    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        ParkTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = label,
            error = error,
            trailing = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
            containerColor = Color.White
        ) {
            filtered.take(12).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
