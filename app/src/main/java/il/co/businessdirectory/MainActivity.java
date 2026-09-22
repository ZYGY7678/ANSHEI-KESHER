package il.co.businessdirectory;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
 private ListView list; private EditText search; private TextView status,title; private Button cityButton,allButton;
 private String selectedCity=""; private ArrayAdapter<String> adapter;
 private final ArrayList<String> allRows=new ArrayList<String>(); private final ArrayList<String> rows=new ArrayList<String>();
 private final Set<String> phones=new HashSet<String>();

 public void onCreate(Bundle b){
  super.onCreate(b); setContentView(R.layout.activity_main);
  list=(ListView)findViewById(R.id.list); search=(EditText)findViewById(R.id.search); status=(TextView)findViewById(R.id.status);
  title=(TextView)findViewById(R.id.title); cityButton=(Button)findViewById(R.id.cityButton); allButton=(Button)findViewById(R.id.allButton);
  adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,rows){
   public View getView(int p,View v,android.view.ViewGroup parent){TextView t=(TextView)super.getView(p,v,parent);t.setTextColor(Color.WHITE);t.setTextSize(16);t.setPadding(18,14,18,14);t.setGravity(android.view.Gravity.RIGHT|android.view.Gravity.CENTER_VERTICAL);t.setSingleLine(false);t.setBackgroundColor(p%2==0?Color.rgb(24,29,35):Color.rgb(29,34,41));return t;}
  };
  list.setAdapter(adapter);
  list.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> a,View v,int p,long id){dial(rows.get(p));}});
  cityButton.setOnClickListener(new View.OnClickListener(){public void onClick(View v){pickCity();}});
  allButton.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showCity("");}});
  search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){} public void onTextChanged(CharSequence s,int a,int b,int c){filter(s.toString());} public void afterTextChanged(android.text.Editable e){}});
  if(!loadData()) status.setText("לא נמצא מאגר נתונים"); showCity("");
 }
 private void dial(String s){try{int i=s.lastIndexOf(" | ");String p=i>=0?s.substring(i+3).trim():s;android.content.Intent x=new android.content.Intent(android.content.Intent.ACTION_DIAL);x.setData(android.net.Uri.parse("tel:"+p.replaceAll("[^0-9+]","")));startActivity(x);}catch(Exception e){}}
 private boolean loadData(){boolean ok=false;try{BufferedReader r=new BufferedReader(new InputStreamReader(getAssets().open("businesses.csv"),"UTF-8"));String l;boolean first=true;while((l=r.readLine())!=null){if(first){first=false;continue;}String[] p=csv(l);if(p.length>=5){int n=allRows.size();add(p[0],p[1],p[2],p[3],p[4]);if(n<allRows.size())ok=true;}}r.close();}catch(Exception e){}return ok;}
 private String[] csv(String s){ArrayList<String> o=new ArrayList<String>();StringBuilder b=new StringBuilder();boolean q=false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='"'){if(q&&i+1<s.length()&&s.charAt(i+1)=='"'){b.append('"');i++;}else q=!q;}else if(c==','&&!q){o.add(b.toString());b.setLength(0);}else b.append(c);}o.add(b.toString());return o.toArray(new String[o.size()]);}
 private void add(String c,String cat,String n,String a,String p){n=s(n);p=s(p);if(n.length()==0||p.length()==0)return;String k=p.replaceAll("[^0-9]","");if(k.length()<6||phones.contains(k))return;phones.add(k);c=s(c);cat=s(cat);a=s(a);String x=c;if(cat.length()>0)x+=(x.length()>0?" • ":"")+cat;x+=(x.length()>0?" • ":"")+n;if(a.length()>0)x+=" • "+a;x+=" | "+p;allRows.add(x);}
 private String s(String x){return x==null?"":x.trim();}
 private String city(String x){int i=x.indexOf(" • ");return i>0?x.substring(0,i).trim():"";}
 private void showCity(String c){selectedCity=c==null?"":c;cityButton.setText(selectedCity.length()==0?"בחירת עיר":selectedCity);rows.clear();search.setText("");for(String x:allRows)if(selectedCity.length()==0||city(x).equals(selectedCity))rows.add(x);title.setText(selectedCity.length()==0?"עסקים וטלפונים בישראל":"עסקים ב"+selectedCity);adapter.notifyDataSetChanged();status.setText((selectedCity.length()==0?"כל הארץ":selectedCity)+" • "+rows.size()+" עסקים");list.requestFocus();}
 private void filter(String q){q=s(q).toLowerCase();rows.clear();for(String x:allRows)if((selectedCity.length()==0||city(x).equals(selectedCity))&&(q.length()==0||x.toLowerCase().contains(q)))rows.add(x);adapter.notifyDataSetChanged();status.setText((selectedCity.length()==0?"כל הארץ":selectedCity)+" • "+rows.size()+" תוצאות");}
 private void pickCity(){final ArrayList<String> c=new ArrayList<String>();c.add("כל הארץ");TreeMap<String,Integer> m=new TreeMap<String,Integer>();for(String x:allRows){String z=city(x);if(z.length()>0)m.put(z,m.containsKey(z)?m.get(z)+1:1);}for(Map.Entry<String,Integer> e:m.entrySet())c.add(e.getKey()+" ("+e.getValue()+")");final AlertDialog d=new AlertDialog.Builder(this).setTitle("בחירת עיר").setSingleChoiceItems(c.toArray(new String[c.size()]),0,null).setNegativeButton("ביטול",null).create();d.setOnShowListener(new android.content.DialogInterface.OnShowListener(){public void onShow(android.content.DialogInterface x){d.getListView().setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> a,View v,int p,long id){String z=c.get(p);if(p==0)showCity("");else showCity(z.substring(0,z.lastIndexOf(" (")));d.dismiss();}});}});d.show();}
 @Override public void onBackPressed(){if(search.getText().length()>0){search.setText("");return;}if(selectedCity.length()>0){showCity("");return;}super.onBackPressed();}
}