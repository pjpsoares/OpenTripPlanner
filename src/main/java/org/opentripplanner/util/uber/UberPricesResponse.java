package org.opentripplanner.util.uber;

import java.util.List;

/**
 * Created by pso on 07/06/17.
 */
public class UberPricesResponse {
    public List<UberPriceEstimate> prices;

    public UberPriceEstimate getPriceEstimate() {
        if (prices == null || prices.size() == 0) {
            return null;
        }

        return prices.get(0);
    }
}
