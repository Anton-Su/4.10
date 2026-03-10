package com.example.a410

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigationevent.NavigationEventDispatcher
import com.example.a410.ui.theme._410Theme
import com.google.android.gms.location.*
import com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            _410Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                    )
                }
            }
        }
    }
}

sealed class LocationState {
    object Idle : LocationState()
    object Loading : LocationState()
    data class Success(
        val address: String,
        val latitude: Double,
        val longitude: Double
    ) : LocationState()
    data class Error(val message: String) : LocationState()
}

@SuppressLint("MissingPermission")
@Composable
fun Greeting() {
    val context = LocalContext.current
    var locationState by remember { mutableStateOf<LocationState>(LocationState.Idle) }
    val fusedClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }
    fun fetchLocation() {
        locationState = LocationState.Loading
        val cts = CancellationTokenSource()
        fusedClient.getCurrentLocation(
            PRIORITY_HIGH_ACCURACY, cts.token
        ).addOnSuccessListener { location: Location? ->
            if (location == null) {
                locationState = LocationState.Error("Не удалось.\nУбедитесь, что GPS включён.")
                return@addOnSuccessListener
            }
            val address = reverseGeocode(context, location.latitude, location.longitude)
            locationState = LocationState.Success(
                address = address,
                latitude = location.latitude,
                longitude = location.longitude
            )
        }.addOnFailureListener { e ->
            locationState = LocationState.Error("Ошибка получения координат:\n${e.localizedMessage}")
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    )
    { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            fetchLocation()
        } else {
            locationState = LocationState.Error("Разрешение на доступ к местоположению отклонено.\nПожалуйста, разрешите доступ в настройках приложения.")
        }
    }

    fun onGetAddressClick() {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            fetchLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = if (locationState is LocationState.Success)
                (locationState as LocationState.Success).address
            else "Симулятор деанона!",
            fontSize = 26.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        when (val state = locationState) {
            is LocationState.Loading -> {
                Text(
                    text = "Вычисляем ваш api...",
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            is LocationState.Success -> {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val lat = "%.6f".format(state.latitude)
                    val lng = "%.6f".format(state.longitude)
                    Text(
                        text = "Lat: $lat",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Lng: $lng",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            is LocationState.Error -> {
                Text(
                    text = state.message,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            is LocationState.Idle -> { }
        }

        Button(
            onClick = { onGetAddressClick() },
            enabled = locationState !is LocationState.Loading,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "Попытать удачу",
            )
        }
    }
}

// из координат в адрес
fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String {
    return try {
        val geocoder = Geocoder(context, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            var result = "Адрес не найден"
            geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                if (addresses.isNotEmpty()) {
                    result = formatAddress(addresses[0])
                }
            }
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) formatAddress(addresses[0]) else result
        } else {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) formatAddress(addresses[0]) else "Адрес не найден"
        }
    } catch (e: Exception) {
        "Не удалось определить адрес:\n${e.localizedMessage ?: "Нет подключения к интернету"}"
    }
}

fun formatAddress(address: android.location.Address): String {
    val parts = mutableListOf<String>()
    val thoroughfare = address.thoroughfare
    val subThoroughfare = address.subThoroughfare
    if (thoroughfare != null) {
        parts.add(if (subThoroughfare != null) "$thoroughfare, $subThoroughfare" else thoroughfare)
    }
    address.locality?.let { parts.add(it) }
    address.adminArea?.let { parts.add(it) }
    address.countryName?.let { parts.add(it) }
    return if (parts.isNotEmpty()) parts.joinToString("\n") else address.getAddressLine(0) ?: "Адрес не найден"
}