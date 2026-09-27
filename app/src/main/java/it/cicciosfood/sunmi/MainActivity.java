package it.cicciosfood.sunmi;

import android.app.*; import android.os.*; import android.content.*; import android.media.*; import android.graphics.*; import android.graphics.drawable.*; import android.view.*; import android.widget.*;
import org.json.*; import java.io.*; import java.net.*; import java.text.*; import java.util.*; import com.sunmi.peripheral.printer.*;

public class MainActivity extends androidx.appcompat.app.AppCompatActivity {
 LinearLayout ordersBox,setupBox,historyFilter; TextView status,title; EditText api,token,historyDate; Handler h=new Handler(Looper.getMainLooper());
 SharedPreferences prefs; MediaPlayer alarm; SunmiPrinterService printer; boolean history=false;
 InnerPrinterCallback printerCb=new InnerPrinterCallback(){@Override protected void onConnected(SunmiPrinterService s){printer=s;} @Override protected void onDisconnected(){printer=null;}};

 @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_main);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  prefs=getSharedPreferences("cfg",MODE_PRIVATE); api=findViewById(R.id.api);token=findViewById(R.id.token);status=findViewById(R.id.status);title=findViewById(R.id.title);
  ordersBox=findViewById(R.id.orders);setupBox=findViewById(R.id.setupBox);historyFilter=findViewById(R.id.historyFilter);historyDate=findViewById(R.id.historyDate);
  api.setText(prefs.getString("api",""));token.setText(prefs.getString("token","")); historyDate.setText(new SimpleDateFormat("yyyy-MM-dd",Locale.ITALY).format(new Date()));
  findViewById(R.id.save).setOnClickListener(v->{String a=api.getText().toString().trim().replaceAll("/+$",""),t=token.getText().toString().trim(); if(a.isEmpty()||t.isEmpty()){status.setText("Inserisci URL API e Token");return;} prefs.edit().putString("api",a).putString("token",t).commit();status.setText("Connessione in corso...");poll();});
  findViewById(R.id.tabOrders).setOnClickListener(v->{history=false;historyFilter.setVisibility(View.GONE);title.setText("CICCIO'S • ORDINI");poll();});
  findViewById(R.id.tabHistory).setOnClickListener(v->{history=true;stopAlarm();historyFilter.setVisibility(View.VISIBLE);title.setText("CICCIO'S • STORICO");loadHistory();});
  findViewById(R.id.loadHistory).setOnClickListener(v->loadHistory());
  try{InnerPrinterManager.getInstance().bindService(this,printerCb);}catch(Exception ignored){} h.post(poller);
 }
 Runnable poller=new Runnable(){public void run(){if(!history)poll();h.postDelayed(this,10000);}};

 void poll(){String a=prefs.getString("api",""),t=prefs.getString("token","");if(a.isEmpty()||t.isEmpty())return;getArray(a+"/orders",arr->render(arr,false));}
 void loadHistory(){String a=prefs.getString("api",""),t=prefs.getString("token","");if(a.isEmpty()||t.isEmpty())return;getArray(a+"/history?date="+historyDate.getText().toString().trim(),arr->render(arr,true));}
 interface ArrCb{void go(JSONArray a);}
 void getArray(String url,ArrCb cb){new Thread(()->{try{JSONArray a=new JSONArray(request("GET",url,prefs.getString("token",""),null));runOnUiThread(()->cb.go(a));}catch(Exception e){runOnUiThread(()->status.setText("Connessione: "+e.getMessage()));}}).start();}

 void render(JSONArray arr,boolean hist){status.setText("CONNESSO • aggiornamento ogni 10 sec");setupBox.setVisibility(View.GONE);ordersBox.removeAllViews();boolean waiting=false;
  for(int i=0;i<arr.length();i++)try{JSONObject o=arr.getJSONObject(i);if(!hist&&!o.optBoolean("accepted",false))waiting=true;addOrder(o,hist);}catch(Exception ignored){}
  if(!hist&&waiting&&alarm==null)beep(); if(hist||!waiting)stopAlarm();
 }
 TextView tv(String s,int size,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.BLACK);v.setPadding(8,5,8,5);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
 void addOrder(JSONObject o,boolean hist)throws Exception{
  LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(12,12,12,16);GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(14);bg.setStroke(2,Color.LTGRAY);c.setBackground(bg);
  LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,12);c.setLayoutParams(cp);
  String pickup=o.optString("pickup_mode").equals("scheduled")?"RITIRO "+o.optString("pickup_datetime"):"⚡ QUANTO PRIMA";
  c.addView(tv("#"+o.optString("number")+" • "+pickup,21,true));
  String state=o.optString("status"); String label=o.optBoolean("accepted",false)?"IN PREPARAZIONE":"IN ATTESA"; if(state.equals("completed"))label="CONSEGNATO";if(state.equals("refunded"))label="RIMBORSATO";
  c.addView(tv(label,16,true));c.addView(tv(buildText(o),16,false));
  if(!hist){
   Button main=new Button(this);
   if(!o.optBoolean("accepted",false)){main.setText("ACCETTA • STAMPA");main.setOnClickListener(v->action(o,"accept",true));}
   else {main.setText("CONSEGNATO");main.setOnClickListener(v->action(o,"complete",false));}
   c.addView(main);
  }
  Button re=new Button(this);re.setText("RISTAMPA");re.setOnClickListener(v->printOrder(o));c.addView(re);
  if(!state.equals("refunded")&&!state.equals("cancelled")){Button cancel=new Button(this);cancel.setText("ANNULLA E RIMBORSA");cancel.setOnClickListener(v->confirmRefund(o));c.addView(cancel);}
  ordersBox.addView(c);
 }
 String buildText(JSONObject o)throws Exception{StringBuilder s=new StringBuilder();s.append(o.optString("customer")).append("\n");if(!o.optString("phone").isEmpty())s.append("Tel: ").append(o.optString("phone")).append("\n");
  JSONArray items=o.getJSONArray("items");for(int i=0;i<items.length();i++){JSONObject it=items.getJSONObject(i);s.append("\n").append(it.getInt("qty")).append(" x ").append(it.getString("name")).append("\n");JSONArray m=it.getJSONArray("meta");for(int j=0;j<m.length();j++){JSONObject x=m.getJSONObject(j);s.append("   ").append(x.optString("key")).append(": ").append(x.optString("value")).append("\n");}}
  if(!o.optString("note").isEmpty())s.append("\nNOTE: ").append(o.optString("note")).append("\n");s.append("\nPagamento: ").append(o.optString("payment_method")).append("\nTotale: ").append(o.optString("total")).append(" ").append(o.optString("currency"));return s.toString();}
 void action(JSONObject o,String act,boolean print){new Thread(()->{try{request("POST",prefs.getString("api","")+"/orders/"+o.optInt("id")+"/"+act,prefs.getString("token",""),null);if(print)runOnUiThread(()->printOrder(o));poll();}catch(Exception e){toast(e.getMessage());}}).start();}
 void confirmRefund(JSONObject o){new AlertDialog.Builder(this).setTitle("Annulla e rimborsa").setMessage("Rimborsare l'ordine #"+o.optString("number")+" e ripristinare le scorte?").setNegativeButton("NO",null).setPositiveButton("SÌ, RIMBORSA",(d,w)->refund(o)).show();}
 void refund(JSONObject o){new Thread(()->{try{request("POST",prefs.getString("api","")+"/orders/"+o.optInt("id")+"/cancel-refund",prefs.getString("token",""),"reason=Annullato%20dal%20SUNMI");toast("Rimborso eseguito");if(history)loadHistory();else poll();}catch(Exception e){toast("Rimborso NON eseguito: "+e.getMessage());}}).start();}
 void printOrder(JSONObject o){if(printer==null){toast("Stampante SUNMI non connessa");return;}try{printer.printerInit(null);printer.setAlignment(1,null);printer.setFontSize(34,null);printer.printText("CICCIO'S FOOD\n",null);printer.setFontSize(29,null);printer.printText("ORDINE #"+o.optString("number")+"\n\n",null);printer.setAlignment(0,null);printer.setFontSize(24,null);
   printer.printText("CLIENTE\n"+o.optString("customer")+"\n",null);if(!o.optString("phone").isEmpty())printer.printText("Tel: "+o.optString("phone")+"\n",null);if(!o.optString("email").isEmpty())printer.printText("Email: "+o.optString("email")+"\n",null);if(!o.optString("address").trim().isEmpty())printer.printText("Indirizzo: "+o.optString("address")+"\n",null);
   String pickup=o.optString("pickup_mode").equals("scheduled")?"RITIRO: "+o.optString("pickup_datetime"):"RITIRO: QUANTO PRIMA";printer.printText("\n------------------------\n"+pickup+"\n------------------------\n\n",null);
   JSONArray items=o.getJSONArray("items");for(int i=0;i<items.length();i++){JSONObject it=items.getJSONObject(i);printer.printText(it.getInt("qty")+" x "+it.getString("name")+"\n",null);JSONArray m=it.getJSONArray("meta");for(int j=0;j<m.length();j++){JSONObject x=m.getJSONObject(j);printer.printText("  "+x.optString("key")+": "+x.optString("value")+"\n",null);}printer.printText("\n",null);}
   if(!o.optString("note").isEmpty())printer.printText("NOTE: "+o.optString("note")+"\n\n",null);printer.printText("Pagamento: "+o.optString("payment_method")+"\nTotale: "+o.optString("total")+" "+o.optString("currency")+"\n\n\n\n\n\n",null);
  }catch(Exception e){toast("Errore stampa: "+e.getMessage());}}
 void beep(){try{stopAlarm();alarm=MediaPlayer.create(this,android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);alarm.setLooping(true);alarm.start();}catch(Exception ignored){}}
 void stopAlarm(){if(alarm!=null){try{alarm.stop();alarm.release();}catch(Exception ignored){}alarm=null;}}
 void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
 String request(String method,String url,String tok,String body)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod(method);c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setRequestProperty("X-Ciccios-Token",tok);c.setRequestProperty("Accept","application/json");if(method.equals("POST")){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");if(body!=null)c.getOutputStream().write(body.getBytes("UTF-8"));else c.getOutputStream().write(new byte[0]);}int code=c.getResponseCode();InputStream in=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();String out=readAll(in);if(code<200||code>=300)throw new IOException("HTTP "+code+" "+out);return out;}
 String readAll(InputStream in)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[4096];int n;while((n=in.read(x))!=-1)b.write(x,0,n);return b.toString("UTF-8");}
 @Override protected void onDestroy(){super.onDestroy();h.removeCallbacks(poller);stopAlarm();try{InnerPrinterManager.getInstance().unBindService(this,printerCb);}catch(Exception ignored){}}
}