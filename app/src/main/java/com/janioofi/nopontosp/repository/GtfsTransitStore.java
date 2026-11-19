package com.janioofi.nopontosp.repository;

import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Leitor local dos horários programados publicados no GTFS da SPTrans. */
public final class GtfsTransitStore {

    public enum DayType { WEEKDAY, SATURDAY, SUNDAY, HOLIDAY }
    public enum TimePeriod {
        ALL, DAWN, MORNING, AFTERNOON, EVENING;

        public boolean matches(int timeSeconds) {
            return this == ALL || inPeriod(this, timeSeconds);
        }
    }

    public static final class Stop {
        public final String id;
        public final String name;
        public final String description;
        public final double latitude;
        public final double longitude;

        private Stop(String id, String name, String description, double latitude, double longitude) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    public static final class Route {
        public final String id;
        public final String shortName;
        public final String longName;

        private Route(String id, String shortName, String longName) {
            this.id = id;
            this.shortName = shortName;
            this.longName = longName;
        }

        @Override public String toString() {
            return shortName + " · " + longName;
        }
    }

    public static final class Direction {
        public final String key;
        public final String origin;
        public final String destination;
        public final String label;

        private Direction(String key, String origin, String destination) {
            this.key = key;
            this.origin = origin;
            this.destination = destination;
            this.label = origin + " → " + destination;
        }

        @Override public String toString() { return label; }
    }

    public static final class Departure {
        public final int timeSeconds;
        public final Route route;
        public final String origin;
        public final String destination;
        public final String stopName;

        private Departure(int timeSeconds, Route route, String origin, String destination,
                          String stopName) {
            this.timeSeconds = timeSeconds;
            this.route = route;
            this.origin = origin;
            this.destination = destination;
            this.stopName = stopName;
        }
    }

    private static volatile GtfsTransitStore instance;

    private final Map<String, Stop> stops;
    private final Map<String, Route> routes;
    private final Map<String, Trip> trips;
    private final Map<String, ServiceCalendar> calendars;
    private final Map<String, List<Frequency>> frequenciesByTrip;
    private final Map<String, List<StopTime>> stopTimesByStop;
    private final Map<String, List<StopTime>> stopTimesByTrip;
    private final Map<String, List<Trip>> tripsByRoute;

    private GtfsTransitStore(AssetManager assets) throws IOException {
        stops = new LinkedHashMap<>();
        routes = new LinkedHashMap<>();
        trips = new HashMap<>();
        calendars = new HashMap<>();
        frequenciesByTrip = new HashMap<>();
        stopTimesByStop = new HashMap<>();
        stopTimesByTrip = new HashMap<>();
        tripsByRoute = new HashMap<>();
        readRoutes(assets);
        readStops(assets);
        readCalendars(assets);
        readTrips(assets);
        readStopTimes(assets);
        readFrequencies(assets);
    }

    public static GtfsTransitStore load(AssetManager assets) throws IOException {
        GtfsTransitStore cached = instance;
        if (cached != null) return cached;
        synchronized (GtfsTransitStore.class) {
            if (instance == null) instance = new GtfsTransitStore(assets);
            return instance;
        }
    }

    public List<Stop> getStops() {
        return Collections.unmodifiableList(new ArrayList<>(stops.values()));
    }

    public List<Route> getRoutes() {
        return Collections.unmodifiableList(new ArrayList<>(routes.values()));
    }

    public Stop getStop(String id) { return stops.get(id); }

    public List<Route> getRoutesForStop(String stopId) {
        Map<String, Route> result = new LinkedHashMap<>();
        for (StopTime stopTime : listOrEmpty(stopTimesByStop.get(stopId))) {
            Trip trip = trips.get(stopTime.tripId);
            Route route = trip == null ? null : routes.get(trip.routeId);
            if (route != null) result.put(route.id, route);
        }
        List<Route> sorted = new ArrayList<>(result.values());
        sorted.sort(Comparator.comparing(route -> route.shortName));
        return sorted;
    }

    public List<Direction> getDirections(String routeId) {
        Map<String, Direction> result = new LinkedHashMap<>();
        for (Trip trip : listOrEmpty(tripsByRoute.get(routeId))) {
            List<StopTime> times = listOrEmpty(stopTimesByTrip.get(trip.id));
            if (times.isEmpty()) continue;
            StopTime first = times.get(0);
            Stop origin = stops.get(first.stopId);
            String originName = origin == null ? first.stopId : origin.name;
            String destination = trip.headsign.isEmpty() ? routeDestination(routeId) : trip.headsign;
            String key = directionKey(trip, first.stopId);
            result.put(key, new Direction(key, originName, destination));
        }
        List<Direction> sorted = new ArrayList<>(result.values());
        sorted.sort(Comparator.comparing(direction -> direction.label));
        return sorted;
    }

