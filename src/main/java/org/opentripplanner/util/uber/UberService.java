package org.opentripplanner.util.uber;

import com.google.gson.Gson;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.opentripplanner.common.model.GenericLocation;

import java.io.IOException;

/**
 * Created by pso on 07/06/17.
 */
public class UberService {

    private static final String RIDE_ESTIMATE_PRICE = "https://api.uber.com/v1.2/estimates/price";
    private static final String RIDE_ESTIMATE_TIME = "https://api.uber.com/v1.2/estimates/time";

    public UberItinerary getEstimate(GenericLocation from, GenericLocation to) {
        try {
            UberETAResponse etaResponse = getEstimateETA(from);
            UberPricesResponse priceResponse = getEstimatePrice(from, to);

            UberETA eta = etaResponse.getETA();
            UberPriceEstimate priceEstimate = priceResponse.getPriceEstimate();

            return UberItinerary.fromApi(priceEstimate, eta);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private UberPricesResponse getEstimatePrice(GenericLocation from, GenericLocation to) throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(
            RIDE_ESTIMATE_PRICE +
                    "?start_latitude=" + from.lat +
                    "&start_longitude=" + from.lng +
                    "&end_latitude=" + to.lat +
                    "&end_longitude=" + to.lng
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

    private UberETAResponse getEstimateETA(GenericLocation from) throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(
                RIDE_ESTIMATE_TIME +
                        "?start_latitude=" + from.lat +
                        "&start_longitude=" + from.lng
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
