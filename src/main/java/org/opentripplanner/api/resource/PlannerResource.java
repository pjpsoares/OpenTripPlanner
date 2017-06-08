/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package org.opentripplanner.api.resource;

import com.google.common.collect.Lists;
import org.glassfish.grizzly.http.server.Request;
import org.opentripplanner.api.common.RoutingResource;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.api.model.TripPlan;
import org.opentripplanner.api.model.error.PlannerError;
import org.opentripplanner.routing.core.Fare;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.impl.GraphPathFinder;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.standalone.Router;
import org.opentripplanner.util.pod.PODResponse;
import org.opentripplanner.util.pod.PODService;
import org.opentripplanner.util.uber.UberItinerary;
import org.opentripplanner.util.uber.UberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.util.*;

import static org.opentripplanner.api.resource.ServerInfo.Q;

/**
 * This is the primary entry point for the trip planning web service.
 * All parameters are passed in the query string. These parameters are defined as fields in the abstract
 * RoutingResource superclass, which also has methods for building routing requests from query
 * parameters. This allows multiple web services to have the same set of query parameters.
 * In order for inheritance to work, the REST resources are request-scoped (constructed at each request)
 * rather than singleton-scoped (a single instance existing for the lifetime of the OTP server).
 */
@Path("routers/{routerId}/plan") // final element needed here rather than on method to distinguish from routers API
public class PlannerResource extends RoutingResource {

    private static final Logger LOG = LoggerFactory.getLogger(PlannerResource.class);
    private static final int SMALL_WALKING_DISTANCE = 1200;

