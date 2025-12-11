package com.actito.go.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
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

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var binding: FragmentHomeBinding
    private val productsAdapter = ProductsAdapter(::onProductClicked)
    private val beaconsAdapter = BeaconsAdapter()


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
            openAppSettings()
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

    private fun openAppSettings() {
        val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }

        startActivity(settingsIntent)
    }
}
