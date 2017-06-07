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
import org.opentripplanner.common.model.GenericLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by pso on 07/06/17.
 */
public class PODService {

    private static final String RIDE_REQUEST_ENDPOINT = "https://dart.tapridemobile.com/api/v1/passenger/request";

    public void getAvailableRides(GenericLocation from, GenericLocation to) {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(RIDE_REQUEST_ENDPOINT);

        try {
            List<NameValuePair> formparams = new ArrayList<>();
            formparams.add(new BasicNameValuePair("username", "pjp.soares@gmail.com"));
            formparams.add(new BasicNameValuePair("key", "HLR/NjCPFfQhzs7J9w3CPC7wmNF66fpJI4EL4GjzKN0="));
            formparams.add(new BasicNameValuePair("pickupLat", from.lat.toString()));
            formparams.add(new BasicNameValuePair("pickupLon", from.lng.toString()));
            formparams.add(new BasicNameValuePair("dropoffLat", to.lat.toString()));
            formparams.add(new BasicNameValuePair("dropoffLon", to.lng.toString()));
            formparams.add(new BasicNameValuePair("passengers", "1"));

            httpPost.setEntity(new UrlEncodedFormEntity(formparams));

            CloseableHttpResponse response = client.execute(httpPost);
            Gson g = new Gson();
            Map a = g.fromJson(EntityUtils.toString(response.getEntity()), Map.class);

            client.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