    // We inject info about the incoming request so we can include the incoming query
    // parameters in the outgoing response. This is a TriMet requirement.
    // Jersey uses @Context to inject internal types and @InjectParam or @Resource for DI objects.
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML + Q, MediaType.TEXT_XML + Q })
    public Response plan(@Context UriInfo uriInfo, @Context Request grizzlyRequest) {

        /*
         * TODO: add Lang / Locale parameter, and thus get localized content (Messages & more...)
         * TODO: from/to inputs should be converted / geocoded / etc... here, and maybe send coords 
         *       or vertex ids to planner (or error back to user)
         * TODO: org.opentripplanner.routing.module.PathServiceImpl has COOORD parsing. Abstract that
         *       out so it's used here too...
         */

        // Create response object, containing a copy of all request parameters. Maybe they should be in the debug section of the response.
        Response response = new Response(uriInfo);
        RoutingRequest request = null;
        Router router = null;
        try {

            /* Fill in request fields from query parameters via shared superclass method, catching any errors. */
            request = super.buildRequest();
            router = otpServer.getRouter(request.routerId);

            /* Find some good GraphPaths through the OTP Graph. */
            GraphPathFinder gpFinder = new GraphPathFinder(router); // we could also get a persistent router-scoped GraphPathFinder but there's no setup cost here

            request.maxWalkDistance = SMALL_WALKING_DISTANCE;
            request.maxTransferWalkDistance = SMALL_WALKING_DISTANCE;

            TripPlan planForSmallWalking = null;
            try {
                List<GraphPath> pathsForSmallWalking = gpFinder.graphPathFinderEntryPoint(request);
                planForSmallWalking = GraphPathToTripPlanConverter.generatePlan(pathsForSmallWalking, request);
            } catch (Exception e) {

            }

            request.maxWalkDistance = Integer.MAX_VALUE;
            request.maxTransferWalkDistance = Integer.MAX_VALUE;

            List<GraphPath> pathsForHugeWalking = gpFinder.graphPathFinderEntryPoint(request);
            TripPlan planForHugeWalking = GraphPathToTripPlanConverter.generatePlan(pathsForHugeWalking, request);

            List<Itinerary> newItineraries = new ArrayList<>();

            if (planForSmallWalking != null) {
                newItineraries = planForSmallWalking.itinerary;
            }

            for (Itinerary itinerary : planForHugeWalking.itinerary) {
                if (itinerary.walkDistance > SMALL_WALKING_DISTANCE) {
                    // UBER
                    Itinerary newItineraryForUber = copyItinerary(itinerary);

                    for (Leg leg : newItineraryForUber.legs) {
                        if (leg.mode == "WALK" && leg.distance > SMALL_WALKING_DISTANCE) {
                            leg.mode = "UBER";

                            UberItinerary uberPart = new UberService().getEstimate(leg.from, leg.to);
                            leg.distance = Double.valueOf(uberPart.distanceEstimate * 1609.344);
                            leg.endTime = (Calendar) leg.startTime.clone();
                            leg.endTime.add(Calendar.SECOND, uberPart.durationEstimate);
                            leg.minPrice = uberPart.lowPriceEstimate;
                            leg.maxPrice = uberPart.highPriceEstimate;
                        }
                    }

                    newItineraries.add(newItineraryForUber);

                    // POD
                    Itinerary newItineraryForPOD = copyItinerary(itinerary);
                    boolean addedPOD = false;
                    boolean failedToAddPOD = false;

                    for (Leg leg : newItineraryForPOD.legs) {
                        if (leg.mode == "WALK" && leg.distance > SMALL_WALKING_DISTANCE) {

                            PODResponse podResponse = new PODService().getAvailableRides(leg.from, leg.to);

                            if (podResponse != null && podResponse.is_valid) {
                                addedPOD = true;
                                leg.mode = "POD";
                                leg.startTime.add(Calendar.MINUTE, podResponse.pickup_minutes);
                                leg.endTime = (Calendar) leg.startTime.clone();
                                leg.endTime.add(Calendar.MINUTE, podResponse.travel_minutes);
                            } else {
                                failedToAddPOD = true;
                            }
                        }
                    }

                    if (!failedToAddPOD && addedPOD) {
                        newItineraries.add(newItineraryForPOD);
                    }
                }
            }

            newItineraries.addAll(getFullItineraries(request, gpFinder));
            planForHugeWalking.itinerary = newItineraries;

            addPricingInformation(planForHugeWalking);

            response.setPlan(planForHugeWalking);
        } catch (Exception e) {
            PlannerError error = new PlannerError(e);
            if(!PlannerError.isPlanningError(e.getClass()))
                LOG.warn("Error while planning path: ", e);
            response.setError(error);
        } finally {
            if (request != null) {
                if (request.rctx != null) {
                    response.debugOutput = request.rctx.debugOutput;
                }
                request.cleanup(); // TODO verify that this cleanup step is being done on Analyst web services
            }
        }

        /* Populate up the elevation metadata */
        response.elevationMetadata = new ElevationMetadata();
        response.elevationMetadata.ellipsoidToGeoidDifference = router.graph.ellipsoidToGeoidDifference;
        response.elevationMetadata.geoidElevation = request.geoidElevation;

        return response;
    }

    private List<Itinerary> getFullItineraries(RoutingRequest request, GraphPathFinder gpFinder) {
        List<Itinerary> itineraries = new ArrayList<>();

        request.modes = new TraverseModeSet(TraverseMode.CAR);
        List<GraphPath> pathsForDriving= gpFinder.graphPathFinderEntryPoint(request);
        TripPlan planForDriving = GraphPathToTripPlanConverter.generatePlan(pathsForDriving, request);

        Itinerary itineraryForDriving = planForDriving.itinerary.get(0);

        Itinerary itineraryForUber = copyItinerary(itineraryForDriving);
        Leg uberLeg = itineraryForUber.legs.get(0);
        UberItinerary uberItinerary = new UberService().getEstimate(request.from, request.to);
        uberLeg.distance = Double.valueOf(uberItinerary.distanceEstimate * 1609.344);
        uberLeg.startTime.add(Calendar.SECOND, uberItinerary.estimateForPickup);
        uberLeg.endTime = (Calendar) uberLeg.startTime.clone();
        uberLeg.endTime.add(Calendar.SECOND, uberItinerary.durationEstimate);
        uberLeg.minPrice = uberItinerary.lowPriceEstimate;
        uberLeg.maxPrice = uberItinerary.highPriceEstimate;
        uberLeg.mode = "UBER";
        itineraries.add(itineraryForUber);

        PODResponse podItinerary = new PODService().getAvailableRides(request.from, request.to);
        if (podItinerary != null && podItinerary.is_valid) {
            Itinerary itineraryForPOD = copyItinerary(itineraryForDriving);
            Leg podLeg = itineraryForPOD.legs.get(0);

            podLeg.mode = "POD";
            podLeg.startTime.add(Calendar.MINUTE, podItinerary.pickup_minutes);
            podLeg.endTime = (Calendar) podLeg.startTime.clone();
            podLeg.endTime.add(Calendar.MINUTE, podItinerary.travel_minutes);

            itineraries.add(itineraryForPOD);
        }

        return itineraries;
    }

    private Itinerary copyItinerary(Itinerary oldItinerary) {
        Itinerary newItinerary = new Itinerary();

        newItinerary.duration = oldItinerary.duration;
        newItinerary.startTime = oldItinerary.startTime;
        newItinerary.endTime = oldItinerary.endTime;
        newItinerary.walkTime = oldItinerary.walkTime;
        newItinerary.transitTime = oldItinerary.transitTime;
        newItinerary.waitingTime = oldItinerary.waitingTime;
        newItinerary.walkDistance = oldItinerary.walkDistance;
        newItinerary.walkLimitExceeded = false;
        newItinerary.elevationLost = oldItinerary.elevationLost;
        newItinerary.elevationGained = oldItinerary.elevationGained;
        newItinerary.transfers = oldItinerary.transfers;
        newItinerary.tooSloped = oldItinerary.tooSloped;
        newItinerary.legs = new ArrayList<>();
        for (Leg leg : oldItinerary.legs) {
            Leg newLeg = new Leg();

            newLeg.startTime = leg.startTime;
            newLeg.endTime = leg.endTime;
            newLeg.departureDelay = leg.departureDelay;
            newLeg.arrivalDelay = leg.arrivalDelay;
            newLeg.realTime = leg.realTime;
            newLeg.isNonExactFrequency = leg.isNonExactFrequency;
            newLeg.headway = leg.headway;
            newLeg.distance = leg.distance;
            newLeg.pathway = leg.pathway;
            newLeg.mode = leg.mode;
            newLeg.route = leg.route;
            newLeg.agencyName = leg.agencyName;
            newLeg.agencyUrl = leg.agencyUrl;
            newLeg.agencyBrandingUrl = leg.agencyBrandingUrl;
            newLeg.agencyTimeZoneOffset = leg.agencyTimeZoneOffset;
            newLeg.routeColor = leg.routeColor;
            newLeg.routeType = leg.routeType;
            newLeg.routeId = leg.routeId;
            newLeg.routeTextColor = leg.routeTextColor;
            newLeg.interlineWithPreviousLeg = leg.interlineWithPreviousLeg;
            newLeg.tripShortName = leg.tripShortName;
            newLeg.tripBlockId = leg.tripBlockId;
            newLeg.headsign = leg.headsign;
            newLeg.agencyId = leg.agencyId;
            newLeg.tripId = leg.tripId;
            newLeg.serviceDate = leg.serviceDate;
            newLeg.routeBrandingUrl = leg.routeBrandingUrl;
            newLeg.from = leg.from;
            newLeg.to = leg.to;
            newLeg.stop = leg.stop;
            newLeg.legGeometry = leg.legGeometry;
            newLeg.walkSteps = leg.walkSteps;
            newLeg.alerts = leg.alerts;
            newLeg.routeShortName = leg.routeShortName;
            newLeg.routeLongName = leg.routeLongName;
            newLeg.boardRule = leg.boardRule;
            newLeg.alightRule = leg.alightRule;

            newItinerary.legs.add(newLeg);
        }

        return newItinerary;
    }

    private void addPricingInformation(TripPlan plan) {
        for (Itinerary itinerary : plan.itinerary) {
            TicketType ticketType = getTicketType(itinerary);
            itinerary.setTicketType(ticketType);

            fillPrice(itinerary);
        }
    }

    private void fillPrice(Itinerary itinerary) {
        itinerary.minPrice = itinerary.ticketType == null ? 0 : itinerary.ticketType.value;
        itinerary.maxPrice = itinerary.ticketType == null ? 0 : itinerary.ticketType.value;

        for (Leg leg : itinerary.legs) {
            itinerary.minPrice += leg.minPrice;
            itinerary.maxPrice += leg.maxPrice;
        }
    }

    public TicketType getTicketType(Itinerary itinerary) {
        List<PublicTransportationCompany> agencies = new ArrayList<>();
        boolean hasPOD = false;

        for (Leg leg : itinerary.legs) {
            if (leg.agencyId != null) {
                agencies.add(getTransportCompany(leg.route));
            } else if (leg.mode == "POD") {
                hasPOD = true;
            }
        }

        if (hasPOD) {
            return agencies.size() > 0 ? TicketType.POD_COMBINED : TicketType.POD_ONLY;
        }

        if (agencies.size() == 0) {
            return null;
        }

        PublicTransportationCompany startCompany = agencies.get(0);
        PublicTransportationCompany endCompany = agencies.get(agencies.size() - 1);

        if(startCompany == PublicTransportationCompany.DART) {
            return endCompany == PublicTransportationCompany.DART ? TicketType.DART_LOCAL : TicketType.DART_REGIONAL;
        } else if (startCompany == PublicTransportationCompany.THE_T) {
            return endCompany == PublicTransportationCompany.THE_T ? TicketType.THE_T_LOCAL : TicketType.THE_T_REGIONAL;
        }

        return null;
    }

    private PublicTransportationCompany getTransportCompany(String route) {
        return route.equals("TRE") ? PublicTransportationCompany.THE_T : PublicTransportationCompany.DART;
    }
}
