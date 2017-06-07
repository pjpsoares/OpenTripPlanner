package org.opentripplanner.util.uber;

/**
 * Created by pso on 07/06/17.
 */
public class UberItinerary {

    public String currencyCode; // ISO 4217
    public int lowPriceEstimate;
    public int highPriceEstimate;
    public int durationEstimate;
    public float distanceEstimate; // miles
    public int estimateForPickup; // seconds

    public UberItinerary(
            String currency_code,
            int low_estimate,
            int high_estimate,
            int duration,
            float distance,
            int estimate
    ) {
        this.currencyCode = currency_code;
        this.lowPriceEstimate = low_estimate;
        this.highPriceEstimate = high_estimate;
        this.durationEstimate = duration;
        this.distanceEstimate = distance;
        this.estimateForPickup = estimate;
    }

    public static UberItinerary fromApi(UberPriceEstimate priceEstimate, UberETA eta) {
        if (priceEstimate == null || eta == null) {
            return null;
        }

        return new UberItinerary(
                priceEstimate.currency_code,
                priceEstimate.low_estimate,
                priceEstimate.high_estimate,
                priceEstimate.duration,
                priceEstimate.distance,
                eta.estimate
        );
    }

}
