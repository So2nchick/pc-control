package com.pcremote.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    WebSocketClient ws;
    Handler ui = new Handler(Looper.getMainLooper());
    AtomicInteger msgId = new AtomicInteger(0);
    Vibrator vibrator;
    boolean connected = false;
    boolean scrollMode = false;
    float lastX, lastY;
    long lastMoveTime = 0;

    // Views
    LinearLayout connectBar, mainContent;
    EditText ipInput;
    Button connectBtn;
    TextView statusText;
    View dot;

    // Tabs
    View tabMedia, tabTrackpad, tabFiles, tabTerminal, tabSystem, tabControl;
    View pageMedia, pageTrackpad, pageFiles, pageTerminal, pageSystem, pageControl;

    // Stats
    TextView cpuVal, ramVal, diskVal, netVal;
    ProgressBar cpuBar, ramBar, diskBar, netBar;
    LinearLayout procList;

    // Terminal
    TextView termOutput;
    EditText termInput;

    // Trackpad
    View trackpadArea;
    TextView tpModeLabel;
    Button btnScrollMode;

    // Type text
    EditText typeInput;

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        bindViews();
        setupTabs();
        setupTrackpad();
        setupTerminal();
        setupTypeInput();
        setupMediaButtons();
        setupControlButtons();
        setupKeyboardButtons();

        connectBtn.setOnClickListener(v -> toggleConnect());
    }

    void bindViews() {
        ipInput = findViewById(R.id.ipInput);
        connectBtn = findViewById(R.id.connectBtn);
        statusText = findViewById(R.id.statusText);
        dot = findViewById(R.id.dot);
        mainContent = findViewById(R.id.mainContent);

        tabMedia = findViewById(R.id.tabMedia);
        tabTrackpad = findViewById(R.id.tabTrackpad);
        tabFiles = findViewById(R.id.tabFiles);
        tabTerminal = findViewById(R.id.tabTerminal);
        tabSystem = findViewById(R.id.tabSystem);
        tabControl = findViewById(R.id.tabControl);

        pageMedia = findViewById(R.id.pageMedia);
        pageTrackpad = findViewById(R.id.pageTrackpad);
        pageFiles = findViewById(R.id.pageFiles);
        pageTerminal = findViewById(R.id.pageTerminal);
        pageSystem = findViewById(R.id.pageSystem);
        pageControl = findViewById(R.id.pageControl);

        cpuVal = findViewById(R.id.cpuVal);
        ramVal = findViewById(R.id.ramVal);
        diskVal = findViewById(R.id.diskVal);
        netVal = findViewById(R.id.netVal);
        cpuBar = findViewById(R.id.cpuBar);
        ramBar = findViewById(R.id.ramBar);
        diskBar = findViewById(R.id.diskBar);
        netBar = findViewById(R.id.netBar);
        procList = findViewById(R.id.procList);

        termOutput = findViewById(R.id.termOutput);
        termInput = findViewById(R.id.termInput);

        trackpadArea = findViewById(R.id.trackpadArea);
        tpModeLabel = findViewById(R.id.tpModeLabel);
        btnScrollMode = findViewById(R.id.btnScrollMode);

        typeInput = findViewById(R.id.typeInput);
    }

    void setupTabs() {
        View[] tabs = {tabMedia, tabTrackpad, tabFiles, tabTerminal, tabSystem, tabControl};
        View[] pages = {pageMedia, pageTrackpad, pageFiles, pageTerminal, pageSystem, pageControl};
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            tabs[i].setOnClickListener(v -> {
                for (View p : pages) p.setVisibility(View.GONE);
                for (View t : tabs) t.setSelected(false);
                pages[idx].setVisibility(View.VISIBLE);
                tabs[idx].setSelected(true);
                if (idx == 2 && connected) loadFiles("~");
            });
        }
        pageMedia.setVisibility(View.VISIBLE);
        tabMedia.setSelected(true);
    }

    @SuppressLint("ClickableViewAccessibility")
    void setupTrackpad() {
        btnScrollMode.setOnClickListener(v -> {
            scrollMode = !scrollMode;
            tpModeLabel.setText(scrollMode ? "Режим: Скролл" : "Режим: Курсор");
            btnScrollMode.setText(scrollMode ? "Курсор" : "Скролл");
        });

        trackpadArea.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = e.getX();
                    lastY = e.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    long now = System.currentTimeMillis();
                    if (now - lastMoveTime > 16) {
                        lastMoveTime = now;
                        float dx = e.getX() - lastX;
                        float dy = e.getY() - lastY;
                        lastX = e.getX();
                        lastY = e.getY();
                        if (scrollMode) {
                            send("{\"action\":\"mouse_scroll\",\"dy\":" + (int)(dy/3) + "}");
                        } else {
                            send("{\"action\":\"mouse_move\",\"dx\":" + (int)(dx*2) + ",\"dy\":" + (int)(dy*2) + "}");
                        }
                    }
                    break;
            }
            return true;
        });

        findViewById(R.id.btnLClick).setOnClickListener(v -> {
            vibrate(30);
            send("{\"action\":\"mouse_click\",\"button\":\"left\"}");
        });
        findViewById(R.id.btnRClick).setOnClickListener(v -> {
            vibrate(30);
            send("{\"action\":\"mouse_click\",\"button\":\"right\"}");
        });
        findViewById(R.id.btnDblClick).setOnClickListener(v -> {
            vibrate(50);
            send("{\"action\":\"mouse_dblclick\"}");
        });
    }

    void setupTerminal() {
        termInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { runCmd(); return true; }
            return false;
        });
        findViewById(R.id.btnRunCmd).setOnClickListener(v -> runCmd());
        findViewById(R.id.btnClearTerm).setOnClickListener(v -> termOutput.setText(""));

        int[] quickIds = {R.id.quickDir, R.id.quickIp, R.id.quickTasklist, R.id.quickPing};
        String[] quickCmds = {"dir", "ipconfig", "tasklist /fo table | head -20", "ping -n 4 8.8.8.8"};
        for (int i = 0; i < quickIds.length; i++) {
            final String cmd = quickCmds[i];
            findViewById(quickIds[i]).setOnClickListener(v -> { termInput.setText(cmd); runCmd(); });
        }
    }

    void runCmd() {
        String cmd = termInput.getText().toString().trim();
        if (cmd.isEmpty()) return;
        termOutput.append("> " + cmd + "\n");
        termInput.setText("");
        sendWithCallback("{\"action\":\"terminal\",\"cmd\":\"" + cmd.replace("\"","\\\"") + "\"}", result -> {
            try {
                JSONObject r = new JSONObject(result);
                String out = r.optString("stdout","");
                String err = r.optString("stderr","");
                String error = r.optString("error","");
                StringBuilder sb = new StringBuilder();
                if (!out.isEmpty()) sb.append(out).append("\n");
                if (!err.isEmpty()) sb.append("[ERR] ").append(err).append("\n");
                if (!error.isEmpty()) sb.append("[!] ").append(error).append("\n");
                termOutput.append(sb.toString());
                ScrollView sv = (ScrollView) termOutput.getParent();
                sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
            } catch (Exception e) { termOutput.append("[parse error]\n"); }
        });
    }

    void setupTypeInput() {
        typeInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendTypeText(); return true; }
            return false;
        });
        findViewById(R.id.btnSendText).setOnClickListener(v -> sendTypeText());
    }

    void sendTypeText() {
        String text = typeInput.getText().toString();
        if (text.isEmpty()) return;
        send("{\"action\":\"type\",\"text\":\"" + text.replace("\"","\\\"") + "\"}");
        typeInput.setText("");
        toast("Отправлено");
    }

    void setupMediaButtons() {
        int[] ids = {R.id.btnPrev, R.id.btnPlayPause, R.id.btnNext, R.id.btnVolDown, R.id.btnVolUp, R.id.btnMute};
        String[] cmds = {"prev","play_pause","next","vol_down","vol_up","mute"};
        for (int i = 0; i < ids.length; i++) {
            final String cmd = cmds[i];
            findViewById(ids[i]).setOnClickListener(v -> {
                vibrate(20);
                send("{\"action\":\"media\",\"cmd\":\"" + cmd + "\"}");
            });
        }
        findViewById(R.id.btnScreenshot).setOnClickListener(v -> {
            toast("Делаю скриншот...");
            send("{\"action\":\"screenshot\"}");
        });
    }

    void setupControlButtons() {
        int[] ids = {R.id.btnShutdown, R.id.btnRestart, R.id.btnSleep, R.id.btnLock, R.id.btnCancelShutdown, R.id.btnBrightUp, R.id.btnBrightDown};
        String[] cmds = {"shutdown","restart","sleep","lock","cancel","bright_up","bright_down"};
        String[] toasts = {"Выключение через 10с","Перезагрузка через 10с","Уходим в сон","Блокировка","Отмена выключения","Яркость +","Яркость -"};
        for (int i = 0; i < ids.length; i++) {
            final String cmd = cmds[i];
            final String t = toasts[i];
            final int idx = i;
            if (idx < 5) {
                findViewById(ids[i]).setOnClickListener(v -> { vibrate(40); send("{\"action\":\"power\",\"cmd\":\"" + cmd + "\"}"); toast(t); });
            } else {
                final int bv = idx == 5 ? 100 : 10;
                findViewById(ids[i]).setOnClickListener(v -> { send("{\"action\":\"brightness\",\"value\":" + bv + "}"); toast(t); });
            }
        }
        // WOL
        EditText macInput = findViewById(R.id.macInput);
        findViewById(R.id.btnWol).setOnClickListener(v -> {
            String mac = macInput.getText().toString().trim();
            if (mac.isEmpty()) { toast("Введи MAC адрес"); return; }
            String wolCmd = "python -c \"import socket; mac='" + mac + "'.replace(':','').replace('-',''); s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM); s.setsockopt(socket.SOL_SOCKET,socket.SO_BROADCAST,1); s.sendto(bytes.fromhex('FF'*6+mac*16),('255.255.255.255',9)); print('WOL sent')\"";
            send("{\"action\":\"terminal\",\"cmd\":\"" + wolCmd.replace("\"","\\\"") + "\"}");
            toast("WOL отправлен!");
        });
    }

    void setupKeyboardButtons() {
        Object[][] keys = {
            {R.id.kbCopy, new String[]{"ctrl","c"}},
            {R.id.kbPaste, new String[]{"ctrl","v"}},
            {R.id.kbUndo, new String[]{"ctrl","z"}},
            {R.id.kbRedo, new String[]{"ctrl","y"}},
            {R.id.kbAltTab, new String[]{"alt","tab"}},
            {R.id.kbWinD, new String[]{"win","d"}},
            {R.id.kbEsc, new String[]{"escape"}},
            {R.id.kbWin, new String[]{"win"}},
            {R.id.kbF11, new String[]{"f11"}},
            {R.id.kbWinL, new String[]{"win","l"}},
            {R.id.kbTaskMgr, new String[]{"ctrl","shift","esc"}},
            {R.id.kbCtrlAltDel, new String[]{"ctrl","alt","delete"}},
        };
        for (Object[] k : keys) {
            String[] hotkeys = (String[]) k[1];
            JSONArray arr = new JSONArray();
            for (String h : hotkeys) arr.put(h);
            final String payload = "{\"action\":\"key\",\"keys\":" + arr.toString() + "}";
            final String label = String.join("+", hotkeys).toUpperCase();
            findViewById((int) k[0]).setOnClickListener(v -> {
                vibrate(20);
                send(payload);
                toast(label);
            });
        }
    }

    void loadFiles(String path) {
        String escaped = path.replace("\\","\\\\").replace("\"","\\\"");
        sendWithCallback("{\"action\":\"ls\",\"path\":\"" + escaped + "\"}", result -> {
            try {
                JSONObject r = new JSONObject(result);
                if (r.has("error")) { toast("Ошибка: " + r.getString("error")); return; }
                String currentPath = r.getString("path");
                ((TextView) findViewById(R.id.currentPath)).setText(currentPath);
                JSONArray items = r.getJSONArray("items");
                LinearLayout list = findViewById(R.id.fileList);
                list.removeAllViews();
                // Up button
                if (!currentPath.equals("/") && !currentPath.equals("~")) {
                    TextView up = makeFileRow("..", true, "");
                    String parent = currentPath.contains("/")
                        ? currentPath.substring(0, currentPath.lastIndexOf('/'))
                        : currentPath.contains("\\")
                        ? currentPath.substring(0, currentPath.lastIndexOf('\\'))
                        : "~";
                    if (parent.isEmpty()) parent = "/";
                    final String p = parent;
                    up.setOnClickListener(v -> loadFiles(p));
                    list.addView(up);
                }
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    boolean isDir = item.getBoolean("is_dir");
                    String name = item.getString("name");
                    long size = item.getLong("size");
                    String sizeStr = isDir ? "" : fmtSize(size);
                    TextView row = makeFileRow(name, isDir, sizeStr);
                    if (isDir) {
                        final String fp = item.getString("path");
                        row.setOnClickListener(v -> loadFiles(fp));
                    }
                    list.addView(row);
                }
            } catch (Exception e) { toast("Ошибка разбора"); }
        });
    }

    TextView makeFileRow(String name, boolean isDir, String size) {
        TextView tv = new TextView(this);
        String icon = isDir ? "📁 " : "📄 ";
        tv.setText(icon + name + (size.isEmpty() ? "" : "   " + size));
        tv.setTextSize(14);
        tv.setPadding(32, 24, 32, 24);
        tv.setTextColor(isDir ? 0xFF3498db : 0xFFaaaaaa);
        tv.setBackgroundResource(R.drawable.ripple_bg);
        return tv;
    }

    String fmtSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576) return (b/1024) + " KB";
        if (b < 1073741824) return (b/1048576) + " MB";
        return String.format("%.1f GB", b/1073741824.0);
    }

    void updateStats(JSONObject d) {
        try {
            int cpu = d.getInt("cpu");
            int ram = d.getInt("ram");
            int disk = d.getInt("disk");
            double net = d.getDouble("net_recv");
            cpuVal.setText(cpu + "%");
            ramVal.setText(ram + "% (" + d.getDouble("ram_used") + "/" + d.getDouble("ram_total") + " GB)");
            diskVal.setText(disk + "% (" + d.getDouble("disk_used") + "/" + d.getDouble("disk_total") + " GB)");
            netVal.setText(String.format("%.1f MB", net));
            cpuBar.setProgress(cpu);
            ramBar.setProgress(ram);
            diskBar.setProgress(disk);
            netBar.setProgress((int) Math.min(net * 2, 100));

            JSONArray procs = d.optJSONArray("processes");
            if (procs != null) {
                procList.removeAllViews();
                for (int i = 0; i < procs.length(); i++) {
                    JSONObject p = procs.getJSONObject(i);
                    TextView tv = new TextView(this);
                    tv.setText(String.format("%-20s  CPU: %s%%  RAM: %s MB",
                        p.getString("name"), p.getString("cpu"), p.getString("mem")));
                    tv.setTextSize(12);
                    tv.setTypeface(android.graphics.Typeface.MONOSPACE);
                    tv.setPadding(24, 16, 24, 16);
                    tv.setTextColor(0xFFaaaaaa);
                    procList.addView(tv);
                }
            }
        } catch (Exception ignored) {}
    }

    interface Callback { void onResult(String result); }
    java.util.Map<Integer, Callback> callbacks = new java.util.HashMap<>();

    void send(String json) {
        if (!connected || ws == null) { toast("Не подключён"); return; }
        try {
            JSONObject obj = new JSONObject(json);
            int id = msgId.incrementAndGet();
            obj.put("id", id);
            ws.send(obj.toString());
        } catch (Exception e) { toast("Ошибка отправки"); }
    }

    void sendWithCallback(String json, Callback cb) {
        if (!connected || ws == null) { toast("Не подключён"); return; }
        try {
            JSONObject obj = new JSONObject(json);
            int id = msgId.incrementAndGet();
            obj.put("id", id);
            callbacks.put(id, cb);
            ws.send(obj.toString());
        } catch (Exception e) { toast("Ошибка"); }
    }

    void toggleConnect() {
        if (connected) { if (ws != null) ws.close(); return; }
        String ip = ipInput.getText().toString().trim();
        if (ip.isEmpty()) { toast("Введи IP адрес"); return; }
        String url = ip.startsWith("ws") ? ip : "ws://" + ip + (ip.contains(":") ? "" : ":8765");
        statusText.setText("Подключение...");
        try {
            ws = new WebSocketClient(new URI(url)) {
                @Override public void onOpen(ServerHandshake h) {
                    connected = true;
                    ui.post(() -> {
                        dot.setBackgroundResource(R.drawable.dot_green);
                        connectBtn.setText("Откл.");
                        statusText.setText("Подключено: " + url);
                        toast("Подключено!");
                    });
                }
                @Override public void onMessage(String msg) {
                    ui.post(() -> {
                        try {
                            JSONObject obj = new JSONObject(msg);
                            if ("stats".equals(obj.optString("event"))) {
                                updateStats(obj.getJSONObject("data"));
                                return;
                            }
                            int id = obj.optInt("id", -1);
                            if (id > 0 && callbacks.containsKey(id)) {
                                Callback cb = callbacks.remove(id);
                                if (cb != null) cb.onResult(obj.optJSONObject("result") != null ? obj.getJSONObject("result").toString() : "{}");
                            }
                        } catch (Exception ignored) {}
                    });
                }
                @Override public void onClose(int c, String r, boolean remote) {
                    connected = false;
                    ui.post(() -> {
                        dot.setBackgroundResource(R.drawable.dot_red);
                        connectBtn.setText("Подключить");
                        statusText.setText("Отключено");
                    });
                }
                @Override public void onError(Exception e) {
                    ui.post(() -> toast("Ошибка: " + e.getMessage()));
                }
            };
            ws.connect();
        } catch (Exception e) { toast("Неверный адрес"); }
    }

    void vibrate(int ms) {
        if (vibrator != null) vibrator.vibrate(ms);
    }

    void toast(String msg) {
        ui.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (ws != null) ws.close();
    }
}
