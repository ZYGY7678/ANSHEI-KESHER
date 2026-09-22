package il.co.businessdirectory;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private ListView list;
    private EditText search;
    private TextView status;
    private ArrayAdapter<String> adapter;
    private final ArrayList<String> rows = new ArrayList<String>();
    private final ArrayList<String> allRows = new ArrayList<String>();

    private static final String DATA_URL =
        "https://data.gov.il/api/3/action/datastore_search?resource_id=5555edc5-532d-46b5-8415-01a54d5a5b73&limit=5000";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        list = (ListView)findViewById(R.id.list);
        search = (EditText)findViewById(R.id.search);
        status = (TextView)findViewById(R.id.status);

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows) {
            @Override public View getView(int p, View v, android.view.ViewGroup parent) {
                TextView t = (TextView)super.getView(p, v, parent);
                t.setTextColor(Color.WHITE);
                t.setTextSize(17);
                t.setPadding(16, 14, 16, 14);
                t.setSingleLine(false);
                return t;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int p, long id) {
                try {
                    String s = rows.get(p);
                    int i = s.indexOf(" | ");
                    String phone = i >= 0 ? s.substring(i + 3).trim() : s;
                    android.content.Intent in = new android.content.Intent(android.content.Intent.ACTION_DIAL);
                    in.setData(android.net.Uri.parse("tel:" + phone.replaceAll("[^0-9+]", "")));
                    startActivity(in);
                } catch (Exception ignored) {}
            }
        });
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent e) {
                filter(v.getText().toString());
                return false;
            }
        });
        search.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent e) {
                if (e.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_ENTER) {
                    filter(search.getText().toString());
                    return true;
                }
                return false;
            }
        });
        loadData();
    }

    private void loadData() {
        status.setText("טוען מאגר...");
        new AsyncTask<Void,Void,String>() {
            protected String doInBackground(Void... x) {
                HttpURLConnection c = null;
                try {
                    URL u = new URL(DATA_URL);
                    c = (HttpURLConnection)u.openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(10000);
                    c.setUseCaches(false);
                    c.setRequestMethod("GET");
                    int code = c.getResponseCode();
                    if (code < 200 || code >= 300) return null;
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder b = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) b.append(line);
                    r.close();
                    return b.toString();
                } catch (Exception ex) {
                    return null;
                } finally {
                    if (c != null) c.disconnect();
                }
            }
            protected void onPostExecute(String json) {
                try {
                    if (json == null) {
                        status.setText("לא ניתן לטעון את המאגר כרגע");
                        return;
                    }
                    JSONObject root = new JSONObject(json);
                    JSONObject result = root.optJSONObject("result");
                    JSONArray records = result == null ? null : result.optJSONArray("records");
                    if (records == null) {
                        status.setText("המאגר לא זמין כרגע");
                        return;
                    }
                    allRows.clear();
                    for (int i=0; i<records.length(); i++) {
                        JSONObject o = records.optJSONObject(i);
                        if (o == null) continue;
                        String name = safe(o.optString("name"));
                        String phone = safe(o.optString("phone"));
                        String city = safe(o.optString("city"));
                        String address = safe(o.optString("address"));
                        if (name.length() == 0 || phone.length() == 0) continue;
                        String meta = city;
                        if (address.length() > 0) meta = meta.length() == 0 ? address : meta + " • " + address;
                        String row = name;
                        if (meta.length() > 0) row += " • " + meta;
                        row += " | " + phone;
                        allRows.add(row);
                    }
                    rows.clear();
                    rows.addAll(allRows);
                    adapter.notifyDataSetChanged();
                    status.setText("נטענו " + rows.size() + " עסקים • לחץ על עסק לחיוג");
                } catch (Exception ex) {
                    status.setText("שגיאה בטעינת הנתונים");
                }
            }
        }.execute();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private void filter(String q) {
        q = q == null ? "" : q.trim().toLowerCase();
        rows.clear();
        if (q.length() == 0) rows.addAll(allRows);
        else for (String s : allRows) if (s.toLowerCase().contains(q)) rows.add(s);
        adapter.notifyDataSetChanged();
        status.setText("נמצאו " + rows.size() + " תוצאות");
    }

    @Override public void onBackPressed() {
        if (search != null && search.getText().length() > 0) {
            search.setText("");
            filter("");
            return;
        }
        super.onBackPressed();
    }
}