    public List<Departure> getDepartures(String routeId, String selectedDirection,
                                         DayType dayType, TimePeriod period) {
        List<Departure> result = new ArrayList<>();
        for (Trip trip : listOrEmpty(tripsByRoute.get(routeId))) {
            if (!isServiceActive(trip.serviceId, dayType)) continue;
            List<StopTime> times = listOrEmpty(stopTimesByTrip.get(trip.id));
            if (times.isEmpty()) continue;
            StopTime first = times.get(0);
            if (selectedDirection != null
                    && !selectedDirection.equals(directionKey(trip, first.stopId))) continue;
            Route route = routes.get(trip.routeId);
            Stop origin = stops.get(first.stopId);
            String originName = origin == null ? first.stopId : origin.name;
            String destination = trip.headsign.isEmpty() ? routeDestination(routeId) : trip.headsign;
            List<Frequency> frequencies = listOrEmpty(frequenciesByTrip.get(trip.id));
            if (frequencies.isEmpty()) {
                addIfInPeriod(result, first.departureSeconds, period, route, originName, destination,
                        originName);
            } else {
                for (Frequency frequency : frequencies) {
                    for (int time = frequency.startSeconds;
                         time < frequency.endSeconds; time += frequency.headwaySeconds) {
                        addIfInPeriod(result, time, period, route, originName, destination, originName);
                    }
                }
            }
        }
        sortDepartures(result);
        return result;
    }

    public List<Departure> getUpcomingArrivals(String stopId, String routeId, DayType dayType,
                                               int nowSeconds) {
        List<Departure> result = new ArrayList<>();
        Stop selectedStop = stops.get(stopId);
        String stopName = selectedStop == null ? stopId : selectedStop.name;
        for (StopTime stopTime : listOrEmpty(stopTimesByStop.get(stopId))) {
            Trip trip = trips.get(stopTime.tripId);
            if (trip == null || (routeId != null && !routeId.equals(trip.routeId))
                    || !isServiceActive(trip.serviceId, dayType)) continue;
            Route route = routes.get(trip.routeId);
            List<StopTime> tripTimes = listOrEmpty(stopTimesByTrip.get(trip.id));
            if (tripTimes.isEmpty()) continue;
            StopTime first = tripTimes.get(0);
            Stop origin = stops.get(first.stopId);
            String originName = origin == null ? first.stopId : origin.name;
            String destination = trip.headsign.isEmpty()
                    ? routeDestination(trip.routeId) : trip.headsign;
            List<Frequency> frequencies = listOrEmpty(frequenciesByTrip.get(trip.id));
            if (frequencies.isEmpty()) {
                addArrivalIfUpcoming(result, stopTime.arrivalSeconds, nowSeconds, route,
                        originName, destination, stopName);
            } else {
                int offset = stopTime.arrivalSeconds - first.departureSeconds;
                if (offset < 0) continue;
                for (Frequency frequency : frequencies) {
                    for (int start = frequency.startSeconds;
                         start < frequency.endSeconds; start += frequency.headwaySeconds) {
                        int arrival = start + offset;
                        addArrivalIfUpcoming(result, arrival, nowSeconds, route,
                                originName, destination, stopName);
                    }
                }
            }
        }
        sortDepartures(result);
        return result;
    }

    private boolean isServiceActive(String serviceId, DayType dayType) {
        ServiceCalendar calendar = calendars.get(serviceId);
        return calendar != null && calendar.runsOn(dayType);
    }

    private String routeDestination(String routeId) {
        Route route = routes.get(routeId);
        return route == null ? "" : route.longName;
    }

    private static String directionKey(Trip trip, String firstStopId) {
        return trip.directionId + "|" + firstStopId + "|" + trip.headsign;
    }

    private static void addIfInPeriod(List<Departure> result, int time, TimePeriod period,
                                      Route route, String origin, String destination, String stop) {
        if (period == TimePeriod.ALL || period.matches(time)) {
            result.add(new Departure(time, route, origin, destination, stop));
        }
    }

