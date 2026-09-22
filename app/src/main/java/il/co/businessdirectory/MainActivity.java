package il.co.businessdirectory;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;\nimport java.io.BufferedReader;\nimport java.io.InputStream;\nimport java.io.InputStreamReader;

public class MainActivity extends Activity {
    private ListView list;
    private EditText search;
    private TextView status;
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

        allButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { selectAll(); } });\n\n        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
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

        // כל המידע נטען מתוך האפליקציה עצמה — אין צורך באינטרנט.
        addNetivotPublicData();
        rows.addAll(allRows);
        adapter.notifyDataSetChanged();
        status.setText("מצב אופליין • " + rows.size() + " עסקים ושירותים • לחץ על עסק לחיוג");
    }

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
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (q && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"'); i++;
                } else q = !q;
            } else if (c == ',' && !q) {
                out.add(cur.toString()); cur.setLength(0);
            } else cur.append(c);
        }
        out.add(cur.toString());
        return out.toArray(new String[out.size()]);
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
        // הרחבה: מספרי עסקים ציבוריים שאותרו במקורות עסקיים ציבוריים בספטמבר 2026.
        addRow("נתיבות","מסעדות","ארומה אספרסו בר","בעלי המלאכה 2","08-9162522");
        addRow("נתיבות","בתי קפה","אביאל ואליה קפה קפה","בעלי המלאכה 203","050-2007220");
        addRow("נתיבות","בתי קפה","ביגה נתיבות","בעלי המלאכה 3","08-6767683");
        addRow("נתיבות","מסעדות","המקום של דודו","דוקטור יוסף סמלו 42","08-9931139");
        addRow("נתיבות","מסעדות","מיטליס","גני טל 25","053-9425594");
        addRow("נתיבות","מסעדות","ויוינו VIVINO","בעלי המלאכה 200","08-6272121");
        addRow("נתיבות","בתי קפה","קפה גרג","בעלי המלאכה 5","08-9933287");
        addRow("נתיבות","מסעדות","לנדוור נתיבות","בעלי המלאכה 200","08-9933287");
        addRow("נתיבות","מסעדות","טעם העמק","בעלי המלאכה 5","08-6765532");
        addRow("נתיבות","מסעדות","לה בוהם","האומן 56","08-9162208");
        addRow("נתיבות","פיצריות","סופ-סופ פיצה","ירושלים 2","08-9930145");
        addRow("נתיבות","פיצריות","פיצה האט נתיבות","בעלי המלאכה 5, מתחם צים סנטר","1-700-50-60-70");
        addRow("נתיבות","מסעדות","שיפודי הפינה","הירדן 41","08-9945466");
        addRow("נתיבות","מסעדות","שלי'ס קפה ופיצה","יוסף סמלו 8","08-9941234");
        addRow("נתיבות","מסעדות","שמונה","יוסף סמלו 10","08-9942222");
        addRow("נתיבות","מסעדות","פיצה כמעט חינם","יוסף סמלו 2","08-9943212");
        addRow("נתיבות","מסעדות","הפינה של אילן","יוסף סמלו 201","053-7737401");
        addRow("נתיבות","מסעדות","מגולגלת","נתיבות","074-7493133");
        addRow("נתיבות","פיצריות","פיצה רמונטדה","נתיבות","08-9708888");
        addRow("נתיבות","פיצריות","פיצה עגול נתיבות","בעלי המלאכה 10","08-9337007");
        addRow("נתיבות","מסעדות","שווארמה כחלון","יוסף סמלו 3","050-5242110");
        addRow("נתיבות","מסעדות","לה פסטריה","יוסף סמלו 42","050-6761059");
        addRow("נתיבות","סופרמרקטים","יוחננוף נתיבות - גלובוס סנטר","בעלי המלאכה 2","053-7002121");
        addRow("נתיבות","סופרמרקטים","יוחננוף","בעלי המלאכה 2","076-8176320");
        addRow("נתיבות","סופרמרקטים","ויקטורי","בעלי המלאכה, צים אורבן נתיבות","");
        addRow("נתיבות","מוסכים","פוינט מערכות","בעלי המלאכה 8, צים סנטר","072-2646451");
        addRow("נתיבות","מוסכים","איתן מרגאני יבוא ושיווק חלקי חילוף לרכב","נתיבות","053-7233506");
        addRow("נתיבות","מוסכים","מוסך העיר","נתיבות","050-6884485");
        addRow("נתיבות","מוסכים","אוטו אאוטלט","האסיף 1","072-3105699");
        addRow("נתיבות","חלפים לרכב","מנור חלפים","הירדן 13","053-7286555");
        addRow("נתיבות","מוסכים","מוסך גרר שבע","האורגים 224","072-3294430");
        addRow("נתיבות","מוסכים","מוסך תהילה בעמ","מרכז תעשיה 602","08-9933616");
        addRow("נתיבות","מוסכים","מוסך לב הרכב","האומן 21","050-6884485");

        addRow("נתיבות","מסעדות","ג'ויה סושי בר","יוסף סמלו 78","08-6900982");
        addRow("נתיבות","מסעדות","מפגש הכיכר","שד' ירושלים 33, מרכז מסחרי","08-9932577");
        addRow("נתיבות","מסעדות","טורו TORO","שד' ירושלים 1","08-9944405");
        addRow("נתיבות","מסעדות","פלאפל כיד המלך","הרמבם 1, מרכז מסחרי","052-7433485");
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
        // מקורות ציבוריים ועסקים נוספים בנתיבות — מספרי קשר עסקיים/ציבוריים בלבד.
        addRow("נתיבות","בתי מרקחת","מכבי פארם","שד' ירושלים 18","08-9945477");
        addRow("נתיבות","בתי מרקחת","סופר-פארם","שד' ירושלים","077-8881330");
        addRow("נתיבות","בתי מרקחת","בית מרקחת-מאוחדת","שד' ירושלים 170","08-9939124");
        addRow("נתיבות","בתי מרקחת","סופר-פארם צים סנטר","בעלי המלאכה 5, צים סנטר","077-8881810");
        addRow("נתיבות","מוצרי חשמל","א.ל.מ - נתיבות","בעלי המלאכה 203","08-9933875");
        addRow("נתיבות","מוצרי חשמל","טרקלין חשמל - נתיבות","בעלי המלאכה 5","08-9932290");
        addRow("נתיבות","מוצרי חשמל","מחסני חשמל - נתיבות","בעלי המלאכה 5","073-2540133");
        addRow("נתיבות","מוצרי חשמל","חשמל נעמן","רבי עקיבא 1","08-9944010");
        addRow("נתיבות","מוצרי חשמל","חשמל נתיבות","יוסף סמלו 10","072-3131893");
        addRow("נתיבות","מזגנים ומיזוג","ענק המזגנים נתיבות","שבטי ישראל 38","054-8102427");
        addRow("נתיבות","מזגנים ומיזוג","קירור יקיר","בן גוריון 21","072-3149016");
        addRow("נתיבות","מזגנים ומיזוג","י.ש. מרכז המזגנים","נתיבות","072-3111440");
        addRow("נתיבות","מזגנים ומיזוג","BFresh","אריאל שרון 15","072-3227122");
        addRow("נתיבות","מזגנים ומיזוג","עידן האנרגיה בעמ","נתיבות","072-3142990");
        addRow("נתיבות","מזגנים ומיזוג","קירור משה","הרצוג 4","072-3107891");
        addRow("נתיבות","מזגנים ומיזוג","מרכז המיזוג","הירדן 7","072-3295425");
        addRow("נתיבות","מזגנים ומיזוג","פור-קור","נתיבות","054-3111971");
        addRow("נתיבות","מזגנים ומיזוג","פסגת המיזוג","אלפסי 3","072-3145374");
        addRow("נתיבות","מזגנים ומיזוג","א.א. רשת המזגן","בעלי המלאכה 10, צים סנטר","072-3133235");
        addRow("נתיבות","מזגנים ומיזוג","אקלים מערכות","שד' ירושלים 55","072-3122618");
        addRow("נתיבות","מזגנים ומיזוג","מיזוג אויר יהונתן שמואל","נתיבות","054-6181611");
        addRow("נתיבות","חשמל רכב","א.א בוחנים","נתיבות","072-3267511");
        addRow("נתיבות","חשמל רכב","פוינט מערכות","בעלי המלאכה 8","054-9509401");
        addRow("נתיבות","חנות לבעלי חיים","עידן התוכי והחי","יוסף סמלו 1","08-9934444");
        addRow("נתיבות","בנקאות","בנק לאומי - סניף נתיבות","בעלי המלאכה 5","03-9545522");
        addRow("נתיבות","בנקאות","בנק הפועלים - סניף נתיבות","בעלי מלאכה 5, צים סנטר","*2390");
        addRow("נתיבות","שירותים ציבוריים","תחנת משטרת נתיבות","אחד עשר הנקודות 3","08-9937444");
        addRow("נתיבות","שירותים ציבוריים","ביטוח לאומי - סניף נתיבות","הגפן 23","*6050");
        addRow("נתיבות","תחבורה ציבורית","שירותי רב-קו נתיבות","שדרות ירושלים 1","*5467");
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
        status.setText("מצב אופליין • נמצאו " + rows.size() + " תוצאות");
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
