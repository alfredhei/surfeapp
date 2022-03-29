package com.example.surfeapp

import android.location.Location
import android.location.LocationManager
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitString
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.math.absoluteValue
import kotlin.math.exp
import android.content.Context
import androidx.core.content.ContentProviderCompat.requireContext
import java.security.AccessControlContext
import java.security.AccessController.getContext
import kotlin.coroutines.coroutineContext
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import java.io.InputStream;
import java.util.*

class DataSource {
//CLIENT ID FROST: af800469-bcec-450b-95c7-d7944ca2b73b
//CLIENT SECRET FROST: 0f39f1cf-033e-43a5-9602-f5855725a638

    public fun getConditions(spot:Surfespot):Conditions{
        // BESKRIVELSE
        // Når du bruker Surfespot.getConditions så kaller den egentlig bare på denne

        // LEGGE TIL ASYNKRON GET MED FUEL HER <------------
        var url = "https://in2000-apiproxy.ifi.uio.no/weatherapi/oceanforecast/2.0/complete?"

        url += "lat=" + spot.coordinates.latitude.toString()
        url += "&lon=" + spot.coordinates.longitude.toString()

        var url2 = "https://in2000-apiproxy.ifi.uio.no/weatherapi/nowcast/2.0/complete?"

        url2 += "lat=" + spot.coordinates.latitude.toString()
        url2 += "&lon=" + spot.coordinates.longitude.toString()

        val gson = Gson()
        var conditions:Conditions = Conditions(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F)
        runBlocking {
            try {
                println(Fuel.get(url).awaitString())
                val response = gson.fromJson(Fuel.get(url).awaitString(), Base::class.java)

                val response2 = gson.fromJson(Fuel.get(url2).awaitString(), Base2::class.java)
                println(response.toString())

                val wavesize:Float? = response.properties?.timeseries?.get(0)?.data?.instant?.details?.sea_surface_wave_height
                val currentspeed:Float? = response.properties?.timeseries?.get(0)?.data?.instant?.details?.sea_water_speed
                val currentdirection:Float? = response.properties?.timeseries?.get(0)?.data?.instant?.details?.sea_water_to_direction

                val air_temperature = response2.properties?.timeseries?.get(0)?.data?.instant?.details?.air_temperature
                val precipitation_rate = response2.properties?.timeseries?.get(0)?.data?.instant?.details?.precipitation_rate
                val wind_speed = response2.properties?.timeseries?.get(0)?.data?.instant?.details?.wind_speed
                val wind_from_direction = response2.properties?.timeseries?.get(0)?.data?.instant?.details?.wind_from_direction

                conditions = Conditions(wavesize, currentspeed, currentdirection, air_temperature, precipitation_rate, wind_speed, wind_from_direction)
            } catch(exception: Exception) {
                println("A network request exception was thrown: ${exception.message}")
            }
        }
        println(conditions.toString())
        return conditions
    }

    public fun getRating(spot:Surfespot):Int{
        var conditions:Conditions = getConditions(spot)

        val waveSize:Float = conditions.waveSize?.toFloat() ?: 0.toFloat()
        val waveSpeed:Float = conditions.currentSpeed?.toFloat() ?: 0.toFloat()

        var j = 13.8
        var tot:Float = 0.0.toFloat()
        var i = j

        var probabilities = mutableListOf<Float>()
        for(a in 3..7){
            if(a > 3){
                if(a == 7){
                    probabilities.add(a-3, 1 - tot)
                }else if(a == 4){
                    i = j + 1.5
                    probabilities.add(a-3, (exp(i-3.130*waveSize-1.184*waveSpeed)/(1+exp(i-3.130*waveSize-1.184*waveSpeed))-exp(j-3.130*waveSize-1.184*waveSpeed)/(1+exp(j-3.130*waveSize-1.184*waveSpeed))).toFloat())
                    tot = tot + (exp(i-3.130*waveSize-1.184*waveSpeed)/(1+exp(i-3.130*waveSize-1.184*waveSpeed))-exp(j-3.130*waveSize-1.184*waveSpeed)/(1+exp(j-3.130*waveSize-1.184*waveSpeed))).toFloat()
                }else{
                    i = j + 2.5
                    probabilities.add(a-3,(exp(i-3.130*waveSize-1.184*waveSpeed)/(1+exp(i-3.130*waveSize-1.184*waveSpeed))-exp(j-3.130*waveSize-1.184*waveSpeed)/(1+exp(j-3.130*waveSize-1.184*waveSpeed))).toFloat())
                    tot = tot + (exp(i-3.130*waveSize-1.184*waveSpeed)/(1+exp(i-3.130*waveSize-1.184*waveSpeed))-exp(j-3.130*waveSize-1.184*waveSpeed)/(1+exp(j-3.130*waveSize-1.184*waveSpeed))).toFloat()

                }
            }else{
                i = j
                probabilities.add(a-3, (exp(i-3.130*waveSize-1.184*waveSpeed)/(1+exp(i-3.130*waveSize-1.184*waveSpeed))).toFloat())
                tot = tot + (exp(i-3.130*waveSize-1.184*waveSpeed)/(1+exp(i-3.130*waveSize-1.184*waveSpeed))).toFloat()
            }
            j = i
        }

        var max:Float = 0.0.toFloat()
        var bestGuess = 0
        var k:Int = 0
        for(p in probabilities){
            if(p > max){
                max = p
                bestGuess = k
            }
            k++
        }
        return bestGuess + 1
    }

