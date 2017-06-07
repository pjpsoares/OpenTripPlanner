package org.opentripplanner.util.uber;

/**
 * Created by pso on 07/06/17.
 */
public class UberPriceEstimate {
    public String currency_code; // ISO 4217
    public int low_estimate;
    public int high_estimate;
    public int duration;
    public float distance; // miles
}