    private static void addArrivalIfUpcoming(List<Departure> result, int time, int nowSeconds,
                                             Route route, String origin, String destination,
                                             String stop) {
        if (time >= nowSeconds) {
            result.add(new Departure(time, route, origin, destination, stop));
        }
    }

    private static void sortDepartures(List<Departure> departures) {
        departures.sort(Comparator.comparingInt((Departure departure) -> departure.timeSeconds)
                .thenComparing(departure -> departure.route == null ? "" : departure.route.shortName));
    }

    private void readRoutes(AssetManager assets) throws IOException {
        readCsv(assets, "routes.txt", false, row -> {
            if (row.size() < 4) return;
            routes.put(row.get(0), new Route(row.get(0), row.get(2), row.get(3)));
        });
    }

    private void readStops(AssetManager assets) throws IOException {
        readCsv(assets, "stops.txt", false, row -> {
            if (row.size() < 5) return;
            try {
                double latitude = Double.parseDouble(row.get(3));
                double longitude = Double.parseDouble(row.get(4));
                if (latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180) {
                    stops.put(row.get(0), new Stop(row.get(0), row.get(1), row.get(2),
                            latitude, longitude));
                }
            } catch (NumberFormatException ignored) {
                // Skip malformed coordinates without discarding the rest of the GTFS feed.
            }
        });
    }

    private void readCalendars(AssetManager assets) throws IOException {
        readCsv(assets, "calendar.txt", false, row -> {
            if (row.size() < 8) return;
            calendars.put(row.get(0), new ServiceCalendar(row));
        });
    }

    private void readTrips(AssetManager assets) throws IOException {
        readCsv(assets, "trips.txt", false, row -> {
            if (row.size() < 5) return;
            Trip trip = new Trip(row.get(0), row.get(1), row.get(2), row.get(3), row.get(4));
            trips.put(trip.id, trip);
            tripsByRoute.computeIfAbsent(trip.routeId, ignored -> new ArrayList<>()).add(trip);
        });
    }

    private void readStopTimes(AssetManager assets) throws IOException {
        readCsv(assets, "stop_times.txt", false, row -> {
            if (row.size() < 5) return;
            try {
                StopTime stopTime = new StopTime(row.get(0), parseTime(row.get(1)),
                        parseTime(row.get(2)), row.get(3), Integer.parseInt(row.get(4)));
                stopTimesByStop.computeIfAbsent(stopTime.stopId, ignored -> new ArrayList<>())
                        .add(stopTime);
                stopTimesByTrip.computeIfAbsent(stopTime.tripId, ignored -> new ArrayList<>())
                        .add(stopTime);
            } catch (NumberFormatException ignored) {
                // Skip malformed timetable rows.
            }
        });
        Comparator<StopTime> bySequence = Comparator.comparingInt(time -> time.sequence);
        for (List<StopTime> times : stopTimesByTrip.values()) times.sort(bySequence);
    }

    private void readFrequencies(AssetManager assets) throws IOException {
        readCsv(assets, "frequencies.txt", true, row -> {
            if (row.size() < 4) return;
            try {
                Frequency frequency = new Frequency(row.get(0), parseTime(row.get(1)),
                        parseTime(row.get(2)), Integer.parseInt(row.get(3)));
                if (frequency.headwaySeconds > 0) {
                    frequenciesByTrip.computeIfAbsent(frequency.tripId, ignored -> new ArrayList<>())
                            .add(frequency);
                }
            } catch (NumberFormatException ignored) {
                // Skip malformed frequency rows.
            }
        });
    }

    private static void readCsv(AssetManager assets, String name, boolean optional,
                                CsvRowConsumer consumer) throws IOException {
        InputStreamAndCompression input;
        try {
            input = openGtfsTable(assets, name);
        } catch (FileNotFoundException missing) {
            if (optional) return;
            throw missing;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                input.compressed ? new GZIPInputStream(input.stream) : input.stream,
                StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null) return;
            while ((line = reader.readLine()) != null) consumer.accept(parseCsvRow(line));
        }
    }

