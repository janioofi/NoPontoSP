package com.janioofi.nopontosp.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.janioofi.nopontosp.R;
import com.janioofi.nopontosp.domain.Veiculo;
import com.janioofi.nopontosp.repository.OlhoVivoRepository;
import com.janioofi.nopontosp.repository.RepositoryCallback;
import com.janioofi.nopontosp.ui.MapaActivity;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-started foreground monitoring for a selected line and stop. */
public class TransitAlertService extends Service {
    public static final String ACTION_START = "com.janioofi.nopontosp.action.START_ALERT";
    public static final String ACTION_STOP = "com.janioofi.nopontosp.action.STOP_ALERT";
    public static final String EXTRA_LINE_CODE = "alert_line_code";
    public static final String EXTRA_DIRECTION = "alert_direction";
    public static final String EXTRA_DESTINATION = "alert_destination";
    public static final String EXTRA_LINE_LABEL = "alert_line_label";
    public static final String EXTRA_STOP_CODE = "alert_stop_code";
    public static final String EXTRA_STOP_NAME = "alert_stop_name";
    public static final String EXTRA_STOP_LAT = "alert_stop_lat";
    public static final String EXTRA_STOP_LON = "alert_stop_lon";

    private static final String CHANNEL_ID = "transit_proximity_alerts";
    private static final int NOTIFICATION_ID = 2401;
    private static final int ARRIVAL_NOTIFICATION_ID = 2402;
    private static final long CHECK_INTERVAL_MS = 30_000L;
    private static volatile boolean active;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean requestInFlight = new AtomicBoolean();
    private OlhoVivoRepository repository;
    private int lineCode;
    private int direction;
    private String destination;
    private String lineLabel;
    private int stopCode;
    private String stopName;
    private double stopLat;
    private double stopLon;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            checkVehicles();
        }
    };

    public static boolean isActive() {
        return active;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        repository = OlhoVivoRepository.getInstance(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopMonitoring();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        lineCode = intent.getIntExtra(EXTRA_LINE_CODE, -1);
        direction = intent.getIntExtra(EXTRA_DIRECTION, -1);
        destination = intent.getStringExtra(EXTRA_DESTINATION);
        lineLabel = intent.getStringExtra(EXTRA_LINE_LABEL);
        stopCode = intent.getIntExtra(EXTRA_STOP_CODE, -1);
        stopName = intent.getStringExtra(EXTRA_STOP_NAME);
        stopLat = intent.getDoubleExtra(EXTRA_STOP_LAT, Double.NaN);
        stopLon = intent.getDoubleExtra(EXTRA_STOP_LON, Double.NaN);
        if (lineCode < 0 || stopCode < 0 || !validCoordinates(stopLat, stopLon)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        active = true;
        startForeground(NOTIFICATION_ID, buildNotification(
                getString(R.string.alert_notification_monitoring, lineLabel, stopName), true));
        handler.removeCallbacks(poll);
        handler.post(poll);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        active = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void checkVehicles() {
        if (!active || !requestInFlight.compareAndSet(false, true)) return;
        repository.posicaoPorLinha(lineCode, direction, destination,
                new RepositoryCallback<List<Veiculo>>() {
                    @Override
                    public void onSuccess(List<Veiculo> vehicles) {
                        handler.post(() -> {
                            requestInFlight.set(false);
                            if (!active) return;
                            for (Veiculo vehicle : vehicles) {
                                if (validCoordinates(vehicle.latitude, vehicle.longitude)
                                        && distanceMeters(stopLat, stopLon,
                                        vehicle.latitude, vehicle.longitude) <= 500) {
                                    sendArrivalNotification();
                                    return;
                                }
                            }
                            handler.postDelayed(poll, CHECK_INTERVAL_MS);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        handler.post(() -> {
                            requestInFlight.set(false);
                            if (!active) return;
                            NotificationManager manager = getSystemService(NotificationManager.class);
                            if (manager != null) {
                                manager.notify(NOTIFICATION_ID, buildNotification(
                                        getString(R.string.alert_notification_error), true));
                            }
                            handler.postDelayed(poll, CHECK_INTERVAL_MS);
                        });
                    }
                });
    }

    private void sendArrivalNotification() {
        handler.removeCallbacksAndMessages(null);
        active = false;
        stopForeground(true);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(ARRIVAL_NOTIFICATION_ID, buildNotification(
                    getString(R.string.alert_notification_body, lineLabel, stopName), false));
        }
        stopSelf();
    }

    private Notification buildNotification(String message, boolean ongoing) {
        Intent openMap = new Intent(this, MapaActivity.class)
                .putExtra(MapaActivity.EXTRA_CODIGO_LINHA, lineCode)
                .putExtra(MapaActivity.EXTRA_SENTIDO, direction)
                .putExtra(MapaActivity.EXTRA_LETREIRO, lineLabel)
                .putExtra(MapaActivity.EXTRA_DESTINO_SENTIDO, destination)
                .putExtra(MapaActivity.EXTRA_CODIGO_PARADA, stopCode)
                .putExtra(MapaActivity.EXTRA_NOME_PARADA, stopName)
                .putExtra(MapaActivity.EXTRA_PARADA_LAT, stopLat)
                .putExtra(MapaActivity.EXTRA_PARADA_LNG, stopLon);
        PendingIntent contentIntent = PendingIntent.getActivity(this, NOTIFICATION_ID,
                openMap, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bus)
                .setContentTitle(getString(R.string.alert_notification_title))
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .setOnlyAlertOnce(ongoing)
                .build();
    }

    private void stopMonitoring() {
        handler.removeCallbacksAndMessages(null);
        active = false;
        stopForeground(true);
        stopSelf();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.alert_notification_channel),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.alert_notification_channel_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private static boolean validCoordinates(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon)
                && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
