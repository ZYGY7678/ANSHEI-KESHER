package il.co.businessdirectory;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

public class MainActivity extends Activity {
    private static final String API = "https://data.gov.il/api/3/action/datastore_search?resource_id=5555edc5-532d-46b5-8415-01a54d5a5b73&limit=5000";
    private final ArrayList<Business> all = new ArrayList<Business>();
    private final ArrayList<Business> shown = new ArrayList<Business>();
    private BusinessAdapter adapter;
    private TextView status;
    private EditText search;
    private String selectedCategory = "הכול";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = (TextView)findViewById(R.id.status);
        search = (EditText)findViewById(R.id.search);
        ListView list = (ListView)findViewById(R.id.list);
        adapter = new BusinessAdapter();
        list.setAdapter(adapter);
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int b,int c) { filter(); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        new LoadTask().execute();
    }

    private void filter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.getDefault());
        shown.clear();
        for (Business x: all) {
            boolean cat = selectedCategory.equals("הכול") || selectedCategory.equals(x.category);
            String hay = (x.name+" "+x.city+" "+x.address+" "+x.phone+" "+x.category).toLowerCase(Locale.getDefault());
            if (cat && (q.length()==0 || hay.contains(q))) shown.add(x);
        }
        adapter.notifyDataSetChanged();
        status.setText("מציג " + shown.size() + " עסקים");
    }

    private void buildCategories() {
        LinearLayout box = (LinearLayout)findViewById(R.id.categories);
        box.removeAllViews();
        LinkedHashSet<String> cats = new LinkedHashSet<String>();
        cats.add("הכול");
        for (Business b: all) if (b.category.length()>0) cats.add(b.category);
        for (final String c: cats) {
            Button v = new Button(this);
            v.setText(c);
            v.setTextSize(13);
            v.setTextColor(Color.WHITE);
            v.setAllCaps(false);
            v.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ selectedCategory=c; filter(); }});
            box.addView(v,new LinearLayout.LayoutParams(-2,-1));
        }
    }

    private class LoadTask extends AsyncTask<Void,Void,String> {
        protected void onPreExecute(){ status.setText("מוריד מאגר עסקים ציבורי..."); }
        protected String doInBackground(Void... v) {
            try {
                URL u=new URL(API);
                HttpURLConnection c=(HttpURLConnection)u.openConnection();
                c.setConnectTimeout(20000); c.setReadTimeout(30000); c.setRequestMethod("GET");
                InputStream in=c.getInputStream();
                BufferedReader r=new BufferedReader(new InputStreamReader(in,"UTF-8"));
                StringBuilder s=new StringBuilder(); String line;
                while((line=r.readLine())!=null)s.append(line);
                r.close(); c.disconnect(); return s.toString();
            } catch(Exception e){ return "ERROR:"+e.toString(); }
        }
        protected void onPostExecute(String json) {
            if(json.startsWith("ERROR:")) { status.setText("לא ניתן לטעון כרגע. בדוק חיבור לאינטרנט"); return; }
            try {
                JSONObject root=new JSONObject(json);
                JSONArray rows=root.getJSONObject("result").getJSONArray("records");
                all.clear();
                for(int i=0;i<rows.length();i++){
                    JSONObject o=rows.getJSONObject(i);
                    String name=o.optString("name","");
                    String phone=o.optString("phone","");
                    String city=o.optString("city","");
                    String address=o.optString("address","");
                    String cat=o.optString("category","");
                    if(name.length()>0 && phone.length()>0) all.add(new Business(name,phone,city,address,cat));
                }
                Collections.sort(all,new Comparator<Business>(){public int compare(Business a,Business b){return a.name.compareToIgnoreCase(b.name);}});
                buildCategories(); filter();
            } catch(Exception e){ status.setText("שגיאה בפענוח המאגר: "+e.getMessage()); }
        }
    }

    private class Business {
        String name,phone,city,address,category;
        Business(String n,String p,String c,String a,String k){name=n;phone=p;city=c;address=a;category=k;}
    }

    private class BusinessAdapter extends BaseAdapter {
        public int getCount(){return shown.size();}
        public Object getItem(int p){return shown.get(p);}
        public long getItemId(int p){return p;}
        public View getView(final int p, View convert, ViewGroup parent) {
            View v=convert;
            if(v==null) v=LayoutInflater.from(MainActivity.this).inflate(R.layout.row_business,parent,false);
            Business b=shown.get(p);
            ((TextView)v.findViewById(R.id.name)).setText(b.name);
            ((TextView)v.findViewById(R.id.meta)).setText(b.category+"  •  "+b.city+"  •  "+b.address);
            TextView phone=(TextView)v.findViewById(R.id.phone);
            phone.setText("☎ "+b.phone);
            phone.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                Business x=shown.get(p);
                try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"+x.phone.replaceAll("[^0-9+]","")))); } catch(Exception ignored) {}
            }});
            v.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                Business x=shown.get(p);
                try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"+x.phone.replaceAll("[^0-9+]","")))); } catch(Exception ignored) {}
            }});
            return v;
        }
    }
}