    public fun getSpots(context: Context): Spots? {
        // BESKRIVELSE
        // Kall på denne funksjonen for å få en liste med alle surfespot-objekte
        val jsonString: String
        val gson = Gson()

        try {
            jsonString = context.assets.open("surfespots.json").bufferedReader().use { it.readText() }
            val response = gson.fromJson(jsonString, Spots_json::class.java)
            println(response.toString())
            var converted_response:MutableList<Surfespot> = mutableListOf()
            for (i in response.list){
                val tmp_spot:Surfespot = Surfespot(i.id, i.name, Coordinates(i.latitude, i.longitude), i.description)
                converted_response.add(tmp_spot)
            }
            val spots: Spots = Spots(converted_response)
            return spots
        } catch (ioException: IOException) {
            ioException.printStackTrace()
            return null
        }
    }
}

// result generated from /json

data class Base(val type: String?, val geometry: Geometry?, val properties: Properties?)

data class Data(val instant: Instant?)

data class Details(val sea_surface_wave_from_direction: Float?, val sea_surface_wave_height: Float?, val sea_water_speed: Float?, val sea_water_temperature: Float?, val sea_water_to_direction: Float?)

data class Geometry(val type: String?, val coordinates: List<Number>?)

data class Instant(val details: Details?)

data class Meta(val updated_at: String?, val units: Units?)

data class Properties(val meta: Meta?, val timeseries: List<Timeseries139117139>?)

data class Timeseries139117139(val time: String?, val data: Data?)

data class Units(val sea_surface_wave_from_direction: String?, val sea_surface_wave_height: String?, val sea_water_speed: String?, val sea_water_temperature: String?, val sea_water_to_direction: String?)

data class Surfespot_json(val id: Int, val name: String, val latitude: Double, val longitude: Double, val description: String?)

data class Spots_json(val list: List<Surfespot_json>)





data class Base2(val type: String?, val geometry: Geometry2?, val properties: Properties2?)

data class Data2(val instant: Instant2?, val next_1_hours: Next_1_hours2?)

data class Details2(val air_temperature: Float?, val precipitation_rate: Float?, val relative_humidity: Float?, val wind_from_direction: Float?, val wind_speed: Float?, val wind_speed_of_gust: Float?)

data class Geometry2(val type: String?, val coordinates: List<Number>?)

data class Instant2(val details: Details2?)

data class Meta2(val updated_at: String?, val units: Units2?, val radar_coverage: String?)

data class Next_1_hours2(val summary: Summary2?, val details: Details2?)

data class Properties2(val meta: Meta2?, val timeseries: List<Timeseries5905723>?)

data class Summary2(val symbol_code: String?)

data class Timeseries5905723(val time: String?, val data: Data2?)

data class Units2(val air_temperature: String?, val precipitation_amount: String?, val precipitation_rate: String?, val relative_humidity: String?, val wind_from_direction: String?, val wind_speed: String?, val wind_speed_of_gust: String?)


// result generated from /json