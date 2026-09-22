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
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private ListView list;
    private EditText search;
    private TextView status;
    private ArrayAdapter<String> adapter;
    private final ArrayList<String> rows = new ArrayList<String>();
    private final ArrayList<String> allRows = new ArrayList<String>();
    private final Set<String> phones = new HashSet<String>();

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
                t.setBackgroundColor(Color.rgb(31, 35, 40));
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
                    int i = s.lastIndexOf(" | ");
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
        addNetivotPublicData();
        rows.addAll(allRows);
        adapter.notifyDataSetChanged();
        status.setText("נטענו " + rows.size() + " עסקים ושירותים • לחץ על עסק לחיוג");
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
                    phones.clear();
                    allRows.clear();
                    addNetivotPublicData();
                    if (json != null) {
                        JSONObject root = new JSONObject(json);
                        JSONObject result = root.optJSONObject("result");
                        JSONArray records = result == null ? null : result.optJSONArray("records");
                        if (records != null) {
                            for (int i=0; i<records.length(); i++) {
                                JSONObject o = records.optJSONObject(i);
                                if (o == null) continue;
                                addRow(safe(o.optString("city")), safe(o.optString("category")),
                                    safe(o.optString("name")), safe(o.optString("address")),
                                    safe(o.optString("phone")));
                            }
                        }
                    }
                    rows.clear();
                    rows.addAll(allRows);
                    adapter.notifyDataSetChanged();
                    status.setText("נטענו " + rows.size() + " עסקים ושירותים • לחץ על עסק לחיוג");
                    return;
                    status.setText("שגיאה בטעינת הנתונים");
                }
            }
        }.execute();
    }

    private void addRow(String city, String category, String name, String address, String phone) {
        city = safe(city); category = safe(category); name = safe(name);
        address = safe(address); phone = safe(phone);
        if (name.length() == 0 || phone.length() == 0) return;
        String key = phone.replaceAll("[^0-9+]", "");
        if (key.length() < 6 || phones.contains(key)) return;
        phones.add(key);
        String row = city;
        if (category.length() > 0) row += (row.length() > 0 ? " • " : "") + category;
        row += (row.length() > 0 ? " • " : "") + name;
        if (address.length() > 0) row += " • " + address;
        row += " | " + phone;
        allRows.add(row);
    }

    private void addNetivotPublicData() {
        addRow("נתיבות","מסעדות","ג'ויה סושי בר","יוסף סמלו 78","08-6900982");
        addRow("נתיבות","מסעדות","מפגש הכיכר","שד' ירושלים 33, מרכז מסחרי","08-9932577");
        addRow("נתיבות","מסעדות","טורו TORO","שד' ירושלים 1","08-9944405");
        addRow("נתיבות","מסעדות","פלאפל כיד המלך","הרמב"ם 1, מרכז מסחרי","052-7433485");
        addRow("נתיבות","מסעדות","מסעדת ניסים","","08-9945287");
        addRow("נתיבות","מסעדות","פלאפל זגדון","יוסף סמלו 16","08-9930187");
        addRow("נתיבות","מסעדות","פלאפל בוארון","יוסף סמלו 9","08-6882299");
        addRow("נתיבות","מסעדות","פלאפל סופר","שד' ירושלים 134","08-6440130");
        addRow("נתיבות","מוסכים","אולטרקס","הארזים 74","08-9941777");
        addRow("נתיבות","מוסכים","מוסך עינב","הארזים 46","08-6709400");
        addRow("נתיבות","מוסכים","מוסך אשר","אברהם רוזנמן 671, צים סנטר","08-6375505");
        addRow("נתיבות","מוסכים","א.א. אטיאס","הקציר 669","050-4213207");
        addRow("נתיבות","מוסכים","מוסך צל אלקטרוניקה","רבי עקיבא 38","072-3116714");
        addRow("נתיבות","מוסכים","מוסך דיזל הנגב-ישראל","אזור תעשיה חדש","072-3200245");
        addRow("נתיבות","סופרמרקטים","סופר דמרי","הגפן 3","08-9932387");
        addRow("נתיבות","סופרמרקטים","סופר חיים סמילה ובניו","ורדימון 2","08-9941473");
        addRow("נתיבות","סופרמרקטים","מינימרקט אדרי","שד' ירושלים 1001, מרכז מסחרי","08-9930302");
        addRow("נתיבות","סופרמרקטים","מכולת ביטון דוד","יוסף סמלו 7","08-9944837");
        addRow("נתיבות","סופרמרקטים","היפר כהן - סניף רימון","הרב צבאן","08-9933173");
        addRow("נתיבות","סופרמרקטים","היפר כהן - סניף בעלי המלאכה","בעלי המלאכה 8, צים סנטר","08-9930991");
        addRow("נתיבות","מאפיות","רשת לחם פרנה","בעלי המלאכה 10","054-2527304");
        addRow("נתיבות","מאפיות","שיבולת השרון נתיבות","שד' ירושלים 18","08-6790413");
        addRow("נתיבות","מאפיות","מאפית שיבולי הלקט","ירושלים 170","052-6175293");
        addRow("נתיבות","מאפיות","מאפה נאמן מתחם פריז","בעלי המלאכה 3","08-6161311");
        addRow("נתיבות","מאפיות","Litel Bakery מאפית בוטיק","רפיח ים 24","058-4242204");
        addRow("נתיבות","סלולר ותקשורת","אריאל פון","בעלי המלאכה 5, צים סנטר","052-3777097");
        addRow("נתיבות","סלולר ותקשורת","פרטנר","רבי עקיבא 1","054-5685005");
        addRow("נתיבות","סלולר ותקשורת","איתמר תקשורת","שד' ירושלים 170","050-3566668");
        addRow("נתיבות","סלולר ותקשורת","Dvcom די.וי קום","יוסף סמלו 1","08-9930412");
        addRow("נתיבות","סלולר ותקשורת","תיקוני אייפון","שד' ירושלים 97","050-3456501");
        addRow("נתיבות","סלולר ותקשורת","עוז והדר סלולאר","רבי עקיבא 1","054-2470702");
        addRow("נתיבות","טכנולוגיה","אל קיי טכנולוגיה","בעלי המלאכה 205","055-2617714");
        addRow("נתיבות","עסקים","סמארט סנטר","בעלי המלאכה 5","08-6435761");
        addRow("נתיבות","שירותי עירייה","עיריית נתיבות","כיכר יהדות צרפת 4","08-9938711");
        addRow("נתיבות","ספרייה","הספריה העירונית","שדרות ירושלים 195","077-9109132");
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
