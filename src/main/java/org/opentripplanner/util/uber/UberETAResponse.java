package org.opentripplanner.util.uber;

import java.util.List;

/**
 * Created by pso on 07/06/17.
 */
public class UberETAResponse {
    public List<UberETA> times;

    public UberETA getETA() {
        if (times == null || times.size() == 0) {
            return null;
        }

        return times.get(0);
    }
}
