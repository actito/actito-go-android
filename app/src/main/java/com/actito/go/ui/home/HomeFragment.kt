package com.actito.go.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.actito.geo.ActitoGeo
import com.actito.go.BuildConfig
import com.actito.go.R
import com.actito.go.databinding.FragmentHomeBinding
import com.actito.go.ktx.hasGeofencingCapabilities
import com.actito.go.live_activities.models.CoffeeBrewingState
import com.actito.go.models.Product
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var binding: FragmentHomeBinding
    private val productsAdapter = ProductsAdapter(::onProductClicked)
    private val beaconsAdapter = BeaconsAdapter()

    private val pendingRationales = mutableListOf<PermissionType>()

    private val openSettingsLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.changeLocationUpdates(enabled = true)
    }

    private val foregroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.all { it.value }

        if (granted) {
            return@registerForActivityResult requestLocationPermission()
        }

        if (shouldOpenSettings()) {
            showSettingsPrompt()
            return@registerForActivityResult
        }

        // Enables location updates with whatever capabilities have been granted so far.
        viewModel.changeLocationUpdates(enabled = true)
    }

    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            return@registerForActivityResult requestLocationPermission()
        }

        // Enables location updates with whatever capabilities have been granted so far.
        viewModel.changeLocationUpdates(enabled = true)
    }

    private val bluetoothScanLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            return@registerForActivityResult requestLocationPermission()
        }

        // Enables location updates with whatever capabilities have been granted so far.
        viewModel.changeLocationUpdates(enabled = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(viewModel)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.productsList.adapter = productsAdapter
        binding.productsList.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        binding.beaconsList.adapter = beaconsAdapter
        binding.beaconsList.layoutManager = LinearLayoutManager(requireContext())

        updateBeaconSection()

        setupListeners()
        setupObservers()
    }

    override fun onResume() {
        super.onResume()

        updateBeaconSection()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(viewModel)
    }

    private fun setupListeners() {
        binding.productsListButton.setOnClickListener {
            findNavController().navigate(R.id.home_to_products_list_action)
        }

        binding.geofencingAlertButton.setOnClickListener {
            requestLocationPermission()
        }

        binding.viewPolicyButton.setOnClickListener {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(requireContext(), BuildConfig.LOCATION_DATA_PRIVACY_POLICY_URL.toUri())
        }

        binding.locationDisabledAlertButton.setOnClickListener {
            val intent =
                Intent(Intent.ACTION_VIEW, "re.notifica.go://notifica.re/settings".toUri())
            findNavController().handleDeepLink(intent)
        }

        binding.eventsButton.setOnClickListener {
            findNavController().navigate(R.id.home_to_events_action)
        }
    }

    private fun setupObservers() {
        viewModel.products.observe(viewLifecycleOwner) { products ->
            productsAdapter.submitList(products)

            binding.productsGroup.isVisible = products.isNotEmpty()
        }

        viewModel.rangedBeacons.observe(viewLifecycleOwner) { beacons ->
            beaconsAdapter.submitList(beacons)

            binding.beaconsList.isVisible = beacons.isNotEmpty()
            binding.beaconsEmptyMessageLabel.isVisible = beacons.isEmpty()
        }

        viewModel.coffeeBrewerUiState.observe(viewLifecycleOwner) { uiState ->
            binding.coffeeBrewerButton.isVisible = uiState.brewingState != CoffeeBrewingState.SERVED
            binding.coffeeBrewerCancelButton.isVisible = uiState.brewingState != null

            when (uiState.brewingState) {
                null -> {
                    binding.coffeeBrewerButton.setText(R.string.home_coffee_brewer_create_button)
                    binding.coffeeBrewerButton.setOnClickListener {
                        viewModel.createCoffeeSession()
                    }
                }
                CoffeeBrewingState.GRINDING -> {
                    binding.coffeeBrewerButton.setText(R.string.home_coffee_brewer_brew_button)
                    binding.coffeeBrewerButton.setOnClickListener {
                        viewModel.continueCoffeeSession()
                    }
                }
                CoffeeBrewingState.BREWING -> {
                    binding.coffeeBrewerButton.setText(R.string.home_coffee_brewer_serve_button)
                    binding.coffeeBrewerButton.setOnClickListener {
                        viewModel.continueCoffeeSession()
                    }
                }
                CoffeeBrewingState.SERVED -> {}
            }

            binding.coffeeBrewerCancelButton.setOnClickListener {
                viewModel.cancelCoffeeSession()
            }
        }
    }

    private fun onProductClicked(product: Product) {
        findNavController().navigate(
            HomeFragmentDirections.homeToProductDetailsAction(product.id)
        )
    }

    private fun updateBeaconSection() {
        binding.beaconsCard.isVisible = false
        binding.geofencingAlert.isVisible = false
        binding.locationDisabledAlert.isVisible = false

        when {
            !ActitoGeo.hasGeofencingCapabilities -> {
                binding.geofencingAlert.isVisible = true
            }
            !ActitoGeo.hasLocationServicesEnabled -> {
                binding.locationDisabledAlert.isVisible = true
            }
            else -> {
                binding.beaconsCard.isVisible = true
            }
        }
    }

    private fun requestLocationPermission() {
        if (!ensureForegroundLocationPermission()) return
        if (!ensureBackgroundLocationPermission()) return
        if (!ensureBluetoothScanPermission()) return
    }

    private fun ensureForegroundLocationPermission(): Boolean {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED

        // We already have been granted the requested permission. Move forward...
        if (granted) return true

        if (shouldShowRequestPermissionRationale(permission)) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.app_name)
                .setMessage(R.string.permission_foreground_location_rationale)
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_ok_button) { _, _ ->
                    Timber.d("Requesting foreground location permission.")
                    pendingRationales.add(PermissionType.LOCATION)
                    foregroundLocationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                }
                .setNegativeButton(R.string.dialog_cancel_button) { _, _ ->
                    Timber.d("Foreground location permission rationale cancelled.")

                    // Enables location updates with whatever capabilities have been granted so far.
                    viewModel.changeLocationUpdates(enabled = true)
                }
                .show()

            return false
        }

        Timber.d("Requesting foreground location permission.")
        foregroundLocationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )

        return false
    }

    private fun ensureBackgroundLocationPermission(): Boolean {
        val permission = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Manifest.permission.ACCESS_BACKGROUND_LOCATION
            else -> Manifest.permission.ACCESS_FINE_LOCATION
        }

        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED

        // We already have been granted the requested permission. Move forward...
        if (granted) return true

        if (shouldShowRequestPermissionRationale(permission)) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.app_name)
                .setMessage(R.string.permission_background_location_rationale)
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_ok_button) { _, _ ->
                    Timber.d("Requesting background location permission.")
                    backgroundLocationPermissionLauncher.launch(permission)
                }
                .setNegativeButton(R.string.dialog_cancel_button) { _, _ ->
                    Timber.d("Background location permission rationale cancelled.")

                    // Enables location updates with whatever capabilities have been granted so far.
                    viewModel.changeLocationUpdates(enabled = true)
                }
                .show()

            return false
        }

        Timber.d("Requesting background location permission.")
        backgroundLocationPermissionLauncher.launch(permission)

        return false
    }

    private fun ensureBluetoothScanPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true

        val permission = Manifest.permission.BLUETOOTH_SCAN
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED

        // We already have been granted the requested permission. Move forward...
        if (granted) return true

        if (shouldShowRequestPermissionRationale(permission)) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.app_name)
                .setMessage(R.string.permission_bluetooth_scan_rationale)
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_ok_button) { _, _ ->
                    Timber.d("Requesting bluetooth scan permission.")
                    bluetoothScanLocationPermissionLauncher.launch(permission)
                }
                .setNegativeButton(R.string.dialog_cancel_button) { _, _ ->
                    Timber.d("Bluetooth scan permission rationale cancelled.")

                    // Enables location updates with whatever capabilities have been granted so far.
                    viewModel.changeLocationUpdates(enabled = true)
                }
                .show()

            return false
        }

        Timber.d("Requesting bluetooth scan permission.")
        bluetoothScanLocationPermissionLauncher.launch(permission)

        return false
    }

    private fun shouldOpenSettings(): Boolean {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION

        // Rationale did show and permission was denied
        if (!shouldShowRequestPermissionRationale(permission) && pendingRationales.contains(PermissionType.LOCATION)) {
            pendingRationales.remove(PermissionType.LOCATION)
            return false
        }
        // Rational did show but request was dismissed
        if (shouldShowRequestPermissionRationale(permission) && pendingRationales.contains(PermissionType.LOCATION)) {
            pendingRationales.remove(PermissionType.LOCATION)
            return false
        }
        // First permission request denied
        if (shouldShowRequestPermissionRationale(permission) && !pendingRationales.contains(PermissionType.LOCATION)) {
            return false
        }
        return true
    }

    private fun showSettingsPrompt() {
        AlertDialog.Builder(requireContext()).setTitle(R.string.app_name)
            .setMessage(R.string.permission_open_os_settings_rationale)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_settings_button) { _, _ ->
                Timber.d("Opening OS Settings")
                val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }

                openSettingsLocationLauncher.launch(settingsIntent)
            }
            .setNegativeButton(R.string.dialog_cancel_button) { _, _ ->
                Timber.d("Redirect to OS Settings cancelled")
                viewModel.changeLocationUpdates(enabled = true)
            }
            .show()
    }

    enum class PermissionType {
        LOCATION
    }
}
