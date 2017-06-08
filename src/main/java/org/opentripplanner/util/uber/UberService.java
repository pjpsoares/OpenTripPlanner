package org.opentripplanner.util.uber;

import com.google.gson.Gson;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.opentripplanner.api.model.Place;
import org.opentripplanner.common.model.GenericLocation;

import java.io.IOException;

/**
 * Created by pso on 07/06/17.
 */
public class UberService {

    private static final String RIDE_ESTIMATE_PRICE = "https://api.uber.com/v1.2/estimates/price";
    private static final String RIDE_ESTIMATE_TIME = "https://api.uber.com/v1.2/estimates/time";

    public UberItinerary getEstimate(Place from, Place to) {
        return getEstimate(from.lat, from.lon, to.lat, to.lon);
    }

    public UberItinerary getEstimate(GenericLocation from, GenericLocation to) {
        return getEstimate(from.lat, from.lng, to.lat, to.lng);
    }

    public UberItinerary getEstimate(Double fromLat, Double fromLng, Double toLat, Double toLng) {
        try {
            UberETAResponse etaResponse = getEstimateETA(fromLat, fromLng);
            UberPricesResponse priceResponse = getEstimatePrice(fromLat, fromLng, toLat, toLng);

            UberETA eta = etaResponse.getETA();
            UberPriceEstimate priceEstimate = priceResponse.getPriceEstimate();

            return UberItinerary.fromApi(priceEstimate, eta);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private UberPricesResponse getEstimatePrice(Double fromLat, Double fromLng, Double toLat, Double toLng) throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(
            RIDE_ESTIMATE_PRICE +
                    "?start_latitude=" + fromLat +
                    "&start_longitude=" + fromLng +
                    "&end_latitude=" + toLat +
                    "&end_longitude=" + toLng
        );
        httpGet.setHeader("Authorization", "Token dcqp48R2CTiBLbpPfvv6idr4mpoDTjqbM9xqkOz-");
        httpGet.setHeader("Content-Type", "application/json");

        try {
            CloseableHttpResponse response = client.execute(httpGet);
            Gson g = new Gson();
            return g.fromJson(EntityUtils.toString(response.getEntity()), UberPricesResponse.class);
        } finally {
            client.close();
        }
    }

    private UberETAResponse getEstimateETA(Double fromLat, Double fromLng) throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(
                RIDE_ESTIMATE_TIME +
                        "?start_latitude=" + fromLat+
                        "&start_longitude=" + fromLng
        );
        httpGet.setHeader("Authorization", "Token dcqp48R2CTiBLbpPfvv6idr4mpoDTjqbM9xqkOz-");
        httpGet.setHeader("Content-Type", "application/json");

        try {
            CloseableHttpResponse response = client.execute(httpGet);
            Gson g = new Gson();
            return g.fromJson(EntityUtils.toString(response.getEntity()), UberETAResponse.class);
        } finally {
            client.close();
        }
    }

}
