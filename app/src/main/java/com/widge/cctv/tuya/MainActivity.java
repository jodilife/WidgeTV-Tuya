
package com.widge.cctv.tuya;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Rational;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout cameraList;
    private VideoView videoView;
    private EditText ipInput, userInput, passInput, nameInput;
    private TextView statusText, helpText;
    private SharedPreferences prefs;
    private String currentRtsp = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cameraList = findViewById(R.id.camera_list);
        videoView = findViewById(R.id.video_view);
        ipInput = findViewById(R.id.ip_input);
        userInput = findViewById(R.id.user_input);
        passInput = findViewById(R.id.pass_input);
        nameInput = findViewById(R.id.name_input);
        statusText = findViewById(R.id.status);
        helpText = findViewById(R.id.help);
        prefs = getSharedPreferences("tuya_cams", MODE_PRIVATE);
        helpText.setText("BARDI & HAN RIVER (Tuya) SIMPLE - VideoView - No ExoPlayer, pasti build hijau! Default Bardi pass 123456, RTSP port 8554");
        loadSavedCameras();
        findViewById(R.id.btn_add_bardi).setOnClickListener(v -> addBardiCamera());
        findViewById(R.id.btn_add_hanriver).setOnClickListener(v -> addHanRiverCamera());
        findViewById(R.id.btn_add_custom).setOnClickListener(v -> addCustomCamera());
        findViewById(R.id.btn_discover).setOnClickListener(v -> discoverTuyaCameras());
        findViewById(R.id.btn_pip).setOnClickListener(v -> enterPip());
        findViewById(R.id.btn_stop).setOnClickListener(v -> stopVideo());
    }

    private void loadSavedCameras() {
        cameraList.removeAllViews();
        Map<String, ?> all = prefs.getAll();
        for (String key : all.keySet()) {
            if (key.startsWith("cam_")) {
                String data = (String) all.get(key);
                String[] parts = data.split("\\|", 5);
                if (parts.length >= 4) {
                    addCameraTile(parts[0], parts[1], parts[2], parts[3], parts.length>4?parts[4]:"Tuya", key);
                }
            }
        }
        if (all.isEmpty()) {
            statusText.setText("Belum ada kamera. Isi IP Bardi/Han River lalu klik Bardi / Han River");
        }
    }

    private void addCameraTile(String ip, String user, String pass, String rtspUrl, String brand, String prefKey) {
        View tile = getLayoutInflater().inflate(R.layout.item_camera, null);
        TextView nameView = tile.findViewById(R.id.cam_name);
        TextView ipView = tile.findViewById(R.id.cam_ip);
        TextView brandView = tile.findViewById(R.id.cam_brand);
        String displayNameTemp = brand + " - " + ip;
        if (brand.equals("Bardi")) displayNameTemp = "Bardi " + ip;
        if (brand.equals("HanRiver")) displayNameTemp = "Han River " + ip;
        final String displayName = displayNameTemp;
        final String finalRtspUrl = rtspUrl;
        nameView.setText(displayName);
        ipView.setText(rtspUrl.length()>45 ? rtspUrl.substring(0,45)+"..." : rtspUrl);
        brandView.setText(brand + " | Tuya | SIMPLE VideoView");
        tile.setFocusable(true);
        tile.setOnClickListener(v -> playRtsp(finalRtspUrl, displayName));
        tile.setOnLongClickListener(v -> {
            prefs.edit().remove(prefKey).apply();
            loadSavedCameras();
            Toast.makeText(this, "Hapus " + ip, Toast.LENGTH_SHORT).show();
            return true;
        });
        cameraList.addView(tile);
    }

    private void addBardiCamera() {
        String ip = ipInput.getText().toString().trim();
        String user = userInput.getText().toString().trim();
        String pass = passInput.getText().toString().trim();
        if (ip.isEmpty()) { Toast.makeText(this, "Isi IP Bardi", Toast.LENGTH_SHORT).show(); return; }
        if (user.isEmpty()) user = "admin";
        if (pass.isEmpty()) pass = "123456";
        String rtsp = "rtsp://" + user + ":" + pass + "@" + ip + ":8554/profile0";
        String key = "cam_" + ip.replace(".", "_") + "_bardi_" + System.currentTimeMillis();
        prefs.edit().putString(key, ip + "|" + user + "|" + pass + "|" + rtsp + "|Bardi").apply();
        addCameraTile(ip, user, pass, rtsp, "Bardi", key);
        playRtsp(rtsp, "Bardi " + ip);
    }

    private void addHanRiverCamera() {
        String ip = ipInput.getText().toString().trim();
        String user = userInput.getText().toString().trim();
        String pass = passInput.getText().toString().trim();
        if (ip.isEmpty()) { Toast.makeText(this, "Isi IP Han River", Toast.LENGTH_SHORT).show(); return; }
        if (user.isEmpty()) user = "admin";
        String rtsp = "rtsp://" + user + ":" + pass + "@" + ip + ":8554/profile0";
        String key = "cam_" + ip.replace(".", "_") + "_hanriver_" + System.currentTimeMillis();
        prefs.edit().putString(key, ip + "|" + user + "|" + pass + "|" + rtsp + "|HanRiver").apply();
        addCameraTile(ip, user, pass, rtsp, "HanRiver", key);
        playRtsp(rtsp, "Han River " + ip);
    }

    private void addCustomCamera() {
        String ip = ipInput.getText().toString().trim();
        String user = userInput.getText().toString().trim();
        String pass = passInput.getText().toString().trim();
        String customUrl = nameInput.getText().toString().trim();
        if (ip.isEmpty() && customUrl.isEmpty()) { Toast.makeText(this, "Isi IP atau RTSP URL", Toast.LENGTH_SHORT).show(); return; }
        String rtsp = customUrl.isEmpty() ? "rtsp://" + user + ":" + pass + "@" + ip + ":554/stream1" : customUrl;
        String key = "cam_" + (ip.isEmpty()?"custom":ip.replace(".", "_")) + "_custom_" + System.currentTimeMillis();
        prefs.edit().putString(key, (ip.isEmpty()?"0.0.0.0":ip) + "|" + user + "|" + pass + "|" + rtsp + "|Custom").apply();
        addCameraTile(ip, user, pass, rtsp, "Custom", key);
        playRtsp(rtsp, "Custom " + ip);
    }

    private void discoverTuyaCameras() {
        statusText.setText("Scan Tuya Bardi Han River 192.168.1.1-254 port 8554 & 554...");
        new AsyncTask<Void, Void, List<String>>() {
            @Override protected List<String> doInBackground(Void... v) {
                List<String> found = new ArrayList<>();
                for (int i=1;i<255;i++) {
                    String ip = "192.168.1." + i;
                    try {
                        java.net.Socket s = new java.net.Socket();
                        s.connect(new java.net.InetSocketAddress(ip, 8554), 150);
                        s.close();
                        found.add(ip + ":8554");
                    } catch (Exception e) {}
                    try {
                        java.net.Socket s = new java.net.Socket();
                        s.connect(new java.net.InetSocketAddress(ip, 554), 150);
                        s.close();
                        if (!found.contains(ip+":554")) found.add(ip+":554");
                    } catch (Exception e) {}
                    if (found.size()>=8) break;
                }
                return found;
            }
            @Override protected void onPostExecute(List<String> ips) {
                statusText.setText("Scan selesai: " + ips.toString());
                for (String ipPort : ips) {
                    String ip = ipPort.split(":")[0];
                    String key = "cam_" + ip.replace(".", "_") + "_discovered";
                    if (!prefs.contains(key)) {
                        String rtsp = "rtsp://admin:123456@" + ip + ":8554/profile0";
                        prefs.edit().putString(key, ip + "|admin|123456|" + rtsp + "|Bardi").apply();
                        addCameraTile(ip, "admin", "123456", rtsp, "Bardi", key);
                    }
                }
            }
        }.execute();
    }

    private void playRtsp(String rtspUrl, String title) {
        try {
            currentRtsp = rtspUrl;
            videoView.setVideoPath(rtspUrl);
            videoView.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                videoView.start();
                statusText.setText("Playing " + title + " - " + rtspUrl);
            });
            videoView.setOnErrorListener((mp, what, extra) -> {
                statusText.setText("VideoView gagal play RTSP (TV tidak support RTSP native): " + rtspUrl + " - Coba VLC atau pakai ExoPlayer versi. Error: " + what + "," + extra);
                // Fallback: try open with external player
                try {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                    intent.setDataAndType(android.net.Uri.parse(rtspUrl), "video/*");
                    startActivity(intent);
                } catch (Exception e) {}
                return true;
            });
            videoView.start();
            statusText.setText("Connecting " + title + "... " + rtspUrl);
        } catch (Exception e) {
            statusText.setText("Error: " + e.getMessage());
        }
    }

    private void stopVideo() {
        try { videoView.stopPlayback(); statusText.setText("Stopped"); } catch (Exception e) {}
    }

    private void enterPip() {
        if (currentRtsp.isEmpty()) {
            Toast.makeText(this, "Play CCTV dulu", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Rational ratio = new Rational(16,9);
            PictureInPictureParams params = new PictureInPictureParams.Builder().setAspectRatio(ratio).build();
            enterPictureInPictureMode(params);
        } catch (Exception e) {
            Toast.makeText(this, "PIP gagal: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (videoView.isPlaying()) { stopVideo(); return true; }
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) { discoverTuyaCameras(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { videoView.stopPlayback(); } catch (Exception e) {}
    }
}