    private static InputStreamAndCompression openGtfsTable(AssetManager assets, String name)
            throws IOException {
        try {
            return new InputStreamAndCompression(
                    assets.open("gtfs/tables/" + name.replace(".txt", ".csv.gz")), true);
        } catch (FileNotFoundException missingCompressed) {
            try {
                return new InputStreamAndCompression(assets.open("gtfs/" + name + ".gz"), true);
            } catch (FileNotFoundException missingGtfsTable) {
                try {
                    return new InputStreamAndCompression(assets.open("gtfs/" + name), false);
                } catch (FileNotFoundException missingLegacyTable) {
                    try {
                        return new InputStreamAndCompression(assets.open(name), false);
                    } catch (FileNotFoundException missingRootTable) {
                        missingRootTable.addSuppressed(missingCompressed);
                        missingRootTable.addSuppressed(missingGtfsTable);
                        missingRootTable.addSuppressed(missingLegacyTable);
                        throw missingRootTable;
                    }
                }
            }
        }
    }

    private static List<String> parseCsvRow(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    private static int parseTime(String value) {
        String[] parts = value.split(":");
        if (parts.length != 3) throw new NumberFormatException("Invalid GTFS time");
        return Integer.parseInt(parts[0]) * 3600
                + Integer.parseInt(parts[1]) * 60
                + Integer.parseInt(parts[2]);
    }

    private static <T> List<T> listOrEmpty(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    public static String formatTime(int timeSeconds) {
        int hours = timeSeconds / 3600;
        int minutes = (timeSeconds % 3600) / 60;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    public static DayType dayTypeForCalendarDay(int calendarDayOfWeek) {
        if (calendarDayOfWeek == java.util.Calendar.SATURDAY) return DayType.SATURDAY;
        if (calendarDayOfWeek == java.util.Calendar.SUNDAY) return DayType.SUNDAY;
        return DayType.WEEKDAY;
    }

    private interface CsvRowConsumer {
        void accept(List<String> row);
    }

    private static final class InputStreamAndCompression {
        final java.io.InputStream stream;
        final boolean compressed;

        InputStreamAndCompression(java.io.InputStream stream, boolean compressed) {
            this.stream = stream;
            this.compressed = compressed;
        }
    }

    private static final class Trip {
        final String routeId;
        final String serviceId;
        final String id;
        final String headsign;
        final String directionId;

        Trip(String routeId, String serviceId, String id, String headsign, String directionId) {
            this.routeId = routeId;
            this.serviceId = serviceId;
            this.id = id;
            this.headsign = headsign;
            this.directionId = directionId;
        }
    }

    private static final class StopTime {
        final String tripId;
        final int arrivalSeconds;
        final int departureSeconds;
        final String stopId;
        final int sequence;

        StopTime(String tripId, int arrivalSeconds, int departureSeconds, String stopId,
                 int sequence) {
            this.tripId = tripId;
            this.arrivalSeconds = arrivalSeconds;
            this.departureSeconds = departureSeconds;
            this.stopId = stopId;
            this.sequence = sequence;
        }
    }

    private static final class Frequency {
        final String tripId;
        final int startSeconds;
        final int endSeconds;
        final int headwaySeconds;

        Frequency(String tripId, int startSeconds, int endSeconds, int headwaySeconds) {
            this.tripId = tripId;
            this.startSeconds = startSeconds;
            this.endSeconds = endSeconds;
            this.headwaySeconds = headwaySeconds;
        }
    }

    private static final class ServiceCalendar {
        final boolean[] weekdays = new boolean[7];

        ServiceCalendar(List<String> row) {
            for (int i = 0; i < weekdays.length; i++) weekdays[i] = "1".equals(row.get(i + 1));
        }

        boolean runsOn(DayType dayType) {
            if (dayType == DayType.SATURDAY) return weekdays[5];
            if (dayType == DayType.SUNDAY || dayType == DayType.HOLIDAY) return weekdays[6];
            for (int i = 0; i < 5; i++) if (weekdays[i]) return true;
            return false;
        }
    }

    private enum ClockPeriod {
        DAWN(0, 6), MORNING(6, 12), AFTERNOON(12, 18), EVENING(18, 24);

        final int startHour;
        final int endHour;
        ClockPeriod(int startHour, int endHour) {
            this.startHour = startHour;
            this.endHour = endHour;
        }

        boolean contains(int seconds) {
            int hourOfDay = (seconds / 3600) % 24;
            return hourOfDay >= startHour && hourOfDay < endHour;
        }
    }

    private static boolean inPeriod(TimePeriod period, int seconds) {
        switch (period) {
            case DAWN: return ClockPeriod.DAWN.contains(seconds);
            case MORNING: return ClockPeriod.MORNING.contains(seconds);
            case AFTERNOON: return ClockPeriod.AFTERNOON.contains(seconds);
            case EVENING: return ClockPeriod.EVENING.contains(seconds);
            default: return true;
        }
    }
}
