package com.example.weatherapp.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.R
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.example.weatherapp.utils.Constants
import com.google.gson.Gson
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

// OpenWeather Link : https://openweathermap.org/api
/**
 * The useful link or some more explanation for this app you can checkout this link :
 * https://medium.com/@sasude9/basic-android-weather-app-6a7c0855caf4
 */

class MainActivity : AppCompatActivity() {

    //TODO(Inorder to use view binding to access the views in the layout)
    private var binding:ActivityMainBinding? = null

    // TODO (STEP 3: Add a variable for FusedLocationProviderClient.)
    // START
    // A fused location client variable which is further used to get the user's current location
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    // END



    // TODO (STEP 4: Create a global variable for ProgressDialog.)
    // A global variable for the Progress Dialog
    private var mProgressDialog: Dialog? = null

    // TODO (Add a variable for SharedPreferences)
    // START
    // A global variable for the SharedPreferences
    private lateinit var mSharedPreferences: SharedPreferences
    // END

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        // TODO (STEP 4: Initialize the fusedLocationProviderClient variable.)
        // START
        // Initialize the Fused location variable
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        // END

        // TODO (Initialize the SharedPreferences variable.)
        // START
        // Initialize the SharedPreferences variable
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        // END

        setupUI()

        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()

            // This will redirect you to settings from where you need to turn on the location provider.
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            // TODO (STEP 1: Asking the location permission on runtime.)
            // START
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            // TODO (STEP 7: Call the location request function here.)
                            // START
                            requestLocationData()
                            // END
                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission. Please allow it is mandatory.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()
            // END
        }
    }



    /**
     * A function which is used to verify that the location or GPS is enable or not of the user's device.
     */
    private fun isLocationEnabled(): Boolean {

        // This provides access to the system location services.
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    // TODO (STEP 2: A alert dialog for denied permissions and if needed to allow it from the settings app info.)
    // START
    /**
     * A function used to show the alert dialog when the permissions are denied and need to allow it from settings app info.
     */
    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog,
                                           _ ->
                dialog.dismiss()
            }.show()
    }
    // END

    // TODO (STEP 5: Add a function to get the location of the device using the fusedLocationProviderClient.)
    // START
    /**
     * A function to request the current location. Using the fused location provider client.
     */
    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }
    // END

    // TODO (STEP 6: Register a request location callback to get the location.)
    // START
    /**
     * A location callback object of fused location provider client where we will get the current location details.
     */
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation!!
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Longitude", "$longitude")
            getLocationWeatherDetails(latitude,longitude)
        }
    }
    // END

    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){
        if (com.example.weatherapp.utils.Constants.isNetworkAvailable(this)){
             val retrofit:Retrofit = Retrofit.Builder()
                 .baseUrl(Constants.BASE_URL)
                 .addConverterFactory(GsonConverterFactory.create())
                 .build()

            // TODO (Further step for API call)
            // START
            /**
             * Here we map the service interface in which we declares the end point and the API type
             *i.e GET, POST and so on along with the request parameter which are required.
             */

            val service:WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)

            /** An invocation of a Retrofit method that sends a request to a web-server and returns a response.
             * Here we pass the required param in the service
             */
            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID
            )

            showCustomProgressDialog()

            // Callback methods are executed using the Retrofit callback executor.
            listCall.enqueue(object : Callback<WeatherResponse> {
                @SuppressLint("SetTextI18n")
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {

                    // Check weather the response is success or not.
                    if (response.isSuccessful) {
                        hideProgressDialog()

                        /** The de-serialized response body of a successful response. */
                        val weatherList: WeatherResponse? = response.body()
                        if (weatherList != null) {

                            val weatherResponseJsonString = Gson().toJson(weatherList)
                            //prepare the shared preference editor
                            val editor =mSharedPreferences.edit()
                            //put information to the editor
                            editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                            editor.apply()





                        }
                        Log.i("Response Result", "$weatherList")
//                        hideProgressDialog()

                    } else {
                        // If the response is not success then we check the response code.
                        val sc = response.code()
                        when (sc) {
                            400 -> {
                                Log.e("Error 400", "Bad Request")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Errorrrr!",t.message.toString())
                    hideProgressDialog()
                }

            })
        }

    }

    // TODO (Create a functions for SHOW and HIDE progress dialog.)
    // START
    /**
     * Method is used to show the Custom Progress Dialog.
     */
    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)

        /*Set the screen content from a layout resource.
        The resource will be inflated, adding all top-level views to the screen.*/
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        //Start the dialog and display it on screen.
        mProgressDialog!!.show()
    }

    /**
     * This function is used to dismiss the progress dialog if it is visible to user.
     */
    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }
    // END

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        //inflate the menu
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh -> {
                requestLocationData()
                true
            }
            else ->{
                super.onOptionsItemSelected(item)

            }

        }
    }

    // TODO (We have set the values to the UI and also added some required methods for Unit and Time below.)
    /**
     * Function is used to set the result in the UI elements.
     */

    private fun setupUI(){
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")
        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList =Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)

            //For loop to get the required data. And all are populated in the UI.
            for (i in weatherList.weather.indices){
                Log.i("Weather name", weatherList.weather.toString())
                val textMain = binding?.tvMain
                textMain?.text= weatherList.weather[i].main

                val textDescription = binding?.tvMainDescription
                textDescription?.text = weatherList.weather[i].description

                val textTemp = binding?.tvTemp
                textTemp?.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.toString())

                val textSunRise = binding?.tvSunriseTime
                textSunRise?.text = unixTime(weatherList.sys.sunrise)

                val textSunSet = binding?.tvSunsetTime
                textSunSet?.text = unixTime(weatherList.sys.sunset)

                val textHumidity = binding?.tvHumidity
                textHumidity?.text = weatherList.main.humidity.toString() + " per cent"

                val textmin = binding?.tvMin
                textmin?.text = weatherList.main.temp_min.toString() + " min"

                val textMax = binding?.tvMax
                textMax?.text = weatherList.main.temp_max.toString() + " max"

                val textSpeed = binding?.tvSpeed
                textSpeed?.text = weatherList.wind.speed.toString()

                val textName = binding?.tvName
                textName?.text = weatherList.name

                val textCountry = binding?.tvCountry
                textCountry?.text = weatherList.sys.country


                val imageMain = binding?.ivMain


                // Here we update the main icon
                when(weatherList.weather[i].icon){
                    "01" -> imageMain?.setImageResource(R.drawable.sunny)
                    "02d" -> imageMain?.setImageResource(R.drawable.cloud)
                    "03d" -> imageMain?.setImageResource(R.drawable.cloud)
                    "04d" -> imageMain?.setImageResource(R.drawable.cloud)
                    "04n" -> imageMain?.setImageResource(R.drawable.cloud)
                    "10d" -> imageMain?.setImageResource(R.drawable.rain)
                    "11d" -> imageMain?.setImageResource(R.drawable.storm)
                    "13d" -> imageMain?.setImageResource(R.drawable.snowflake)
                    "01n" -> imageMain?.setImageResource(R.drawable.cloud)
                    "02n" -> imageMain?.setImageResource(R.drawable.cloud)
                    "03n" -> imageMain?.setImageResource(R.drawable.cloud)
                    "10n" -> imageMain?.setImageResource(R.drawable.cloud)
                    "11n" -> imageMain?.setImageResource(R.drawable.rain)
                    "13n" -> imageMain?.setImageResource(R.drawable.snowflake)


                }



            }
        }







    }




    /**
     * Function is used to get the temperature unit value.
     */
    private fun getUnit(value: String):String? {
        var value = "°C"
        if ("US" ==value || "LR" == value || "MM" ==value){
            value ="°F"
        }

        return value

    }

    /**
     * The function is used to get the formatted time based on the Format and the LOCALE we pass to it.
     */
    private fun unixTime(timex:Long):String?{
        val date = Date(timex*1000L)
        val sdf = SimpleDateFormat("HH:mm",Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

}