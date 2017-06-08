package org.opentripplanner.api.resource;

/**
 * Created by pso on 08/06/17.
 */
public enum TicketType {
    DART_LOCAL(2.5),
    DART_REGIONAL(5),
    THE_T_REGIONAL(5),
    THE_T_LOCAL(1.75);

    public double value;

    TicketType(double value) {
        this.value = value;
    }
}

