package it.cicciosfood.sunmi;

import android.app.*;
import android.os.*;
import android.content.*;
import android.media.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.sunmi.peripheral.printer.*;

public class MainActivity extends androidx.appcompat.app.AppCompatActivity {
    LinearLayout ordersBox; TextView status; EditText api, token;
    Handler h = new Handler(Looper.getMainLooper());
    SharedPreferences prefs;
    Set<Integer> seen = new HashSet<>();
    MediaPlayer alarm;
    SunmiPrinterService printer;
    InnerPrinterCallback printerCb = new InnerPrinterCallback() {
        @Override protected void onConnected(SunmiPrinterService service) { printer = service; }
        @Override protected void onDisconnected() { printer = null; }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs=getSharedPreferences("cfg",MODE_PRIVATE);
        api=findViewById(R.id.api); token=findViewById(R.id.token);
        status=findViewById(R.id.status); ordersBox=findViewById(R.id.orders);
        api.setText(prefs.getString("api",""));
        token.setText(prefs.getString("token",""));
        findViewById(R.id.save).setOnClickListener(v -> {
            prefs.edit().putString("api",api.getText().toString().replaceAll("/+$",""))
                    .putString("token",token.getText().toString()).apply();
            poll();
        });
        try { InnerPrinterManager.getInstance().bindService(this, printerCb); } catch(Exception e){}
        h.post(poller);
    }

    Runnable poller = new Runnable(){ public void run(){ poll(); h.postDelayed(this,10000); } };

    void poll(){
        String a=prefs.getString("api",""), t=prefs.getString("token","");
        if(a.isEmpty()||t.isEmpty()) return;
        new Thread(() -> {
            try {
                String s=request("GET",a+"/orders",t);
                JSONArray arr=new JSONArray(s);
                runOnUiThread(() -> render(arr));
            } catch(Exception e){ runOnUiThread(() -> status.setText("Connessione: "+e.getMessage())); }
        }).start();
    }

    void render(JSONArray arr){
        status.setText("Connesso • aggiornamento automatico ogni 10 secondi");
        ordersBox.removeAllViews();
        boolean hasWaiting=false;
        for(int i=0;i<arr.length();i++){
            try{
                JSONObject o=arr.getJSONObject(i);
                int id=o.getInt("id"); String st=o.getString("status");
                if(st.equals("pending")||st.equals("on-hold")){
                    hasWaiting=true;
                    seen.add(id);
                }
                addOrder(o);
            }catch(Exception ignored){}
        }
        if(hasWaiting && alarm==null) beep();
        if(!hasWaiting) stopAlarm();
    }

    void addOrder(JSONObject o) throws Exception {
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(12,12,12,20);
        TextView head=new TextView(this); head.setTextSize(22); head.setTypeface(null,Typeface.BOLD);
        String pickup=o.optString("pickup_mode").equals("scheduled") ? "RITIRO "+o.optString("pickup_datetime") : "⚡ QUANTO PRIMA";
        head.setText("#"+o.optString("number")+" • "+pickup); card.addView(head);
        TextView body=new TextView(this); body.setText(buildText(o)); body.setTextSize(17); card.addView(body);
        String st=o.getString("status");
        Button b=new Button(this);
        if(st.equals("processing")) {
            b.setText("CONSEGNATO • COMPLETA");
            b.setOnClickListener(v -> action(o,"complete",false));
        } else {
            b.setText("ACCETTA • STAMPA");
            b.setOnClickListener(v -> action(o,"accept",true));
        }
        card.addView(b);
        Button re=new Button(this); re.setText("RISTAMPA"); re.setOnClickListener(v -> printOrder(o)); card.addView(re);
        ordersBox.addView(card);
    }

    String buildText(JSONObject o) throws Exception {
        StringBuilder s=new StringBuilder();
        s.append(o.optString("customer")).append("\n");
        JSONArray items=o.getJSONArray("items");
        for(int i=0;i<items.length();i++){
            JSONObject it=items.getJSONObject(i);
            s.append(it.getInt("qty")).append(" x ").append(it.getString("name")).append("\n");
            JSONArray m=it.getJSONArray("meta");
            for(int j=0;j<m.length();j++){
                JSONObject x=m.getJSONObject(j);
                s.append("   ").append(x.optString("key")).append(": ").append(x.optString("value")).append("\n");
            }
        }
        if(!o.optString("note").isEmpty()) s.append("NOTE: ").append(o.optString("note")).append("\n");
        s.append("Pagamento: ").append(o.optString("payment_method")).append("\n");
        s.append("Totale: ").append(o.optString("total"));
        return s.toString();
    }

    void action(JSONObject o,String action,boolean print){
        new Thread(() -> {
            try{
                request("POST",prefs.getString("api","")+"/orders/"+o.getInt("id")+"/"+action,prefs.getString("token",""));
                if(print) runOnUiThread(() -> printOrder(o));
                poll();
            }catch(Exception e){runOnUiThread(() -> Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}
        }).start();
    }

    void printOrder(JSONObject o){
        if(printer==null){ Toast.makeText(this,"Stampante SUNMI non connessa",Toast.LENGTH_LONG).show(); return; }
        try{
            printer.printerInit(null);
            printer.setAlignment(1,null);
            printer.setFontSize(34,null);
            printer.printText("CICCIO'S FOOD\n",null);
            printer.setFontSize(30,null);
            printer.printText("ORDINE #"+o.optString("number")+"\n",null);
            printer.setAlignment(0,null);
            printer.setFontSize(25,null);
            String pickup=o.optString("pickup_mode").equals("scheduled") ? "RITIRO: "+o.optString("pickup_datetime") : "RITIRO: QUANTO PRIMA";
            printer.printText(pickup+"\n",null);
            printer.printText(buildText(o)+"\n\n\n",null);
        }catch(Exception e){ Toast.makeText(this,"Errore stampa: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
    }

    void beep(){
        try{
            stopAlarm();
            alarm=MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);
            alarm.setLooping(true); alarm.start();
        }catch(Exception ignored){}
    }
    void stopAlarm(){ if(alarm!=null){ try{alarm.stop(); alarm.release();}catch(Exception ignored){} alarm=null; } }

    String request(String method,String url,String tok) throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(8000); c.setReadTimeout(8000);
        c.setRequestProperty("X-Ciccios-Token",tok); c.setRequestProperty("Accept","application/json");
        if(method.equals("POST")){ c.setDoOutput(true); c.getOutputStream().write(new byte[0]); }
        int code=c.getResponseCode();
        InputStream in=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
        String out=readAll(in);
        if(code<200||code>=300) throw new IOException("HTTP "+code+" "+out);
        return out;
    }
    String readAll(InputStream in)throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] x=new byte[4096]; int n;
        while((n=in.read(x))!=-1)b.write(x,0,n);
        return b.toString("UTF-8");
    }

    @Override protected void onDestroy(){
        super.onDestroy(); h.removeCallbacks(poller); stopAlarm();
        try{ InnerPrinterManager.getInstance().unBindService(this,printerCb); }catch(Exception ignored){}
    }
}