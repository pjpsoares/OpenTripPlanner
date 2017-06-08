package org.opentripplanner.util.pod;

import com.google.gson.Gson;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.opentripplanner.api.model.Place;
import org.opentripplanner.common.model.GenericLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by pso on 07/06/17.
 */
public class PODService {

    private static final String RIDE_REQUEST_ENDPOINT = "https://dart.tapridemobile.com/api/v1/passenger/estimate";

    public PODResponse getAvailableRides(GenericLocation from, GenericLocation to) {
        return getAvailableRides(from.lat, from.lng, to.lat, to.lng);
    }

    public PODResponse getAvailableRides(Place from, Place to) {
        return getAvailableRides(from.lat, from.lon, to.lat, to.lon);
    }

    public PODResponse getAvailableRides(Double fromLat, Double fromLng, Double toLat, Double toLng) {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(RIDE_REQUEST_ENDPOINT);

        try {
            List<NameValuePair> formparams = new ArrayList<>();
            formparams.add(new BasicNameValuePair("username", "pjp.soares@gmail.com"));
            formparams.add(new BasicNameValuePair("key", "HLR/NjCPFfQhzs7J9w3CPC7wmNF66fpJI4EL4GjzKN0="));
            formparams.add(new BasicNameValuePair("pickupLat", fromLat.toString()));
            formparams.add(new BasicNameValuePair("pickupLon", fromLng.toString()));
            formparams.add(new BasicNameValuePair("dropoffLat", toLat.toString()));
            formparams.add(new BasicNameValuePair("dropoffLon", toLng.toString()));
            formparams.add(new BasicNameValuePair("passengers", "1"));

            httpPost.setEntity(new UrlEncodedFormEntity(formparams));

            CloseableHttpResponse response = client.execute(httpPost);
            Gson g = new Gson();

            return g.fromJson(EntityUtils.toString(response.getEntity()), PODResponse.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
