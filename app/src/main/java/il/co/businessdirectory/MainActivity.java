package il.co.businessdirectory;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends Activity {
    private ListView list;
    private EditText search;
    private TextView status;
    private TextView screenTitle;
    private Button cityButton;
    private Button allButton;
    private String selectedCity = "";
    private ArrayAdapter<String> adapter;
    private final ArrayList<String> rows = new ArrayList<String>();
    private final ArrayList<String> allRows = new ArrayList<String>();
    private final Set<String> phones = new HashSet<String>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        list = (ListView)findViewById(R.id.list);
        search = (EditText)findViewById(R.id.search);
        status = (TextView)findViewById(R.id.status);
        screenTitle = (TextView)findViewById(R.id.title);
        cityButton = (Button)findViewById(R.id.cityButton);
        allButton = (Button)findViewById(R.id.allButton);

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows) {
            @Override public View getView(int p, View v, android.view.ViewGroup parent) {
                TextView t = (TextView)super.getView(p, v, parent);
                t.setTextColor(Color.WHITE);
                t.setTextSize(16);
                t.setTypeface(Typeface.create("sans", Typeface.NORMAL));
                t.setBackgroundColor(p % 2 == 0 ? Color.rgb(24,29,35) : Color.rgb(29,34,41));
                t.setPadding(18, 14, 18, 14);
                t.setSingleLine(false);
                t.setGravity(android.view.Gravity.RIGHT | android.view.Gravity.CENTER_VERTICAL);
                return t;
            }
        };
        list.setAdapter(adapter);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int p, long id) {
                dialRow(rows.get(p));
            }
        });

        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> a, View v, int p, long id) {
                dialRow(rows.get(p));
                return true;
            }
        });

        cityButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showCityPicker(); }
        });
        allButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { selectAll(); }
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
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { filter(s.toString()); }
            public void afterTextChanged(android.text.Editable e) {}
        });

        boolean loaded = loadBundledData();
        if (!loaded) addNetivotPublicData();
        updateCityButton();
        showCity("");
    }

    private void dialRow(String s) {
        try {
            int i = s.lastIndexOf(" | ");
            String phone = i >= 0 ? s.substring(i + 3).trim() : s;
            android.content.Intent in = new android.content.Intent(android.content.Intent.ACTION_DIAL);
            in.setData(android.net.Uri.parse("tel:" + phone.replaceAll("[^0-9+]", "")));
            startActivity(in);
        } catch (Exception ignored) {}
    }

    private void updateCityButton() {
        cityButton.setText(selectedCity.length() == 0 ? "בחירת עיר" : selectedCity);
    }

    private void showCityPicker() {
        final ArrayList<String> cities = new ArrayList<String>();
        cities.add("כל הארץ");
        Map<String,Integer> counts = new TreeMap<String,Integer>();
        for (String s : allRows) {
            String c = cityOf(s);
            if (c.length() == 0) continue;
            Integer n = counts.get(c);
            counts.put(c, n == null ? 1 : n + 1);
        }
        for (Map.Entry<String,Integer> e : counts.entrySet()) cities.add(e.getKey() + "  (" + e.getValue() + ")");
        AlertDialog d = new AlertDialog.Builder(this)
            .setTitle("בחירת עיר")
            .setSingleChoiceItems(cities.toArray(new String[cities.size()]),
                selectedCity.length() == 0 ? 0 : cityIndex(cities, selectedCity),
                null)
            .setNegativeButton("ביטול", null)
            .create();
        d.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            public void onShow(android.content.DialogInterface x) {
                ListView lv = d.getListView();
                lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> a, View v, int p, long id) {
                        String value = cities.get(p);
                        if (p == 0) showCity("");
                        else {
                            int n = value.lastIndexOf("  (");
                            showCity(n > 0 ? value.substring(0, n) : value);
                        }
                        d.dismiss();
                    }
                });
            }
        });
        d.show();
    }

    private int cityIndex(ArrayList<String> cities, String city) {
        for (int i=1;i<cities.size();i++) {
            if (cities.get(i).startsWith(city + "  (")) return i;
        }
        return 0;
    }

    private String cityOf(String s) {
        int i = s.indexOf(" • ");
        return i > 0 ? s.substring(0, i).trim() : "";
    }

    private void showCity(String city) {
        selectedCity = city == null ? "" : city;
        updateCityButton();
        rows.clear();
        search.setText("");
        for (String s : allRows) {
            if (selectedCity.length() == 0 || cityOf(s).equals(selectedCity)) rows.add(s);
        }
        screenTitle.setText(selectedCity.length() == 0 ? "עסקים וטלפונים בישראל" : "עסקים ב" + selectedCity);
        adapter.notifyDataSetChanged();
        status.setText((selectedCity.length() == 0 ? "כל הארץ" : selectedCity) + "  •  " + rows.size() + " עסקים עם מספר ציבורי");
        list.requestFocus();
    }

    private void selectAll() { showCity(""); }

    private boolean loadBundledData() {
        boolean any = false;
        try {
            InputStream in = getAssets().open("businesses.csv");
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; }
                String[] p = csvSplit(line);
                if (p.length >= 5) {
                    int before = allRows.size();
                    addRow(p[0], p[1], p[2], p[3], p[4]);
                    if (allRows.size() > before) any = true;
                }
            }
            br.close();
        } catch (Exception ignored) {}
        return any;
    }

    private String[] csvSplit(String line) {
        ArrayList<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i=0;i<line.length();i++) {
            char c=line.charAt(i);
            if(c=='"'){ if(q && i+1<line.length() && line.charAt(i+1)=='"'){cur.append('"');i++;} else q=!q; }
            else if(c==',' && !q){out.add(cur.toString());cur.setLength(0);}
            else cur.append(c);
        }
        out.add(cur.toString());
        return out.toArray(new String[out.size()]);
    }

    private void addRow(String city,String category,String name,String address,String phone) {
        city=safe(city); category=safe(category); name=safe(name); address=safe(address); phone=safe(phone);
        if(name.length()==0 || phone.length()==0) return;
        String key=phone.replaceAll("[^0-9+]","");
        if(key.length()<6 || phones.contains(key)) return;
        phones.add(key);
        String row=city;
        if(category.length()>0) row += (row.length()>0 ? " • " : "") + category;
        row += (row.length()>0 ? " • " : "") + name;
        if(address.length()>0) row += " • " + address;
        row += " | " + phone;
        allRows.add(row);
    }

    private String safe(String s){return s==null ? "" : s.trim();}

    private void filter(String q) {
        q=q==null ? "" : q.trim().toLowerCase();
        rows.clear();
        for(String s:allRows){
            if(selectedCity.length()>0 && !cityOf(s).equals(selectedCity)) continue;
            if(q.length()==0 || s.toLowerCase().contains(q)) rows.add(s);
        }
        adapter.notifyDataSetChanged();
        status.setText((selectedCity.length()==0 ? "כל הארץ" : selectedCity) + "  •  נמצאו " + rows.size() + " תוצאות");
    }

    @Override public void onBackPressed() {
        if(search != null && search.getText().length()>0){search.setText("");return;}
        if(selectedCity.length()>0){showCity("");return;}
        super.onBackPressed();
    }
}
